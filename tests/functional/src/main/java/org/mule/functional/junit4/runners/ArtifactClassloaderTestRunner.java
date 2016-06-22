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
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;

/**
 * Runner that creates a similar classloader isolation hierarchy as Mule uses on runtime.
 * The classloaders created here have the following hierarchy:
 * <ul>
 *     <li>Container: all the provided scope dependencies plus their dependencies (if they are not test) and java</li>
 *     <li>Plugin (optional): all the compile scope dependencies and their dependencies (only the ones with scope compile)</li>
 *     <li>Application: all the test scope dependencies and their dependencies if they are not defined to be excluded, plus the test dependencies
 *     from the compile scope dependencies (again if they are not excluded).</li>
 * </ul>
 */
public class ArtifactClassloaderTestRunner extends AbstractRunnerDelegate
{

    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";

    private final Runner delegate;
    private final ArtifactClassLoaderRunnerConfig annotation;
    private ClassPathURLsProvider classPathURLsProvider;
    private MavenDependenciesResolver mavenDependenciesResolver;
    private MavenMultiModuleAritfactMapping mavenMultiModuleAritfactMapping;

    /**
     * Creates a Runner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public ArtifactClassloaderTestRunner(final Class<?> klass) throws Exception
    {
        annotation = klass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        classPathURLsProvider = getClassPathURLsProvider();
        mavenDependenciesResolver = getMavenDependenciesResolver();
        mavenMultiModuleAritfactMapping = getMavenMultiModuleAritfactMapping();

        ClassLoader classLoader = buildArtifactClassloader(klass);
        delegate = new ClassLoaderIsolatedTestRunner(classLoader, klass);
    }

    @Override
    protected Runner getDelegateRunner()
    {
        return delegate;
    }

    private ClassLoader buildArtifactClassloader(final Class<?> klass) throws IOException, URISyntaxException
    {
        final String userDir = System.getProperty("user.dir");
        final Set<URL> urls = this.classPathURLsProvider.getURLs();
        boolean isUsingPluginClassSpace = isUsePluginClassSpace();

        Map<MavenArtifact, Set<MavenArtifact>> allDependencies = mavenDependenciesResolver.buildDependencies(klass);

        Set<URL> containerProvidedDependenciesURLs = buildClassLoaderURLs(urls, allDependencies, false, artifact -> artifact.isProvidedScope(), dependency -> !dependency.isTestScope());

        Predicate<MavenArtifact> appExclusion = getAppExclusionPredicate();
        Set<URL> appURLs = buildClassLoaderURLs(urls, allDependencies, false, artifact -> artifact.isTestScope(), dependency -> !appExclusion.test(dependency));
        appURLs.addAll(buildClassLoaderURLs(urls, allDependencies, true, artifact -> artifact.isCompileScope() && !appExclusion.test(artifact), dependency -> dependency.isTestScope() && !appExclusion.test(dependency)));

        appURLs.addAll(buildArtifactTargetClassesURL(userDir, urls));

        // The container contains anything that is not application either extension classloader urls
        Set<URL> containerURLs = new HashSet<>();
        containerURLs.addAll(urls);
        containerURLs.removeAll(appURLs);

        Set<URL> pluginURLs = Collections.emptySet();
        if (isUsingPluginClassSpace)
        {
            pluginURLs = buildClassLoaderURLs(urls, allDependencies, false, artifact -> artifact.isCompileScope(), dependency -> dependency.isCompileScope());
            containerURLs.removeAll(pluginURLs);
        }

        // After removing all the plugin and application urls we add provided dependencies urls (supports for having same dependencies as provided transitive and compile either test)
        containerURLs.addAll(containerProvidedDependenciesURLs);

        // Container classLoader
        logClassLoaderUrls("CONTAINER", containerURLs);
        final TestContainerClassLoaderFactory testContainerClassLoaderFactory = new TestContainerClassLoaderFactory(getExtraBootPackages());
        Set<String> containerExportedPackages = new HashSet<>();
        containerExportedPackages.addAll(testContainerClassLoaderFactory.getBootPackages());
        containerExportedPackages.addAll(testContainerClassLoaderFactory.getSystemPackages());
        ArtifactClassLoader classLoader = testContainerClassLoaderFactory.createContainerClassLoader(new URLClassLoader(containerURLs.toArray(new URL[containerURLs.size()])));

        if (isUsingPluginClassSpace)
        {
            // Plugin classLoader
            logClassLoaderUrls("PLUGIN", pluginURLs);
            classLoader = new MuleArtifactClassLoader("plugin", pluginURLs.toArray(new URL[pluginURLs.size()]), classLoader.getClassLoader(), classLoader.getClassLoaderLookupPolicy());
        }

        // Application classLoader
        logClassLoaderUrls("APP", appURLs);
        return new MuleArtifactClassLoader("app", appURLs.toArray(new URL[appURLs.size()]), classLoader.getClassLoader(), classLoader.getClassLoaderLookupPolicy()).getClassLoader();
    }

    private Set<URL> buildClassLoaderURLs(Set<URL> urls, Map<MavenArtifact, Set<MavenArtifact>> allDependencies, boolean shouldAddOnlyDependencies, Predicate<MavenArtifact> predicateArtifact, Predicate<MavenArtifact> predicateDependency)
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
        collectedDependencies.forEach(artifact -> addURL(fetchedURLs, artifact, urls));
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

    private void addURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls)
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
            addModuleURL(collection, artifact, urls);
        }
    }

    private void addModuleURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls)
    {
        final StringBuilder moduleFolder = new StringBuilder(mavenMultiModuleAritfactMapping.mapModuleFolderNameFor(artifact.getArtifactId())).append("target/");

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

    private Set<String> getExtraBootPackages()
    {
        String extraPackages = "org.junit,junit,org.hamcrest,org.mockito";
        if (annotation != null)
        {
            extraPackages = annotation.extraBootPackages();
        }
        return Sets.newHashSet(extraPackages.split(","));
    }

    private Predicate<MavenArtifact> getAppExclusionPredicate()
    {
        String exclusions = "org.mule*:*:*";
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

    private boolean isUsePluginClassSpace()
    {
        boolean usePluginClassSpace = false;
        if (annotation != null)
        {
            usePluginClassSpace = annotation.usePluginClassSpace();
        }
        return usePluginClassSpace;
    }

    public ClassPathURLsProvider getClassPathURLsProvider() throws IllegalAccessException, InstantiationException
    {
        ClassPathURLsProvider classPathURLsProvider = new DefaultClassPathURLsProvider();
        if (annotation != null)
        {
            classPathURLsProvider = annotation.classPathURLsProvider().newInstance();
        }
        return classPathURLsProvider;
    }

    public void setClassPathURLsProvider(ClassPathURLsProvider classPathURLsProvider)
    {
        this.classPathURLsProvider = classPathURLsProvider;
    }

    private MavenDependenciesResolver getMavenDependenciesResolver() throws IllegalAccessException, InstantiationException
    {
        MavenDependenciesResolver mavenDependenciesResolver = new DependencyGraphMavenDependenciesResolver();
        if (annotation != null)
        {
            mavenDependenciesResolver = annotation.mavenDependenciesResolver().newInstance();
        }
        return mavenDependenciesResolver;
    }

    public void setMavenDependenciesResolver(MavenDependenciesResolver mavenDependenciesResolver)
    {
        this.mavenDependenciesResolver = mavenDependenciesResolver;
    }

    private MavenMultiModuleAritfactMapping getMavenMultiModuleAritfactMapping() throws IllegalAccessException, InstantiationException
    {
        MavenMultiModuleAritfactMapping mavenMultiModuleArtifactMapping = new MuleMavenMultiModuleArtifactMapping();
        if (annotation != null)
        {
            mavenMultiModuleArtifactMapping = annotation.mavenMultiModuleArtifactMapping().newInstance();
        }
        return mavenMultiModuleArtifactMapping;
    }
}
