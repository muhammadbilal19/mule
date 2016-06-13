/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4.runners;

import org.mule.runtime.core.util.SerializationUtils;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.classloader.MuleArtifactClassLoader;

import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    private static final String DOT_CHARACTER = ".";
    private static final String MAVEN_DEPENDENCIES_DELIMITER = ":";
    private static final String MAVEN_COMPILE_SCOPE = "compile";
    private static final String MAVEN_TEST_SCOPE = "test";
    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";
    private static final String TARGET_CLASSES = "/target/classes/";
    private static final String DEPENDENCIES_LIST_FILE = "dependencies.list";

    private final Object innerRunner;
    private final Class<?> innerRunnerClass;

    private ClassLoader artifactClassLoader;

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

        if (annotation != null)
        {
            runnerClass = annotation.value();
        }

        return runnerClass;
    }

    public String getDependenciesListFileName(Class<?> testClass)
    {
        String dependenciesListFileName = DEPENDENCIES_LIST_FILE;
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            dependenciesListFileName = annotation.dependenciesListFileName();
        }

        return dependenciesListFileName;
    }

    public Set<String> getExtraBootPackages(Class<?> testClass)
    {
        String extraPackages = "org.junit,junit,org.hamcrest,org.mockito";
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            extraPackages = annotation.extraBootPackages();
        }

        return Sets.newHashSet(extraPackages.split(","));
    }

    private ClassLoader buildArtifactClassloader(Class<?> klass) throws IOException, URISyntaxException
    {
        final File dependenciesFile = new File(System.getProperty("user.dir"), "target/test-classes/dependencies.list");
        if (!dependenciesFile.exists())
        {
            throw new RuntimeException(String.format("Unable to run test a '%s' was not found. Run 'mvn process-resources' to ensure the file is built", DEPENDENCIES_LIST_FILE));
        }

        Path dependenciesPath = Paths.get(dependenciesFile.toURI());
        BasicFileAttributes view = Files.getFileAttributeView(dependenciesPath, BasicFileAttributeView.class).readAttributes();
        logger.debug("Building classloader hierarchy using maven dependency list file: '{}', created: {}, last modified: {}", dependenciesFile, view.creationTime(), view.lastModifiedTime());
        final List<URL> urls = getFullClassPathUrls();

        // maven-dependency-plugin adds a few extra lines to the top
        List<MavenArtifact> mavenDependencies = toMavenArtifacts(dependenciesFile.toURL());

        // Lists of artifacts to be used by different classloaders
        List<URL> pluginURLs = new ArrayList<>();
        List<URL> applicationURLs = new ArrayList<>();

        // plugin libraries should be all the dependencies with scope 'compile'
        mavenDependencies.stream().filter(artifact -> artifact.isCompileScope()).forEach(artifact -> addURL(pluginURLs, artifact, urls));

        // plugin libraries should be all the dependencies with scope 'test'
        mavenDependencies.stream().filter(artifact -> artifact.isTestScope()).forEach(artifact -> addURL(applicationURLs, artifact, urls));

        // when multi-module is used classes folders should be added as plugin classloader libraries for this artifact
        String currentArtifactFolderName = new File(System.getProperty("user.dir")).getName();

        // /target-classes only for the current artifact being tested
        urls.stream().filter(url -> url.getFile().trim().endsWith(currentArtifactFolderName + TARGET_CLASSES)).forEach(url -> pluginURLs.add(url));

        // Tests classes should be app classloader
        applicationURLs.addAll(urls.stream().filter(url -> url.getFile().trim().endsWith(currentArtifactFolderName + TARGET_TEST_CLASSES)).collect(Collectors.toList()));


        // The container contains anything that is not application either extension classloader urls
        List<URL> containerURLs = new ArrayList<>();
        containerURLs.addAll(urls);
        containerURLs.removeAll(pluginURLs);
        containerURLs.removeAll(applicationURLs);
        final String localRepository = System.getProperty("localRepository", "/Users/pablokraan/.m2/repository");
        logger.debug("Using maven local repository: " + localRepository);
        containerURLs.add(new URL("file:" + localRepository +"/com/google/guava/guava/18.0/guava-18.0.jar"));

        // Container classLoader
        logClassLoaderUrls("CONTAINER", containerURLs);
        final TestContainerClassLoaderFactory testContainerClassLoaderFactory = new TestContainerClassLoaderFactory(getExtraBootPackages(klass));
        ArtifactClassLoader containerClassLoader = testContainerClassLoaderFactory.createContainerClassLoader(new SystemContainerClassLoader(containerURLs.toArray(new URL[containerURLs.size()]), testContainerClassLoaderFactory.getBootPackages()));

        // Extension/Plugin classLoader
        logClassLoaderUrls("PLUGIN", pluginURLs);
        MuleArtifactClassLoader pluginClassLoader = new MuleArtifactClassLoader("plugin", pluginURLs.toArray(new URL[pluginURLs.size()]), containerClassLoader.getClassLoader(), containerClassLoader.getClassLoaderLookupPolicy());

        // Application classLoader
        logClassLoaderUrls("APPLICATION", applicationURLs);
        return new MuleArtifactClassLoader("application", applicationURLs.toArray(new URL[applicationURLs.size()]), pluginClassLoader.getClassLoader(), pluginClassLoader.getClassLoaderLookupPolicy()).getClassLoader();
    }

    private void logClassLoaderUrls(String classLoaderName, List<URL> containerURLs)
    {
        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder(classLoaderName).append(" classloader: [");
            containerURLs.forEach(e -> builder.append("\n").append(e.getFile()));
            builder.append("\n]");
            logger.debug(builder.toString());
        }
    }

    /**
     * Gets the urls from the {@code java.class.path} and {@code sun.boot.class.path} system properties
     */
    private List<URL> getFullClassPathUrls() throws MalformedURLException
    {
        final List<URL> urls = new LinkedList<>();
        addUrlsFromSystemProperty(urls, "java.class.path");
        addUrlsFromSystemProperty(urls, "sun.boot.class.path");

        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder("ClassPath:");
            logger.debug(builder.toString());
            urls.stream().forEach(url -> builder.append("\n").append(url));
        }

        return urls;
    }

    private void addUrlsFromSystemProperty(List<URL> urls, String propertyName) throws MalformedURLException
    {
        for (String file : System.getProperty(propertyName).split(":"))
        {
            urls.add(new File(file).toURI().toURL());
        }
    }

    private List<MavenArtifact> toMavenArtifacts(URL mavenDependenciesFile) throws IOException
    {
        return Files.readAllLines(new File(mavenDependenciesFile.getFile()).toPath(),
                                  Charset.defaultCharset()).stream()
                .filter(line -> line.length() - line.replace(MAVEN_DEPENDENCIES_DELIMITER, "").length() >= 4).map(artifactLine -> parseMavenArtifact(artifactLine)).collect(Collectors.toList());
    }

    private static final Map<String, String> moduleMapping = new HashMap();

    static
    {
        moduleMapping.put("mule-module-container", "modules/container/target/classes/");
        moduleMapping.put("mule-tests-functional", "tests/functional/target/classes/");
        moduleMapping.put("mule-tests-unit", "tests/unit/target/classes/");
        moduleMapping.put("mule-module-launcher", "modules/launcher/target/classes/");
        moduleMapping.put("mule-module-reboot", "modules/reboot/target/classes/");
        moduleMapping.put("mule-tests-infrastructure", "tests/infrastructure/target/classes/");
        moduleMapping.put("mule-module-client", "modules/client/target/classes/");
    }

    private void addURL(List<URL> listBuilder, MavenArtifact artifact, List<URL> urls)
    {
        Optional<URL> artifactURL = urls.stream().filter(filePath -> filePath.getFile().contains(artifact.getGroupIdAsPath() + File.separator + artifact.getArtifactId() + File.separator)).findFirst();
        if (artifactURL.isPresent())
        {
            listBuilder.add(artifactURL.get());
        }
        else
        {
            if (artifact.isTestScope())
            {
                final String urlSuffix = moduleMapping.get(artifact.artifactId);
                if (urlSuffix != null)
                {
                    final Optional<URL> localFile = urls.stream().filter(url -> url.toString().endsWith(urlSuffix)).findFirst();
                    if (localFile.isPresent())
                    {
                        listBuilder.add(localFile.get());
                        return;
                    }
                }

                throw new IllegalArgumentException("Cannot locate artifact: " + artifact);

            }
        }
    }

    public static class SystemContainerClassLoader extends URLClassLoader
    {

        private final Set<String> bootPackages;

        public SystemContainerClassLoader(URL[] urls, Set<String> bootPackages)
        {
            super(urls, null);
            this.bootPackages = bootPackages;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException
        {
            Class<?> result = findLoadedClass(name);

            if (result != null)
            {
                return result;
            }

            if (isBootPackage(name))
            {
                return getSystemClassLoader().loadClass(name);
            }
            else
            {
                return findClass(name);
            }
        }

        private boolean isBootPackage(String name)
        {
            for (String bootPackage : bootPackages)
            {
                if (name.startsWith(bootPackage))
                {
                    return true;
                }
            }

            return false;
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
        final MavenArtifact mavenArtifact = new MavenArtifact(groupId, artifactId, type, version, scope);
        System.out.println(mavenArtifact);
        return mavenArtifact;
    }

    @Override
    public Description getDescription()
    {
        try
        {
            final byte[] serialized = SerializationUtils.serialize((Serializable) innerRunnerClass.getMethod("getDescription").invoke(innerRunner));
            return (Description) SerializationUtils.deserialize(serialized);
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

        public boolean isCompileScope()
        {
            return MAVEN_COMPILE_SCOPE.equals(scope);
        }

        public boolean isTestScope()
        {
            return MAVEN_TEST_SCOPE.equals(scope);
        }

        @Override
        public String toString()
        {
            return "MavenArtifact[groupId" + groupId + " artifactId: " + artifactId + " version:" + version + " type: " + type + " scope: " + scope + "]";
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

        public boolean isJunit()
        {
            return artifactId.contains("junit") || artifactId.contains("hamcrest") || artifactId.contains("mockito");
        }
    }
}
