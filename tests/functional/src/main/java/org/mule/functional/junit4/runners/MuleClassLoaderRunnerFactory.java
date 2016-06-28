/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import static org.mule.functional.junit4.runners.AnnotationUtils.getAnnotationAttributeFrom;
import static org.mule.runtime.module.extension.internal.ExtensionProperties.EXTENSION_MANIFEST_FILE_NAME;
import org.mule.runtime.extension.api.manifest.ExtensionManifest;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoaderFilter;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoaderFilterFactory;
import org.mule.runtime.module.artifact.classloader.ClassLoaderLookupPolicyProvider;
import org.mule.runtime.module.artifact.classloader.CompositeClassLoader;
import org.mule.runtime.module.artifact.classloader.DefaultArtifactClassLoaderFilterFactory;
import org.mule.runtime.module.artifact.classloader.FilteringArtifactClassLoader;
import org.mule.runtime.module.artifact.classloader.MuleArtifactClassLoader;
import org.mule.runtime.module.extension.internal.manager.DefaultExtensionManager;

import com.google.common.collect.Sets;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default Mule implementation that creates almost the same classloader hierarchy that is used by Mule when running
 * applications.
 * The classloaders created have the following hierarchy:
 * <ul>
 *     <li>Container: all the provided scope dependencies plus their dependencies (if they are not test) and java</li>
 *     <li>Plugin (optional): all the compile scope dependencies and their dependencies (only the ones with scope compile)</li>
 *     <li>Application: all the test scope dependencies and their dependencies if they are not defined to be excluded, plus the test dependencies
 *     from the compile scope dependencies (again if they are not excluded).</li>
 * </ul>
 */
public class MuleClassLoaderRunnerFactory implements ClassLoaderRunnerFactory
{
    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());
    private ArtifactClassLoaderFilterFactory artifactClassLoaderFilterFactory = new DefaultArtifactClassLoaderFilterFactory();
    private DefaultExtensionManager extensionManager = new DefaultExtensionManager();

    @Override
    public ClassLoader createClassLoader(Class<?> klass, ArtifactUrlClassification artifactUrlClassification)
    {
        // Container classLoader
        logClassLoaderUrls("CONTAINER", artifactUrlClassification.getContainer());
        final TestContainerClassLoaderFactory testContainerClassLoaderFactory = new TestContainerClassLoaderFactory(getExtraBootPackages(klass));
        Set<String> containerExportedPackages = new HashSet<>();
        containerExportedPackages.addAll(testContainerClassLoaderFactory.getBootPackages());
        containerExportedPackages.addAll(testContainerClassLoaderFactory.getSystemPackages());
        ClassLoader classLoader = testContainerClassLoaderFactory.createContainerClassLoader(new URLClassLoader(artifactUrlClassification.getContainer().toArray(new URL[0]))).getClassLoader();

        // Plugin classloaders
        if (!artifactUrlClassification.getPlugins().isEmpty())
        {
            final List<ClassLoader> pluginClassLoaders = new ArrayList<>();
            //TODO(gfernandes) workaround until we define what to do with CompositeClassLoader if for resources it should also delegate to the parent classloader or not
            pluginClassLoaders.add(new MuleArtifactClassLoader("sharedLibs", new URL[0], classLoader, ((ClassLoaderLookupPolicyProvider) classLoader).getClassLoaderLookupPolicy()));

            for(Set<URL> pluginUrls : artifactUrlClassification.getPlugins())
            {
                // Plugin classLoader
                logClassLoaderUrls("PLUGIN", pluginUrls);
                MuleArtifactClassLoader pluginCL = new MuleArtifactClassLoader("plugin", pluginUrls.toArray(new URL[0]), classLoader, ((ClassLoaderLookupPolicyProvider) classLoader).getClassLoaderLookupPolicy());

                URL manifestUrl = pluginCL.findResource("META-INF/" + EXTENSION_MANIFEST_FILE_NAME);
                ExtensionManifest extensionManifest = extensionManager.parseExtensionManifestXml(manifestUrl);
                ArtifactClassLoaderFilter filter = artifactClassLoaderFilterFactory.create(extensionManifest.getExportedPackages().stream().collect(Collectors.joining(", ")), extensionManifest.getExportedResources().stream().collect(Collectors.joining(", ")));
                pluginClassLoaders.add(new FilteringArtifactClassLoader(pluginCL, filter));
            }
            classLoader = new CompositeClassLoader(classLoader, pluginClassLoaders, ((ClassLoaderLookupPolicyProvider) classLoader).getClassLoaderLookupPolicy());
        }

        // Application classLoader
        logClassLoaderUrls("APP", artifactUrlClassification.getApplication());
        classLoader = new MuleArtifactClassLoader("app", artifactUrlClassification.getApplication().toArray(new URL[0]), classLoader, ((ClassLoaderLookupPolicyProvider) classLoader).getClassLoaderLookupPolicy());

        return classLoader;
    }

    private void logClassLoaderUrls(final String classLoaderName, final Set<URL> urls)
    {
        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder(classLoaderName).append(" classloader urls: [");
            urls.stream().forEach(e -> builder.append("\n").append(" ").append(e.getFile()));
            builder.append("\n]");
            logger.debug(builder.toString());
        }
    }

    private Set<String> getExtraBootPackages(Class<?> klass)
    {
        String extraPackages = getAnnotationAttributeFrom(klass, ArtifactClassLoaderRunnerConfig.class, "extraBootPackages");

        return Sets.newHashSet(extraPackages.split(","));
    }

}
