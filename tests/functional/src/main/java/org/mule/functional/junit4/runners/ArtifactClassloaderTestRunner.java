/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4.runners;

import org.mule.runtime.container.ContainerClassLoaderFactory;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.classloader.MuleArtifactClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * TODO
 */
public class ArtifactClassloaderTestRunner extends Runner
{

    public static final String MAVEN_DEPENDECIES_DELIMITER = ":";
    public static final String DOT_CHARACTER = ".";
    public static final String MAVEN_COMPILE_SCOPE = "compile";
    public static final String MAVEN_TEST_SCOPE = "test";
    private final Object innerRunner;
    private final Class<?> innerRunnerClass;
    private static ClassLoader classLoader;

    /**
     * Creates a Runner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public ArtifactClassloaderTestRunner(Class<?> klass) throws InitializationError
    {
        String testFileClassName = klass.getName();
        try
        {
            if (classLoader == null)
            {
                classLoader = buildArtifactClassloader();
            }

            innerRunnerClass = classLoader.loadClass(BlockJUnit4ClassRunner.class.getName());
            Class<?> testClass = classLoader.loadClass(testFileClassName);
            innerRunner = innerRunnerClass.cast(innerRunnerClass.getConstructor(Class.class).newInstance(testClass));
        }
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | ClassNotFoundException | IOException e)
        {
            throw new InitializationError(e);
        }
    }

    private ClassLoader buildArtifactClassloader() throws IOException
    {
        ClassLoader classloader = ArtifactClassloaderTestRunner.class.getClassLoader();
        URL mavenDependenciesFile = classloader.getResource("dependencies.list");
        if (mavenDependenciesFile != null)
        {
            // classpath
            final URL[] urls = ((URLClassLoader) classloader).getURLs();

            List<String> mavenDependencies = Files.readAllLines(new File(mavenDependenciesFile.getFile()).toPath(), Charset.defaultCharset()).stream().filter(line -> line.length() - line.replace(MAVEN_DEPENDECIES_DELIMITER, "").length() >= 4).sorted().collect(Collectors.toList());

            // Build lists
            List<URL> extensionURLs = new ArrayList<>();
            List<URL> applicationURLs = new ArrayList<>();

            mavenDependencies.stream().forEach(mavenDependency -> {
                if(isArtifactOf(mavenDependency, MAVEN_COMPILE_SCOPE))
                {
                    addURL(extensionURLs, mavenDependency, urls);
                }
                else if(isArtifactOf(mavenDependency, MAVEN_TEST_SCOPE))
                {
                    //addURL(applicationURLs, mavenDependency, urls);
                }
            });

            // classes should be added as extension classloader for this artifact
            String currentArtifactFolderName = new File(System.getProperty("user.dir")).getName();
            for (URL url : urls)
            {
                String file = url.getFile().trim();
                if(file.endsWith(currentArtifactFolderName + "/target/classes/"))
                {
                    extensionURLs.add(url);
                }
                else if(file.endsWith("/target/classes/"))
                {
                    String fileParent = new File(file).getParentFile().getParentFile().getName();
                    Optional<String> dependency = mavenDependencies.stream().filter(mavenDependency ->
                    {
                        MavenArtifact artifact = parseMavenArtifact(mavenDependency);
                        // Just for the time being use contains but it would be better to have the folders with exactly the same artifactId to improve this filter
                        return artifact.getArtifactId().contains(fileParent) && artifact.getScope().equals(MAVEN_COMPILE_SCOPE);
                    }).findFirst();
                    if(dependency.isPresent())
                    {
                        extensionURLs.add(url);
                    }
                }
            }

            // tests should be app classloader
            for (URL url : urls)
            {
                if(url.getFile().trim().endsWith(currentArtifactFolderName + "/target/test-classes/"))
                {
                    applicationURLs.add(url);
                }
            }

            // The container contains anything that is not application either extension classloader urls
            List<URL> containerURLs = new ArrayList<>();
            containerURLs.addAll(Arrays.asList(urls));
            containerURLs.removeAll(extensionURLs);
            containerURLs.removeAll(applicationURLs);

            // Container classloader
            ArtifactClassLoader containerClassLoader = new ContainerClassLoaderFactory().createContainerClassLoader(ClassLoader.getSystemClassLoader());

            // Extension/Plugin classlaoder
            MuleArtifactClassLoader pluginClassLoader = new MuleArtifactClassLoader("plugin", extensionURLs.toArray(new URL[extensionURLs.size()]), containerClassLoader.getClassLoader(), containerClassLoader.getClassLoaderLookupPolicy());

            // Application classloader
            classloader = new MuleArtifactClassLoader("application", applicationURLs.toArray(new URL[applicationURLs.size()]), pluginClassLoader.getClassLoader(), pluginClassLoader.getClassLoaderLookupPolicy()).getClassLoader();
        }

        return classloader;
    }

    private boolean isArtifactOf(String line, String scope)
    {
        return line.endsWith(scope);
    }

    private void addURL(List<URL> listBuilder, String mavenDependencyString, URL[] urls)
    {
        MavenArtifact mavenArtifact = parseMavenArtifact(mavenDependencyString);
        Optional<URL> artifact = Arrays.stream(urls).filter(filePath -> filePath.getFile().contains(mavenArtifact.getGroupIdAsPath() + File.separator + mavenArtifact.getArtifactId())).findFirst();
        if (artifact.isPresent())
        {
            listBuilder.add(artifact.get());
        }
    }

    private MavenArtifact parseMavenArtifact(String mavenDependencyString)
    {
        StringTokenizer tokenizer = new StringTokenizer(mavenDependencyString, MAVEN_DEPENDECIES_DELIMITER);
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
            Thread.currentThread().setContextClassLoader(classLoader);
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
        }
    }

    private class MavenArtifact implements Comparable<MavenArtifact>
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

        @Override
        public int compareTo(MavenArtifact o)
        {
            int compare = groupId.compareTo(o.getGroupId());
            if(compare == 0) {
                compare = getArtifactId().compareTo(o.getArtifactId());
            }
            return compare;
        }
    }
}
