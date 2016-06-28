/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import static java.util.Arrays.stream;
import static org.mule.functional.junit4.runners.AnnotationUtils.getAnnotationAttributeFrom;
import org.mule.functional.junit4.ExtensionsTestInfrastructureDiscoverer;
import org.mule.runtime.extension.api.introspection.declaration.spi.Describer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for {@link ClassPathClassifier} that builds a {@link ArtifactUrlClassification} similar to what Mule
 * Runtime does by taking into account the Maven dependencies of the given tested artifact.
 * Basically it creates a {@link ArtifactUrlClassification} hierarchy with:
 * Provided Scope (plus JDK stuff)->Compile Scope (plus target/classes)->Test Scope (plus target/test-classes)
 * In all the cases it also includes its dependencies.
 */
public class MuleClassPathClassifier implements ClassPathClassifier
{

    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";
    public static final String GENERATED_TEST_SOURCES = "generated-test-sources";
    private static final String TARGET_CLASSES = "/target/";

    @Override
    public ArtifactUrlClassification classify(Class<?> klass, Set<URL> classPathURLs, LinkedHashMap<MavenArtifact, Set<MavenArtifact>> allDependencies, MavenMultiModuleArtifactMapping mavenMultiModuleMapping)
    {
        final String currentArtifactFolder = new File(System.getProperty("user.dir")).getPath();

        final Class[] extensions = getExtensions(klass);

        Predicate<MavenArtifact> appExclusion = getAppExclusionPredicate(klass);

        // First we find the compile artifact that should be the one being tested here!
        MavenArtifact compileArtifact = getCompileArtifact(allDependencies);
        logger.debug("Classification based on: " + compileArtifact);

        ClassLoaderURLsBuilder classLoaderURLsBuilder = new ClassLoaderURLsBuilder(classPathURLs, mavenMultiModuleMapping, allDependencies);

        // Application URLs are obtained by getting the dependencies of the compile artifact but only those that are not excluded (due to they are provided)
        Set<URL> appURLs = classLoaderURLsBuilder.buildClassLoaderURLs(true, true, artifact -> artifact.equals(compileArtifact), dependency -> dependency.isTestScope() && !appExclusion.test(dependency));

        // Plus the target/test-classes of the current compiled artifact
        appURLs.addAll(buildArtifactTargetClassesURL(currentArtifactFolder, classPathURLs));

        // The container contains anything that is not application either extension classloader urls
        Set<URL> containerURLs = new HashSet<>();
        containerURLs.addAll(classPathURLs);
        containerURLs.removeAll(appURLs);

        List<Set<URL>> extensionsURLs = new ArrayList<>(extensions.length);
        if (extensions.length > 0)
        {
            stream(extensions).forEach(extension ->
                                       {
                                           Set<URL> extensionURLs = extensionClassPathClassification(extension, mavenMultiModuleMapping, classLoaderURLsBuilder, compileArtifact, currentArtifactFolder);
                                           containerURLs.removeAll(extensionURLs);
                                           extensionsURLs.add(extensionURLs);
                                       });
        }

        // After removing all the plugin and application urls we add provided dependencies urls (supports for having same dependencies as provided transitive and compile either test)
        Set<URL> containerProvidedDependenciesURLs = classLoaderURLsBuilder.buildClassLoaderURLs(true,false,artifact -> artifact.equals(compileArtifact), dependency -> dependency.isProvidedScope());
        containerURLs.addAll(containerProvidedDependenciesURLs);

        return new ArtifactUrlClassification(containerURLs, extensionsURLs, appURLs);
    }

    private Set<URL> extensionClassPathClassification(Class<?> extension, MavenMultiModuleArtifactMapping mavenMultiModuleMapping, ClassLoaderURLsBuilder classLoaderURLsBuilder, MavenArtifact compileArtifact, String currentArtifactFolder)
    {
        Set<URL> extensionURLs = new LinkedHashSet<>();
        String extensionMavenArtifactId = mavenMultiModuleMapping.getMavenArtifactIdFor(extension);

        // First we need to add META-INF folder for generated resources due to they may be already created by another mvn install goal by the extension maven plugin
        File generatedResourcesDirectory = new File(currentArtifactFolder + TARGET_CLASSES + GENERATED_TEST_SOURCES + File.separator + extensionMavenArtifactId + File.separator + "META-INF");
        generatedResourcesDirectory.mkdirs();
        ExtensionsTestInfrastructureDiscoverer extensionDiscoverer = new ExtensionsTestInfrastructureDiscoverer(generatedResourcesDirectory);
        extensionDiscoverer.discoverExtensions(new Describer[0], new Class[] {extension});
        try
        {
            // Registering parent file as resource to be used from the configuration builder
            extensionURLs.add(generatedResourcesDirectory.getParentFile().toURI().toURL());
        }
        catch (MalformedURLException e)
        {
            throw new IllegalArgumentException("Error while building resource URL for directory: " + generatedResourcesDirectory.getPath(), e);
        }

        // Just get the extension maven artifact without its dependencies (case if the extension maven artifact doesn't have dependencies)
        extensionURLs.addAll(classLoaderURLsBuilder.buildClassLoaderURLs(!compileArtifact.getArtifactId().equals(extensionMavenArtifactId), false, artifact -> artifact.equals(compileArtifact), dependency -> dependency.getArtifactId().equals(extensionMavenArtifactId)));

        // Get dependencies from the extension maven artifact
        extensionURLs.addAll(classLoaderURLsBuilder.buildClassLoaderURLs(true, false, artifact -> artifact.getArtifactId().equals(extensionMavenArtifactId), dep -> dep.isCompileScope()));

        return extensionURLs;
    }

    private MavenArtifact getCompileArtifact(final LinkedHashMap<MavenArtifact, Set<MavenArtifact>> allDependencies)
    {
        Optional<MavenArtifact> compileArtifact = allDependencies.keySet().stream().filter(artifact -> artifact.isCompileScope()).findFirst();
        if (!compileArtifact.isPresent())
        {
            throw new IllegalArgumentException("Couldn't get current artifactId mapped as compile in dependency graph, it should be the first compile dependency");
        }
        return compileArtifact.get();
    }

    private Set<URL> buildArtifactTargetClassesURL(String currentArtifactFolderName, Set<URL> urls)
    {
        return urls.stream().filter(url -> url.getFile().trim().equals(currentArtifactFolderName + TARGET_TEST_CLASSES)).collect(Collectors.toSet());
    }


    private Class[] getExtensions(Class<?> klass)
    {
        return getAnnotationAttributeFrom(klass, ArtifactClassLoaderRunnerConfig.class, "extensions");
    }

    private Predicate<MavenArtifact> getAppExclusionPredicate(Class<?> klass)
    {
        String exclusions = getAnnotationAttributeFrom(klass, ArtifactClassLoaderRunnerConfig.class, "appPackageExclusions");

        Predicate<MavenArtifact> exclusionPredicate = null;
        for (String exclusion : exclusions.split(","))
        {
            String[] exclusionSplit = exclusion.split(":");
            if (exclusionSplit.length != 3)
            {
                throw new IllegalArgumentException("Exclusion pattern should be a GAT format, groupId:artifactId:type");
            }
            Predicate<MavenArtifact> artifactExclusion = new MavenArtifactMatcherPredicate(exclusionSplit[0], exclusionSplit[1], exclusionSplit[2]);
            if (exclusionPredicate == null)
            {
                exclusionPredicate = artifactExclusion;
            }
            else
            {
                exclusionPredicate = exclusionPredicate.or(artifactExclusion);
            }
        }

        return exclusionPredicate;
    }
}
