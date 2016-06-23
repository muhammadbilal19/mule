/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for {@link ClassPathClassifier} that builds a {@link ClassSpace} similar to what Mule
 * Runtime does by taking into account the Maven dependencies of the given tested artifact.
 * Basically it creates a {@link ClassSpace} hierarchy with:
 * Provided Scope (plus JDK stuff)->Compile Scope (plus target/classes)->Test Scope (plus target/test-classes)
 * In all the cases it also includes its dependencies.
 */
public class MuleClassPathClassifier implements ClassPathClassifier
{
    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";

    @Override
    public ClassSpace classify(Class<?> klass, Set<URL> classPathURLs, Map<MavenArtifact, Set<MavenArtifact>> allDependencies, MavenMultiModuleArtifactMapping mavenMultiModuleMapping)
    {
        final String userDir = System.getProperty("user.dir");

        boolean isUsingPluginClassSpace = isUsePluginClassSpace(klass);

        ClassSpaceBuilder classSpaceBuilder = new ClassSpaceBuilder();

        Predicate<MavenArtifact> appExclusion = getAppExclusionPredicate(klass);
        Set<URL> appURLs = buildClassLoaderURLs(mavenMultiModuleMapping, classPathURLs, allDependencies, false, artifact -> artifact.isTestScope(), dependency -> !appExclusion.test(dependency));
        appURLs.addAll(buildClassLoaderURLs(mavenMultiModuleMapping, classPathURLs, allDependencies, true, artifact -> artifact.isCompileScope() && !appExclusion.test(artifact), dependency -> dependency.isTestScope() && !appExclusion.test(dependency)));
        appURLs.addAll(buildArtifactTargetClassesURL(userDir, classPathURLs));

        classSpaceBuilder.withSpace(appURLs.toArray(new URL[appURLs.size()]), new URL[0]);

        // The container contains anything that is not application either extension classloader urls
        Set<URL> containerURLs = new HashSet<>();
        containerURLs.addAll(classPathURLs);
        containerURLs.removeAll(appURLs);

        if (isUsingPluginClassSpace)
        {
            Set<URL> pluginURLs = buildClassLoaderURLs(mavenMultiModuleMapping, classPathURLs, allDependencies, false, artifact -> artifact.isCompileScope(), dependency -> dependency.isCompileScope());
            containerURLs.removeAll(pluginURLs);

            classSpaceBuilder.withSpace(pluginURLs.toArray(new URL[pluginURLs.size()]), new URL[0]);
        }

        // After removing all the plugin and application urls we add provided dependencies urls (supports for having same dependencies as provided transitive and compile either test)
        Set<URL> containerProvidedDependenciesURLs = buildClassLoaderURLs(mavenMultiModuleMapping, classPathURLs, allDependencies, false, artifact -> artifact.isProvidedScope(), dependency -> !dependency.isTestScope());
        containerURLs.addAll(containerProvidedDependenciesURLs);

        classSpaceBuilder.withSpace(containerURLs.toArray(new URL[containerURLs.size()]), new URL[0]);

        return classSpaceBuilder.build();
    }

    private Set<URL> buildClassLoaderURLs(final MavenMultiModuleArtifactMapping mavenMultiModuleMapping, Set<URL> urls, Map<MavenArtifact, Set<MavenArtifact>> allDependencies, boolean shouldAddOnlyDependencies, Predicate<MavenArtifact> predicateArtifact, Predicate<MavenArtifact> predicateDependency)
    {
        Set<MavenArtifact> collectedDependencies = new HashSet<>();
        allDependencies.entrySet().stream().filter(e -> predicateArtifact.test(e.getKey())).map(e -> e.getKey()).collect(Collectors.toSet()).forEach(artifact -> {
            if (!shouldAddOnlyDependencies)
            {
                collectedDependencies.add(artifact);
            }
            collectedDependencies.addAll(getDependencies(artifact, allDependencies, predicateDependency));
        });
        Set<URL> fetchedURLs = new HashSet<>();
        collectedDependencies.forEach(artifact -> addURL(fetchedURLs, artifact, urls, mavenMultiModuleMapping));
        return fetchedURLs;
    }

