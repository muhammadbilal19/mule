/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4.runners;

import org.mule.runtime.core.util.SerializationUtils;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.classloader.DisposableClassLoader;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String MAVEN_PROVIDED_SCOPE = "provided";
    private static final String TARGET_TEST_CLASSES = "/target/test-classes/";
    private static final String TARGET_CLASSES = "/target/classes/";

    private static final String DEPENDENCIES_LIST_FILE = TARGET_TEST_CLASSES + "dependencies.list";
    private static final String DEPENDENCIES_GRAPH_FILE = TARGET_TEST_CLASSES + "dependency-graph.dot";

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
            dependenciesListFileName = annotation.dependenciesListFile();
        }

        return dependenciesListFileName;
    }

    public String getDependenciesGraphFileName(Class<?> testClass)
    {
        String dependenciesListFileName = DEPENDENCIES_GRAPH_FILE;
        ArtifactClassLoaderRunnerConfig annotation = testClass.getAnnotation(ArtifactClassLoaderRunnerConfig.class);

        if (annotation != null)
        {
            dependenciesListFileName = annotation.dependenciesGraphFile();
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
        final String userDir = System.getProperty("user.dir");
        final File dependenciesFile = new File(userDir, getDependenciesListFileName(klass));
        final File dependenciesGraphFile = new File(userDir, getDependenciesGraphFileName(klass));

        if (!dependenciesFile.exists())
        {
            throw new RuntimeException(String.format("Unable to run test a '%s' was not found. Run 'mvn process-resources' to ensure this file is created before running the test", DEPENDENCIES_LIST_FILE));
        }
        if (!dependenciesGraphFile.exists())
        {
            throw new RuntimeException(String.format("Unable to run test a '%s' was not found. Run 'mvn process-resources' to ensure this file is created before running the test", DEPENDENCIES_GRAPH_FILE));
        }

        final Set<URL> urls = getFullClassPathUrls();

        Path dependenciesPath = Paths.get(dependenciesFile.toURI());
        BasicFileAttributes view = Files.getFileAttributeView(dependenciesPath, BasicFileAttributeView.class).readAttributes();
        logger.debug("Building classloader hierarchy using maven dependency list file: '{}', created: {}, last modified: {}", dependenciesFile, view.creationTime(), view.lastModifiedTime());
        // maven-dependency-plugin adds a few extra lines to the top
        List<MavenArtifact> mavenDependencies = toMavenArtifacts(dependenciesFile);

        // Provided dependencies (with dependencies) are obtained first in order to avoid including them in application classloader if needed
        Set<URL> containerDependenciesProvided = new HashSet<>();
        mavenDependencies.stream().filter(artifact -> artifact.isProvidedScope()).forEach(artifact -> {fillDependencies(artifact, dependenciesGraphFile); addURL(containerDependenciesProvided, artifact, urls, Collections.EMPTY_LIST);});

        // Lists of urls to be used by different classloaders
        Set<URL> pluginURLs = new HashSet<>();
        Set<URL> applicationURLs = new HashSet<>();
        
        // plugin libraries should be all the dependencies with scope 'compile'
        mavenDependencies.stream().filter(artifact -> artifact.isCompileScope()).forEach(artifact -> {fillDependencies(artifact, dependenciesGraphFile); addURL(pluginURLs, artifact, urls, Collections.EMPTY_LIST);});

        // plugin libraries should be all the dependencies with scope 'test'
        mavenDependencies.stream().filter(artifact -> artifact.isTestScope()).forEach(artifact -> {fillDependencies(artifact, dependenciesGraphFile); addURL(applicationURLs, artifact, urls, containerDependenciesProvided);});

        // when multi-module is used classes folders should be added as plugin classloader libraries for this artifact
        String currentArtifactFolderName = new File(System.getProperty("user.dir")).getName();

        // /target-classes only for the current artifact being tested
        urls.stream().filter(url -> url.getFile().trim().endsWith(currentArtifactFolderName + TARGET_CLASSES)).forEach(url -> pluginURLs.add(url));

        // Tests classes should be app classloader
        applicationURLs.addAll(urls.stream().filter(url -> url.getFile().trim().endsWith(currentArtifactFolderName + TARGET_TEST_CLASSES)).collect(Collectors.toList()));

        // The container contains anything that is not application either extension classloader urls
        Set<URL> containerURLs = new HashSet<>();
        containerURLs.addAll(urls);
        containerURLs.removeAll(pluginURLs);
        containerURLs.removeAll(applicationURLs);
        containerURLs.addAll(containerDependenciesProvided);

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

    private void logClassLoaderUrls(String classLoaderName, Collection<URL> containerURLs)
    {
        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder(classLoaderName).append(" classloader: [");
            containerURLs.forEach(e -> builder.append("\n").append(e.getFile()));
            builder.append("\n]");
            logger.debug(builder.toString());
        }
    }

    private void fillDependencies(MavenArtifact artifact, File dependenciesGraphFile)
    {
        try
        {
            artifact.setDependencies(buildDependenciesFor(artifact, dependenciesGraphFile));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could get dependencies for artifact: " + artifact, e);
        }
    }

    private Set<MavenArtifact> buildDependenciesFor(MavenArtifact artifact, File dependenciesGraph) throws IOException
    {
        //TODO too ugly!
        return Files.readAllLines(dependenciesGraph.toPath(),
                                  Charset.defaultCharset()).stream()
                .filter(line -> line.contains("->") &&
                                line.split("->")[0].contains(artifact.getGroupId() + MAVEN_DEPENDENCIES_DELIMITER + artifact.getArtifactId())).map(artifactLine -> parseMavenArtifactFromDepGraph(artifactLine)).collect(Collectors.toSet());
    }
    
    /**
     * Gets the urls from the {@code java.class.path} and {@code sun.boot.class.path} system properties
     */
    private Set<URL> getFullClassPathUrls() throws MalformedURLException
    {
        final Set<URL> urls = new HashSet<>();
        addUrlsFromSystemProperty(urls, "java.class.path");
        addUrlsFromSystemProperty(urls, "sun.boot.class.path");

        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder("ClassPath:");
            urls.stream().forEach(url -> builder.append("\n").append(url));
            logger.debug(builder.toString());
        }

        return urls;
    }

    private void addUrlsFromSystemProperty(Collection<URL> urls, String propertyName) throws MalformedURLException
    {
        for (String file : System.getProperty(propertyName).split(":"))
        {
            urls.add(new File(file).toURI().toURL());
        }
    }

    private List<MavenArtifact> toMavenArtifacts(File mavenDependenciesFile) throws IOException
    {
        return Files.readAllLines(mavenDependenciesFile.toPath(),
                                  Charset.defaultCharset()).stream()
                .filter(line -> line.length() - line.replace(MAVEN_DEPENDENCIES_DELIMITER, "").length() >= 4).map(artifactLine -> parseMavenArtifact(artifactLine.trim())).collect(Collectors.toList());
    }

    private static final Map<String, String> moduleMapping = new HashMap();

    static
    {
        // Test artifacts
        moduleMapping.put("mule-tests-functional", "/tests/functional/target/");
        moduleMapping.put("mule-tests-unit", "/tests/unit/target/");
        moduleMapping.put("mule-tests-infrastructure", "/tests/infrastructure/target/");

        // Bootstrap artifacts
        moduleMapping.put("mule-module-artifact", "/modules/artifact/target/");

        // Modules
        moduleMapping.put("mule-core", "/core/target/classes");
        moduleMapping.put("mule-module-extensions-spring-support", "/modules/extensions-spring-support/target/");
        moduleMapping.put("mule-module-tls", "/modules/tls/target/");
        moduleMapping.put("mule-module-extensions-support", "/modules/extensions-support/target/");
        moduleMapping.put("mule-module-spring-config", "/modules/spring-config/target/");
        moduleMapping.put("mule-module-file-extension-common", "/modules/file-extension-common/target/");
        moduleMapping.put("mule-transport-sockets", "/transports/sockets/target/");
        moduleMapping.put("mule-module-container", "/modules/container/target/");
        //moduleMapping.put("mule-module-launcher", "/modules/launcher/target/");
        //moduleMapping.put("mule-module-reboot", "/modules/reboot/target/");
        //moduleMapping.put("mule-module-client", "/modules/client/target/");
    }

    private void addURL(final Collection<URL> collection, final MavenArtifact artifact, final Collection<URL> urls, final Collection<URL> exclusions)
    {
        // Resolves the artifact from maven repository
        Optional<URL> artifactURL = urls.stream().filter(filePath -> filePath.getFile().contains(artifact.getGroupIdAsPath() + File.separator + artifact.getArtifactId() + File.separator)).findFirst();
        if (artifactURL.isPresent())
        {
            if (!exclusions.contains(artifactURL.get()))
            {
                collection.add(artifactURL.get());
            }
            // It would also include its dependencies
            artifact.getDependencies().forEach(dependency -> addURL(collection, dependency, urls, exclusions));
        }
        else
        {
            addModuleURL(collection, artifact, urls, exclusions);
        }
    }

    private void addModuleURL(Collection<URL> collection, MavenArtifact artifact, Collection<URL> urls, Collection<URL> exclusions)
    {
        final String urlSuffix = moduleMapping.get(artifact.artifactId);
        if (urlSuffix == null)
        {
            throw new IllegalArgumentException("Cannot locate artifact as multi-module dependency: '" + artifact + "', mapping used is: " + moduleMapping);
        }
        final Optional<URL> localFile = urls.stream().filter(url -> url.toString().contains(urlSuffix)).findFirst();
        if (localFile.isPresent())
        {
            if (!exclusions.contains(localFile.get()))
            {
                collection.add(localFile.get());
            }
            // It would also include its dependencies
            artifact.getDependencies().stream().forEach(dependency -> addURL(collection, dependency, urls, exclusions));
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
        String[] tokens = mavenDependencyString.split(MAVEN_DEPENDENCIES_DELIMITER);
        String groupId = tokens[0];
        String artifactId = tokens[1];
        String type = tokens[2];
        String version = tokens[3];
        String scope = tokens[4];
        return new MavenArtifact(groupId, artifactId, type, version, scope);
    }

    private MavenArtifact parseMavenArtifactFromDepGraph(String line)
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

            if (artifactClassLoader instanceof DisposableClassLoader)
            {
                try
                {
                    ((DisposableClassLoader) artifactClassLoader).dispose();
                }
                catch (Exception e)
                {
                    // Ignore
                }
            }
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
        private Set<MavenArtifact> dependencies = Collections.EMPTY_SET;

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

        public boolean isProvidedScope()
        {
            return MAVEN_PROVIDED_SCOPE.equals(scope);
        }

        public void setDependencies(Set<MavenArtifact> dependencies)
        {
            this.dependencies = dependencies;
        }

        public Set<MavenArtifact> getDependencies()
        {
            return dependencies;
        }

        @Override
        public String toString()
        {
            return groupId + MAVEN_DEPENDENCIES_DELIMITER + artifactId + MAVEN_DEPENDENCIES_DELIMITER + type + MAVEN_DEPENDENCIES_DELIMITER + version + MAVEN_DEPENDENCIES_DELIMITER + scope;
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
