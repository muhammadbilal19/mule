/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4;

import static org.mule.runtime.core.config.MuleManifest.getProductVersion;
import org.mule.runtime.core.DefaultMuleContext;
import org.mule.runtime.core.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.registry.MuleRegistry;
import org.mule.runtime.core.api.registry.ServiceRegistry;
import org.mule.runtime.core.config.MuleManifest;
import org.mule.runtime.core.registry.DefaultRegistryBroker;
import org.mule.runtime.core.registry.MuleRegistryHelper;
import org.mule.runtime.core.registry.SpiServiceRegistry;
import org.mule.runtime.core.util.ArrayUtils;
import org.mule.runtime.core.util.collection.ImmutableListCollector;
import org.mule.runtime.extension.api.ExtensionManager;
import org.mule.runtime.extension.api.introspection.ExtensionFactory;
import org.mule.runtime.extension.api.introspection.declaration.DescribingContext;
import org.mule.runtime.extension.api.introspection.declaration.spi.Describer;
import org.mule.runtime.extension.api.resources.GeneratedResource;
import org.mule.runtime.extension.api.resources.ResourcesGenerator;
import org.mule.runtime.extension.api.resources.spi.GeneratedResourceFactory;
import org.mule.runtime.module.extension.internal.DefaultDescribingContext;
import org.mule.runtime.module.extension.internal.introspection.DefaultExtensionFactory;
import org.mule.runtime.module.extension.internal.introspection.describer.AnnotationsBasedDescriber;
import org.mule.runtime.module.extension.internal.introspection.version.StaticVersionResolver;
import org.mule.runtime.module.extension.internal.manager.DefaultExtensionManager;
import org.mule.runtime.module.extension.internal.manager.ExtensionManagerAdapter;
import org.mule.runtime.module.extension.internal.resources.AbstractResourcesGenerator;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;

/**
 * It creates a {@link ExtensionManager} and automatically registers extensions.
 * <p/>
 * Once extensions are registered, a {@link ResourcesGenerator} is used to automatically
 * generate any backing resources needed (XSD schemas, spring bundles, etc).
 * <p/>
 * In this way, the user experience is greatly simplified when running the test
 * either through an IDE or build tool such as maven or gradle.
 * <p/>
 */
public class ExtensionsTestInfrastructureDiscoverer
{
    private final ServiceRegistry serviceRegistry = new SpiServiceRegistry();
    private final ExtensionFactory extensionFactory = new DefaultExtensionFactory(serviceRegistry, getClass().getClassLoader());
    private final ExtensionManagerAdapter extensionManager;
    private final File generatedResourcesDirectory;

    public ExtensionsTestInfrastructureDiscoverer(ExtensionManagerAdapter extensionManager, File generatedResourcesDirectory)
    {
        try
        {
            this.extensionManager = extensionManager;
            this.generatedResourcesDirectory = generatedResourcesDirectory;
            createManifestFileIfNecessary(generatedResourcesDirectory);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error creating discoverer", e);
        }
    }

    public ExtensionsTestInfrastructureDiscoverer(File generatedResourcesDirectory)
    {
        this(new DefaultExtensionManager(), generatedResourcesDirectory);
        ((DefaultExtensionManager) extensionManager).setMuleContext(new DefaultMuleContext()
        {
            @Override
            public MuleRegistry getRegistry()
            {
                return new MuleRegistryHelper(new DefaultRegistryBroker(this), this);
            }
        });
        try
        {
            ((DefaultExtensionManager) extensionManager).initialise();
        }
        catch (InitialisationException e)
        {
            throw new RuntimeException("Error while initialising the extension manager", e);
        }
    }

    /**
     * @param describers if empty it will use annotatedClasses param to build the describers
     * @param annotatedClasses used to build the describers
     * @return a {@link List} of the resources generated for the given describers or annotated classes
     */
    public List<GeneratedResource> discoverExtensions(Describer[] describers, Class<?>[] annotatedClasses)
    {
        if (ArrayUtils.isEmpty(describers))
        {
            if (!ArrayUtils.isEmpty(annotatedClasses))
            {
                describers = new Describer[annotatedClasses.length];
                int i = 0;
                for (Class<?> annotatedClass : annotatedClasses)
                {
                    describers[i++] = new AnnotationsBasedDescriber(annotatedClass, new StaticVersionResolver(getProductVersion()));
                }
            }
        }

        if (ArrayUtils.isEmpty(describers))
        {
            throw new IllegalStateException("No extension referenced from test");
        }
        else
        {
            loadExtensionsFromDescribers(extensionManager, describers);
        }

        //TODO: support for multiple extensions here, resources should be returned by extension
        ExtensionsTestInfrastructureResourcesGenerator generator = new ExtensionsTestInfrastructureResourcesGenerator(getResourceFactories(), generatedResourcesDirectory);
        extensionManager.getExtensions().forEach(generator::generateFor);
        return generator.dumpAll();
    }

    private List<GeneratedResourceFactory> getResourceFactories()
    {
        return ImmutableList.copyOf(serviceRegistry.lookupProviders(GeneratedResourceFactory.class));
    }

    private void loadExtensionsFromDescribers(ExtensionManagerAdapter extensionManager, Describer[] describers)
    {
        for (Describer describer : describers)
        {
            final DescribingContext context = new DefaultDescribingContext(getClass().getClassLoader());
            extensionManager.registerExtension(extensionFactory.createFrom(describer.describe(context), context));
        }
    }

    private class ExtensionsTestInfrastructureResourcesGenerator extends AbstractResourcesGenerator
    {

        private File targetDirectory;
        private Map<String, StringBuilder> contents = new HashMap<>();

        private ExtensionsTestInfrastructureResourcesGenerator(Collection<GeneratedResourceFactory> resourceFactories, File targetDirectory)
        {
            super(resourceFactories);
            this.targetDirectory = targetDirectory;
        }

        @Override
        protected void write(GeneratedResource resource)
        {
            StringBuilder builder = contents.get(resource.getPath());
            if (builder == null)
            {
                builder = new StringBuilder();
                contents.put(resource.getPath(), builder);
            }

            if (builder.length() > 0)
            {
                builder.append("\n");
            }

            builder.append(new String(resource.getContent()));
        }

        List<GeneratedResource> dumpAll()
        {
            List<GeneratedResource> allResources = contents.entrySet().stream()
                    .map(entry -> new GeneratedResource(entry.getKey(), entry.getValue().toString().getBytes()))
                    .collect(new ImmutableListCollector<>());

            allResources.forEach(resource -> {
                File targetFile = new File(targetDirectory, resource.getPath());
                try
                {
                    FileUtils.write(targetFile, new String(resource.getContent()));
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            });

            return allResources;
        }
    }

    private File createManifestFileIfNecessary(File targetDirectory) throws IOException
    {
        return createManifestFileIfNecessary(targetDirectory, MuleManifest.getManifest());
    }

    private File createManifestFileIfNecessary(File targetDirectory, Manifest sourceManifest) throws IOException
    {
        File manifestFile = new File(targetDirectory.getPath(), "MANIFEST.MF");
        if (!manifestFile.exists())
        {
            Manifest manifest = new Manifest(sourceManifest);
            try (FileOutputStream fileOutputStream = new FileOutputStream(manifestFile))
            {
                manifest.write(fileOutputStream);
            }
        }
        return manifestFile;
    }

}