    /**
     * @param artifact
     * @param allDependencies
     * @return recursively gets the dependencies for the given artifact
     */
    private Set<MavenArtifact> getDependencies(MavenArtifact artifact, Map<MavenArtifact, Set<MavenArtifact>> allDependencies, Predicate<MavenArtifact> predicate)
    {
        Set<MavenArtifact> dependencies = new HashSet<>();
        if (allDependencies.containsKey(artifact))
        {
            allDependencies.get(artifact).stream().forEach(dependency -> {
                if (predicate.test(dependency))
                {
                    dependencies.add(dependency);
                }
                dependencies.addAll(getDependencies(dependency, allDependencies, predicate));
            });
        }
        return dependencies;
    }

    private Set<URL> buildArtifactTargetClassesURL(String userDir, Set<URL> urls)
    {
        String currentArtifactFolderName = new File(userDir).getPath();
        return urls.stream().filter(url -> url.getFile().trim().equals(currentArtifactFolderName + TARGET_TEST_CLASSES)).collect(Collectors.toSet());
    }

    private void addURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls, final MavenMultiModuleArtifactMapping mavenMultiModuleMapping)
    {
        if (artifact.getType().equals("pom"))
        {
            logger.debug("Artifact ignored and not added to classloader: " + artifact);
            return;
        }

        Optional<URL> artifactURL = urls.stream().filter(filePath -> filePath.getFile().contains(artifact.getGroupIdAsPath() + File.separator + artifact.getArtifactId() + File.separator)).findFirst();
        if (artifactURL.isPresent())
        {
            collection.add(artifactURL.get());
        }
        else
        {
            addModuleURL(collection, artifact, urls, mavenMultiModuleMapping);
        }
    }

    private void addModuleURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls, final MavenMultiModuleArtifactMapping mavenMultiModuleMapping)
    {
        final StringBuilder moduleFolder = new StringBuilder(mavenMultiModuleMapping.mapModuleFolderNameFor(artifact.getArtifactId())).append("target/");

        // Fix to handle when running test during an intall phase due to maven builds the classpath pointing out to packaged files instead of classes folders.
        final StringBuilder explodedUrlSuffix = new StringBuilder();
        final StringBuilder packagedUrlSuffix = new StringBuilder();
        if (artifact.isTestScope() && artifact.getType().equals("test-jar"))
        {
            explodedUrlSuffix.append("test-classes/");
            packagedUrlSuffix.append(".*-tests.jar");
        }
        else
        {
            explodedUrlSuffix.append("classes/");
            packagedUrlSuffix.append("^(?!.*?(?:-tests.jar)).*.jar");
        }
        final Optional<URL> localFile = urls.stream().filter(url -> {
            String path = url.toString();
            if (path.contains(moduleFolder))
            {
                String pathSuffix = path.substring(path.lastIndexOf(moduleFolder.toString()) + moduleFolder.length(), path.length());
                return pathSuffix.matches(explodedUrlSuffix.toString()) || pathSuffix.matches(packagedUrlSuffix.toString());
            }
            return false;
        }).findFirst();
        if (localFile.isPresent())
        {
            collection.add(localFile.get());
        }
        else
        {
            throw new IllegalArgumentException("Cannot locate artifact as multi-module dependency: '" + artifact + "', on module folder: " + moduleFolder + " using exploded url suffix regex: " + explodedUrlSuffix + " or " + packagedUrlSuffix);
        }
    }


    private boolean isUsePluginClassSpace(Class<?> klass)
    {
        boolean usePluginClassSpace = false;
        MuleClassPathClassifierConfig annotation = klass.getAnnotation(MuleClassPathClassifierConfig.class);
        if (annotation != null)
        {
            usePluginClassSpace = annotation.usePluginClassSpace();
        }
        return usePluginClassSpace;
    }

    private Predicate<MavenArtifact> getAppExclusionPredicate(Class<?> klass)
    {
        String exclusions = "org.mule*:*:*";
        MuleClassPathClassifierConfig annotation = klass.getAnnotation(MuleClassPathClassifierConfig.class);
        if (annotation != null)
        {
            exclusions = annotation.appExclusions();
        }

        Predicate<MavenArtifact> exclusionPredicate = null;
        for (String exclusion : exclusions.split(","))
        {
            String[] exclusionSplit = exclusion.split(":");
            if (exclusionSplit.length != 3)
            {
                throw new IllegalArgumentException("Exclusion pattern should be a GAT format, groupId:artifactId:type");
            }
            Predicate<MavenArtifact> artifactExclusion = new MavenArtifactExclusionPredicate(exclusionSplit[0], exclusionSplit[1], exclusionSplit[2]);
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
