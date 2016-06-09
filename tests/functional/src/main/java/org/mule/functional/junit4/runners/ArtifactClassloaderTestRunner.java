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
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runner that creates a similar classloader isolation hierarchy as Mule uses on runtime.
 * The classloaders here created for running the test have the following hierarchy, from parent to child:
 * ContainerClassLoader (it also adds junit and org.hamcrest packages as PARENT_ONLY look up strategy)
 */
public class ArtifactClassloaderTestRunner extends Runner
{

    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String MAVEN_DEPENDENCIES_DELIMITER = ":";
    private static final String DOT_CHARACTER = ".";
    private static final String MAVEN_COMPILE_SCOPE = "compile";
    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";
    private static final String TARGET_CLASSES = "/target/classes/";

    private static final String DEPENDENCIES_LIST_FILE = "dependencies.list";

    private final Object innerRunner;
    private final Class<?> innerRunnerClass;

    private static ClassLoader artifactClassLoader;

    /**
     * Creates a Runner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public ArtifactClassloaderTestRunner(Class<?> klass) throws InitializationError
    {
        try
        {
            logger.debug("Running with runner: '{}'", this.getClass().getName());
            artifactClassLoader = buildArtifactClassloader(klass);
            innerRunnerClass = artifactClassLoader.loadClass(getDelegateRunningToOn(klass).getName());
            Class<?> testClass = artifactClassLoader.loadClass(klass.getName());
            innerRunner = innerRunnerClass.cast(innerRunnerClass.getConstructor(Class.class).newInstance(testClass));
        }
        catch (Exception e)
        {
            throw new InitializationError(e);
        }
    }

    /**
     * @param testClass
     * @return the delegate {@link Runner} to be used or {@link JUnit4} if no one is defined.
     */
    public Class<? extends Runner> getDelegateRunningToOn(Class<?> testClass)
    {
        Class<? extends Runner> runnerClass = JUnit4.class;
        ArtifactRunningDelegate annotation = testClass.getAnnotation(ArtifactRunningDelegate.class);

        if(annotation != null)
        {
            runnerClass = annotation.value();
        }

        return runnerClass;
    }

    public String getDependenciesListFileName(Class<?> testClass)
    {
        String dependenciesListFileName = DEPENDENCIES_LIST_FILE;
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if(annotation != null)
        {
            dependenciesListFileName = annotation.dependenciesListFileName();
        }

        return dependenciesListFileName;
    }

    public Set<String> getExtraBootPackages(Class<?> testClass)
    {
        String extraPackages = "org.junit,junit,org.hamcrest";
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if(annotation != null)
        {
            extraPackages = annotation.extraBootPackages();
        }

        return Sets.newHashSet(extraPackages.split(","));
    }

    private ClassLoader buildArtifactClassloader(Class<?> klass) throws IOException, URISyntaxException
    {
        ClassLoader classloader = ArtifactClassloaderTestRunner.class.getClassLoader();
        URL mavenDependenciesFile = classloader.getResource(getDependenciesListFileName(klass));
        if (mavenDependenciesFile != null)
        {
            Path dependenciesPath = Paths.get(mavenDependenciesFile.toURI());
            BasicFileAttributes view = Files.getFileAttributeView(dependenciesPath, BasicFileAttributeView.class).readAttributes();
            logger.debug("Building classloader hierarchy using maven dependency list file: '{}', created: {}, last modified: {}", mavenDependenciesFile, view.creationTime(), view.lastModifiedTime());
            // get the urls from the java.class.path system property (works for maven or when running tests from IDEs)
            final List<URL> urls = new LinkedList<>();
            for (String file : System.getProperty("java.class.path").split(":"))
            {
                urls.add(new File(file).toURI().toURL());
            }

            // maven-dependency-plugin adds a few extra lines to the top
            List<String> mavenDependencies = Files.readAllLines(new File(mavenDependenciesFile.getFile()).toPath(),
                                                                Charset.defaultCharset()).stream()
                    .filter(line -> line.length() - line.replace(MAVEN_DEPENDENCIES_DELIMITER, "").length() >= 4).collect(Collectors.toList());

            // Lists of artifacts to be used by different classloaders
            List<URL> pluginURLs = new ArrayList<>();
            List<URL> applicationURLs = new ArrayList<>();

            // plugin libraries should be all the dependencies with scope 'compile'
            mavenDependencies.stream().filter(dependencyStringLine -> isArtifactOf(dependencyStringLine, MAVEN_COMPILE_SCOPE)).forEach(mavenDependency -> addURL(pluginURLs, mavenDependency, urls));

            // when multi-module is used classes folders should be added as plugin classloader libraries for this artifact
            String currentArtifactFolderName = new File(System.getProperty("user.dir")).getName();
            for (URL url : urls)
            {
                String file = url.getFile().trim();
                if(file.endsWith(currentArtifactFolderName + TARGET_CLASSES))
                {
                    pluginURLs.add(url);
                }
                else if(file.endsWith(TARGET_CLASSES))
                {
                    String fileParent = new File(file).getParentFile().getParentFile().getName();
                    Optional<String> dependency = mavenDependencies.stream().filter(mavenDependency ->
                    {
                        MavenArtifact artifact = parseMavenArtifact(mavenDependency);
                        // Just for the time being use contains but it would be better to have the folders with exactly the same artifactId to improve this filter
                        // TODO: the folder name of the module should be the same as artifactId in order to improve this check!
                        return artifact.getArtifactId().contains(fileParent) && artifact.getScope().equals(MAVEN_COMPILE_SCOPE);
                    }).findFirst();
                    if(dependency.isPresent())
                    {
                        pluginURLs.add(url);
                    }
                }
            }

            // Tests classes should be app classloader
            for (URL url : urls)
            {
                if(url.getFile().trim().endsWith(currentArtifactFolderName + TARGET_TEST_CLASSES))
                {
                    applicationURLs.add(url);
                }
            }

            // The container contains anything that is not application either extension classloader urls
            List<URL> containerURLs = new ArrayList<>();
            containerURLs.addAll(urls);
            containerURLs.removeAll(pluginURLs);
            containerURLs.removeAll(applicationURLs);

            // Container classloader
            logger.debug("CONTAINER classloader: [");
            containerURLs.forEach(e -> logger.debug(e.getFile()));
            logger.debug("]");
            ArtifactClassLoader containerClassLoader = new TestContainerClassLoaderFactory(getExtraBootPackages(klass)).createContainerClassLoader(new URLClassLoader(containerURLs.toArray(new URL[containerURLs.size()]), getClass().getClassLoader()));

            // Extension/Plugin classlaoder
            logger.debug("PLUGIN classloader: [");
            pluginURLs.forEach(e -> logger.debug(e.getFile()));
            logger.debug("]");
            MuleArtifactClassLoader pluginClassLoader = new MuleArtifactClassLoader("plugin", pluginURLs.toArray(new URL[pluginURLs.size()]), containerClassLoader.getClassLoader(), containerClassLoader.getClassLoaderLookupPolicy());

            // Application classloader
            logger.debug("APPLICATION classloader: [");
            applicationURLs.forEach(e -> logger.debug(e.getFile()));
            logger.debug("]");
            classloader = new MuleArtifactClassLoader("application", applicationURLs.toArray(new URL[applicationURLs.size()]), pluginClassLoader.getClassLoader(), pluginClassLoader.getClassLoaderLookupPolicy()).getClassLoader();
        }

        return classloader;
    }

