/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4.runners;

import org.mule.runtime.module.artifact.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.classloader.MuleArtifactClassLoader;

import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.runners.model.InitializationError;

/**
 * Runner that creates a similar classloader isolation hierarchy as Mule uses on runtime.
 * The classloaders here created for running the test have the following hierarchy, from parent to child:
 * ContainerClassLoader (it also adds junit and org.hamcrest packages as PARENT_ONLY look up strategy)
 */
public class ArtifactClassloaderTestRunner extends AbstractClassLoaderIsolatedTestRunner
{

    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";
    private static final String DEPENDENCIES_GRAPH_FILE = TARGET_TEST_CLASSES + "dependency-graph.dot";

    /**
     * Creates a Runner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public ArtifactClassloaderTestRunner(final Class<?> klass) throws InitializationError
    {
        super(klass);
    }

    public String getDependenciesGraphFileName(final Class<?> testClass)
    {
        String dependenciesListFileName = DEPENDENCIES_GRAPH_FILE;
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            dependenciesListFileName = annotation.dependenciesGraphFile();
        }

        return dependenciesListFileName;
    }

    public Set<String> getExtraBootPackages(final Class<?> testClass)
    {
        String extraPackages = "org.junit,junit,org.hamcrest,org.mockito";
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            extraPackages = annotation.extraBootPackages();
        }

        return Sets.newHashSet(extraPackages.split(","));
    }

    public Set<String> getExclusionsGroupIds(final Class<?> testClass)
    {
        String exclusions = "org.mule,com.mulesoft";
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            exclusions = annotation.exclusions();
        }

        return Sets.newHashSet(exclusions.split(",")).stream().map(exclusion -> exclusion.split(MavenArtifact.MAVEN_DEPENDENCIES_DELIMITER)[0]).collect(Collectors.toSet());
    }

    public Set<String> getExclusionsArtifactIds(final Class<?> testClass)
    {
        String exclusions = "";
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            exclusions = annotation.exclusions();
        }

        return Sets.newHashSet(exclusions.split(",")).stream().filter(exclusion -> exclusion.contains(MavenArtifact.MAVEN_DEPENDENCIES_DELIMITER)).map(exclusion -> exclusion.split(MavenArtifact.MAVEN_DEPENDENCIES_DELIMITER)[1]).collect(Collectors.toSet());
    }

    public boolean isUsePluginClassSpace(final Class<?> testClass)
    {
        boolean usePluginClassSpace = false;
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            usePluginClassSpace = annotation.usePluginClassSpace();
        }

        return usePluginClassSpace;
    }

    @Override
    protected ClassLoader buildArtifactClassloader(final Class<?> klass) throws IOException, URISyntaxException
    {
        final String userDir = System.getProperty("user.dir");
        final File dependenciesGraphFile = new File(userDir, getDependenciesGraphFileName(klass));

        if (!dependenciesGraphFile.exists())
        {
            throw new RuntimeException(String.format("Unable to run test a '%s' was not found. Run 'mvn process-resources' to ensure this file is created before running the test", DEPENDENCIES_GRAPH_FILE));
        }

        final Set<URL> urls = getFullClassPathUrls();

        Path dependenciesPath = Paths.get(dependenciesGraphFile.toURI());
        BasicFileAttributes view = Files.getFileAttributeView(dependenciesPath, BasicFileAttributeView.class).readAttributes();
        logger.debug("Building classloader hierarchy using maven dependency graph file: '{}', created: {}, last modified: {}", dependenciesGraphFile, view.creationTime(), view.lastModifiedTime());
        Map<MavenArtifact, Set<MavenArtifact>> allDependencies = toMavenDependenciesMap(dependenciesGraphFile);

        Set<String> exclusionsGroupIds = getExclusionsGroupIds(klass);
        Set<String> exclusionsArtifactIds = getExclusionsArtifactIds(klass);

        Set<URL> pluginURLs = buildPluginClassLoaderURLs(urls, allDependencies, exclusionsGroupIds, exclusionsArtifactIds);
        Set<URL> appURLs = buildApplicationClassLoaderURLs(urls, allDependencies, exclusionsGroupIds, exclusionsArtifactIds);

        appURLs.addAll(buildArtifactTargetClassesURL(userDir, urls));

        // The container contains anything that is not application either extension classloader urls
        Set<URL> containerURLs = new HashSet<>();
        containerURLs.addAll(urls);
        containerURLs.removeAll(pluginURLs);
        containerURLs.removeAll(appURLs);

        // Container classLoader
        logClassLoaderUrls("CONTAINER", containerURLs);
        final TestContainerClassLoaderFactory testContainerClassLoaderFactory = new TestContainerClassLoaderFactory(getExtraBootPackages(klass));
        Set<String> containerExportedPackages = new HashSet<>();
        containerExportedPackages.addAll(testContainerClassLoaderFactory.getBootPackages());
        containerExportedPackages.addAll(testContainerClassLoaderFactory.getSystemPackages());
        ArtifactClassLoader classLoader = testContainerClassLoaderFactory.createContainerClassLoader(new SystemContainerClassLoader(containerURLs.toArray(new URL[containerURLs.size()]), containerExportedPackages));

        if (isUsePluginClassSpace(klass))
        {
            // Plugin classLoader
            logClassLoaderUrls("PLUGIN", pluginURLs);
            classLoader = new MuleArtifactClassLoader("plugin", pluginURLs.toArray(new URL[pluginURLs.size()]), classLoader.getClassLoader(), classLoader.getClassLoaderLookupPolicy());
        }
        else
        {
            appURLs.addAll(pluginURLs);
        }

        // Application classLoader
        logClassLoaderUrls("APP", appURLs);
        return new MuleArtifactClassLoader("app", appURLs.toArray(new URL[appURLs.size()]), classLoader.getClassLoader(), classLoader.getClassLoaderLookupPolicy()).getClassLoader();
    }

    private Set<URL> buildArtifactTargetClassesURL(String userDir, Set<URL> urls)
    {
        String currentArtifactFolderName = new File(userDir).getPath();
        return urls.stream().filter(url -> url.getFile().trim().equals(currentArtifactFolderName + TARGET_TEST_CLASSES)).collect(Collectors.toSet());
    }

    private Set<URL> buildApplicationClassLoaderURLs(Set<URL> urls, Map<MavenArtifact, Set<MavenArtifact>> allDependencies, Set<String> exclusionsGroupIds, Set<String> exclusionsArtifactIds)
    {
        Set<URL> testURLs = new HashSet<>();
        Set<MavenArtifact> testDeps = allDependencies.entrySet().stream().filter(e -> e.getKey().isTestScope()).map(e -> e.getKey()).collect(Collectors.toSet());
        testDeps.addAll(allDependencies.entrySet()
                .stream()
                .filter(e -> testDeps.contains(e.getKey()))
                .flatMap(p -> p.getValue().stream())
                .filter(dep -> !exclusionsGroupIds.contains(dep.getGroupId()) && !exclusionsArtifactIds.contains(dep.getArtifactId()))
                .collect(Collectors.toSet())
        );
        // just the case for current artifact that is compile scope and has test dependencies that should be added too
        Set<MavenArtifact> compileDeps = allDependencies.entrySet().stream().filter(e -> e.getKey().isCompileScope()).map(e -> e.getKey()).collect(Collectors.toSet());
        testDeps.addAll(allDependencies.entrySet()
                                .stream()
                                .filter(e -> compileDeps.contains(e.getKey()))
                                .flatMap(p -> p.getValue().stream())
                                .filter(dep -> dep.isTestScope())
                                .collect(Collectors.toSet())
        );
        testDeps.forEach(artifact -> addURL(testURLs, artifact, urls));
        return testURLs;
    }

    private Set<URL> buildPluginClassLoaderURLs(Set<URL> urls, Map<MavenArtifact, Set<MavenArtifact>> allDependencies, Set<String> exclusionsGroupIds, Set<String> exclusionsArtifactIds)
    {
        // plugin libraries should be all the dependencies with scope 'compile'
        Set<URL> appURLs = new HashSet<>();
        Set<MavenArtifact> appDeps = allDependencies.entrySet().stream().filter(e -> e.getKey().isCompileScope()).map(e -> e.getKey()).collect(Collectors.toSet());
        appDeps.addAll(allDependencies.entrySet()
                               .stream()
                               .filter(e -> appDeps.contains(e.getKey()))
                               .flatMap(p -> p.getValue().stream())
                               .filter(dep -> !exclusionsGroupIds.contains(dep.getGroupId()) && !exclusionsArtifactIds.contains(dep.getArtifactId()) && !dep.isTestScope()).collect(Collectors.toSet()));
        appDeps.forEach(artifact -> addURL(appURLs, artifact, urls));
        return appURLs;
    }

    private void logClassLoaderUrls(final String classLoaderName, final Collection<URL> urls)
    {
        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder(classLoaderName).append(" classloader urls: [");
            urls.forEach(e -> builder.append("\n").append(" ").append(e.getFile()));
            builder.append("\n]");
            logger.debug(builder.toString());
        }
    }

    private Map<MavenArtifact, Set<MavenArtifact>> toMavenDependenciesMap(final File dependenciesGraphFile) throws IOException
    {
        Map<MavenArtifact, Set<MavenArtifact>> mavenArtifactsDependencies = new HashMap<>();

        Files.readAllLines(dependenciesGraphFile.toPath(),
                           Charset.defaultCharset()).stream()
                .filter(line -> line.contains("->")).forEach(line -> {
                MavenArtifact from = parseDotDependencyArtifactFrom(line);
                MavenArtifact to = parseDotDependencyArtifactTo(line);
                if (!mavenArtifactsDependencies.containsKey(from)) {
                    mavenArtifactsDependencies.put(from, new HashSet<>());
                }
                mavenArtifactsDependencies.get(from).add(to);
            }
        );

        return mavenArtifactsDependencies;
    }

    private void addURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls)
    {
        if(artifact.getType().equals("pom")) {
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
            addModuleURL(collection, artifact, urls);
        }
    }

    private void addModuleURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls)
    {
        final StringBuilder moduleFolder = new StringBuilder(MavenModuleMapping.moduleMapping.get(artifact.getArtifactId())).append("target/");
        if (moduleFolder == null)
        {
            throw new IllegalArgumentException("Cannot locate artifact as multi-module dependency: '" + artifact + "', mapping used is: " + MavenModuleMapping.moduleMapping);
        }

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
            packagedUrlSuffix.append("^(?!.*?(?:-test.jar)).*$");
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

    private MavenArtifact parseMavenArtifact(final String mavenDependencyString)
    {
        String[] tokens = mavenDependencyString.split(MavenArtifact.MAVEN_DEPENDENCIES_DELIMITER);
        String groupId = tokens[0];
        String artifactId = tokens[1];
        String type = tokens[2];
        String version = tokens[3];
        String scope = tokens[4];
        return new MavenArtifact(groupId, artifactId, type, version, scope);
    }

    private MavenArtifact parseDotDependencyArtifactTo(final String line)
    {
        String artifactLine = line.split("->")[1];
        if (artifactLine.contains("["))
        {
            artifactLine = artifactLine.substring(0, artifactLine.indexOf("["));
        }
        if (artifactLine.contains("\""))
        {
            artifactLine = artifactLine.substring(artifactLine.indexOf("\"") + 1, artifactLine.lastIndexOf("\""));
        }
        return parseMavenArtifact(artifactLine.trim());
    }

    private MavenArtifact parseDotDependencyArtifactFrom(final String line)
    {
        String artifactLine = line.split("->")[0];
        if (artifactLine.contains("\""))
        {
            artifactLine = artifactLine.substring(artifactLine.indexOf("\"") + 1, artifactLine.lastIndexOf("\""));
        }
        return parseMavenArtifact(artifactLine.trim());
    }
}
