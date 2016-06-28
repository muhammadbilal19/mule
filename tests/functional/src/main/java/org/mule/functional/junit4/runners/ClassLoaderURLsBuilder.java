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
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassLoaderURLsBuilder {
    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<URL> urls;
    private final MavenMultiModuleArtifactMapping mavenMultiModuleMapping;
    private final LinkedHashMap<MavenArtifact, Set<MavenArtifact>> allDependencies;

    public ClassLoaderURLsBuilder(Set<URL> urls, MavenMultiModuleArtifactMapping mavenMultiModuleMapping, LinkedHashMap<MavenArtifact, Set<MavenArtifact>> allDependencies) {
        this.urls = urls;
        this.mavenMultiModuleMapping = mavenMultiModuleMapping;
        this.allDependencies = allDependencies;
    }

    public Set<URL> buildClassLoaderURLs(final boolean shouldAddOnlyDependencies,
                                         final boolean shouldAddTransitiveDepFromExcluded,
                                         final Predicate<MavenArtifact> predicateArtifact,
                                         final Predicate<MavenArtifact> predicateDependency
    ) {
        Set<MavenArtifact> collectedDependencies = new HashSet<>();

        allDependencies.entrySet().stream()
                .filter(e -> predicateArtifact.test(e.getKey()))
                .map(e -> e.getKey())
                .collect(Collectors.toSet())
                .forEach(artifact -> {

                    if (!shouldAddOnlyDependencies) {
                        collectedDependencies.add(artifact);
                    }
                    collectedDependencies.addAll(getDependencies(artifact, predicateDependency, shouldAddTransitiveDepFromExcluded));
                });

        Set<URL> fetchedURLs = new HashSet<>();
        collectedDependencies.forEach(artifact -> addURL(fetchedURLs, artifact, urls, mavenMultiModuleMapping));
        return fetchedURLs;
    }

    /**
     * It builds a list of MavenArtifact representing the dependencies of the provided artifact.
     *
     * @param artifact a MavenArtifact for which we want to know its dependencies
     * @return recursively gets the dependencies for the given artifact
     */
    private Set<MavenArtifact> getDependencies(final MavenArtifact artifact, final Predicate<MavenArtifact> predicate, final boolean shouldAddTransitiveDepFromExcluded) {
        Set<MavenArtifact> dependencies = new HashSet<>();
        if (allDependencies.containsKey(artifact)) {
            allDependencies.get(artifact).stream().forEach(dependency -> {
                if (predicate.test(dependency)) {
                    dependencies.add(dependency);
                    dependencies.addAll(getDependencies(dependency, predicate, shouldAddTransitiveDepFromExcluded));
                } else {
                    // Just the case for getting all their dependencies from an excluded dependencies (case of org.mule:core for instance, we also need their transitive dependencies)
                    if (shouldAddTransitiveDepFromExcluded) {
                        dependencies.addAll(getDependencies(dependency, predicate, shouldAddTransitiveDepFromExcluded));
                    }
                }
            });
        }
        return dependencies;
    }

    private void addURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls, final MavenMultiModuleArtifactMapping mavenMultiModuleMapping) {
        if (artifact.getType().equals("pom")) {
            logger.debug("Artifact ignored and not added to classloader: " + artifact);
            return;
        }

        Optional<URL> artifactURL = urls.stream().filter(filePath -> filePath.getFile().contains(artifact.getGroupIdAsPath() + File.separator + artifact.getArtifactId() + File.separator)).findFirst();
        if (artifactURL.isPresent()) {
            collection.add(artifactURL.get());
        } else {
            addModuleURL(collection, artifact, urls, mavenMultiModuleMapping);
        }
    }

    private void addModuleURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls, final MavenMultiModuleArtifactMapping mavenMultiModuleMapping) {
        final StringBuilder moduleFolder = new StringBuilder(mavenMultiModuleMapping.mapModuleFolderNameFor(artifact.getArtifactId())).append("target/");

        // Fix to handle when running test during an intall phase due to maven builds the classpath pointing out to packaged files instead of classes folders.
        final StringBuilder explodedUrlSuffix = new StringBuilder();
        final StringBuilder packagedUrlSuffix = new StringBuilder();
        if (artifact.isTestScope() && artifact.getType().equals("test-jar")) {
            explodedUrlSuffix.append("test-classes/");
            packagedUrlSuffix.append(".*-tests.jar");
        } else {
            explodedUrlSuffix.append("classes/");
            packagedUrlSuffix.append("^(?!.*?(?:-tests.jar)).*.jar");
        }
        final Optional<URL> localFile = urls.stream().filter(url -> {
            String path = url.toString();
            if (path.contains(moduleFolder)) {
                String pathSuffix = path.substring(path.lastIndexOf(moduleFolder.toString()) + moduleFolder.length(), path.length());
                return pathSuffix.matches(explodedUrlSuffix.toString()) || pathSuffix.matches(packagedUrlSuffix.toString());
            }
            return false;
        }).findFirst();
        if (localFile.isPresent()) {
            collection.add(localFile.get());
        } else {
            throw new IllegalArgumentException("Cannot locate artifact as multi-module dependency: '" + artifact + "', on module folder: " + moduleFolder + " using exploded url suffix regex: " + explodedUrlSuffix + " or " + packagedUrlSuffix);
        }
    }
} 