    private boolean isArtifactOf(String line, String scope)
    {
        return line.endsWith(scope);
    }

    private void addURL(List<URL> listBuilder, String mavenDependencyString, List<URL> urls)
    {
        MavenArtifact mavenArtifact = parseMavenArtifact(mavenDependencyString);
        Optional<URL> artifact = urls.stream().filter(filePath -> filePath.getFile().contains(mavenArtifact.getGroupIdAsPath() + File.separator + mavenArtifact.getArtifactId())).findFirst();
        if (artifact.isPresent())
        {
            listBuilder.add(artifact.get());
        }
    }

    private MavenArtifact parseMavenArtifact(String mavenDependencyString)
    {
        StringTokenizer tokenizer = new StringTokenizer(mavenDependencyString, MAVEN_DEPENDENCIES_DELIMITER);
        String groupId = tokenizer.nextToken().trim();
        String artifactId = tokenizer.nextToken().trim();
        String type = tokenizer.nextToken().trim();
        String version = tokenizer.nextToken().trim();
        String scope = tokenizer.nextToken().trim();
        return new MavenArtifact(groupId, artifactId, type, version, scope);
    }

    @Override
    public Description getDescription()
    {
        try
        {
            return (Description) innerRunnerClass.getMethod("getDescription").invoke(innerRunner);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e)
        {
            throw new RuntimeException("Could not get description", e);
        }
    }

    @Override
    public void run(RunNotifier notifier)
    {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(artifactClassLoader);
            innerRunnerClass.getMethod("run", RunNotifier.class).invoke(innerRunner, notifier);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e)
        {
            notifier.fireTestFailure(new Failure(getDescription(), e));
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(original);
            artifactClassLoader = null;
        }
    }

    private class MavenArtifact
    {
        private String groupId;
        private String artifactId;
        private String type;
        private String version;
        private String scope;

        public MavenArtifact(String groupId, String artifactId, String type, String version, String scope)
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.type = type;
            this.version = version;
            this.scope = scope;
        }

        public String getGroupId()
        {
            return groupId;
        }

        public String getGroupIdAsPath()
        {
            return getGroupId().replace(DOT_CHARACTER, File.separator);
        }

        public String getArtifactId()
        {
            return artifactId;
        }

        public String getType()
        {
            return type;
        }

        public String getVersion()
        {
            return version;
        }

        public String getScope()
        {
            return scope;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            MavenArtifact that = (MavenArtifact) o;

            if (!groupId.equals(that.groupId))
            {
                return false;
            }
            if (!artifactId.equals(that.artifactId))
            {
                return false;
            }
            if (!type.equals(that.type))
            {
                return false;
            }
            if (!version.equals(that.version))
            {
                return false;
            }
            return scope.equals(that.scope);

        }

        @Override
        public int hashCode()
        {
            int result = groupId.hashCode();
            result = 31 * result + artifactId.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + version.hashCode();
            result = 31 * result + scope.hashCode();
            return result;
        }
    }
}
