/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.junit4.runners;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassLoaderURLsBuilderTest {

    private Set<URL> urls;
    private MavenMultiModuleArtifactMapping mavenMultiModuleMapping;

    private ClassLoaderURLsBuilder builder;


    private MavenArtifact rootArtifact;
    private MavenArtifact commonsLangArtifact;
    private MavenArtifact gsonArtifact;
    private MavenArtifact commonsCliArtifact;
    private MavenArtifact dom4JArtifact;


    @Before
    public void setUp() throws MalformedURLException {
        buildDefaultURLs();
        buildDefaultArtifacts();
        mavenMultiModuleMapping = new MuleMavenMultiModuleArtifactMapping();
    }


    @Test
    public void excludeRootOnlyTestTransitiveDependencies() {
        MavenArtifact mavenArtifact = rootArtifact;
        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, buildDefaultDependencies());

        boolean shouldAddOnlyDependencies = true;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isTestScope());


        assertTrue(appURLs.isEmpty());
    }

    @Test
    public void onlyTestTransitiveDependencies() {
        MavenArtifact mavenArtifact = rootArtifact;
        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, buildDefaultDependencies());

        boolean shouldAddOnlyDependencies = false;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isTestScope());

        assertEquals(1, appURLs.size());
    }

    @Test
    public void excludeRootOnlyProvidedDependencies() {
        MavenArtifact mavenArtifact = rootArtifact;

        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, buildDefaultDependencies());

        boolean shouldAddOnlyDependencies = true;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isProvidedScope());


        assertEquals(2, appURLs.size());
    }

    @Test
    public void onlyProvidedDependencies() {
        MavenArtifact mavenArtifact = rootArtifact;

        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, buildDefaultDependencies());

        boolean shouldAddOnlyDependencies = false;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isProvidedScope());


        assertEquals(3, appURLs.size());
    }

    @Test
    public void excludeRootOnlyCompileDependencies() {
        MavenArtifact mavenArtifact = rootArtifact;
        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, buildDefaultDependencies());

        boolean shouldAddOnlyDependencies = true;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isCompileScope());

        assertEquals(2, appURLs.size());
    }

    @Test
    public void onlyCompileDependencies() {
        MavenArtifact mavenArtifact = rootArtifact;
        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, buildDefaultDependencies());

        boolean shouldAddOnlyDependencies = false;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isCompileScope());

        assertEquals(3, appURLs.size());
    }

    @Test
    public void excludeRootOnlyProvidedAndTransitiveDependencies() {
        LinkedHashMap<MavenArtifact, Set<MavenArtifact>> dependencies = new LinkedHashMap<>();

        dom4JArtifact = buildMavenArtifact(dom4JArtifact.getGroupId(), dom4JArtifact.getArtifactId(), dom4JArtifact.getType(), dom4JArtifact.getVersion(), "compile");

        // Dependencies
        Set<MavenArtifact> rootDependencies = new HashSet<>();
        rootDependencies.add(commonsCliArtifact);

        Set<MavenArtifact> commonsCliDependencies = new HashSet<>();
        commonsCliDependencies.add(dom4JArtifact);

        dependencies.put(rootArtifact, rootDependencies);
        dependencies.put(commonsCliArtifact, commonsCliDependencies);


        MavenArtifact mavenArtifact = rootArtifact;
        builder = new ClassLoaderURLsBuilder(urls, mavenMultiModuleMapping, dependencies);

        boolean shouldAddOnlyDependencies = true;
        boolean shouldAddTransitiveDepFromExcluded = true;
        Set<URL> appURLs = builder.buildClassLoaderURLs(shouldAddOnlyDependencies, shouldAddTransitiveDepFromExcluded, artifact -> artifact.equals(mavenArtifact), dependency -> dependency.isCompileScope());

        assertEquals(1, appURLs.size());
    }


    private MavenArtifact buildMavenArtifact(String groupId, String artifactId, String type, String version, String scope) {
        return new MavenArtifact(groupId, artifactId, type, version, scope);
    }

    private void buildDefaultURLs() throws MalformedURLException {
        ClassLoader classLoader = this.getClass().getClassLoader();
        URLClassLoader urlClassLoader = (URLClassLoader) classLoader;

        urls = stream(urlClassLoader.getURLs()).collect(Collectors.toSet());
        urls.add(buildRootArtifactURLMock());
        urls.add(buildCommonsLangArtifactURLMock());
        urls.add(buildCommonsCliArtifactURLMock());
        urls.add(buildDom4jCliArtifactURLMock());
        urls.add(buildGsonArtifactURLMock());
    }

    private URL buildRootArtifactURLMock() throws MalformedURLException {
        String s = File.separator;
        StringBuilder filePath = new StringBuilder();
        filePath.append(s).append("home").append(s).append("user").append(s).append(".m2").append(s).append("repository").append(s)
                .append("org").append(s)
                .append("my").append(s)
                .append("company").append(s)
                .append("core-artifact").append(s)
                .append("1.0.0").append(s)
                .append("core-artifact-1.0.0.jar");

        URL artifactURL = new URL("file", "", -1, filePath.toString());

        return artifactURL;
    }

    private URL buildCommonsLangArtifactURLMock() throws MalformedURLException {
        String s = File.separator;
        StringBuilder filePath = new StringBuilder();
        filePath.append(s).append("home").append(s).append("user").append(s).append(".m2").append(s).append("repository").append(s)
                .append("org").append(s)
                .append("apache").append(s)
                .append("commons").append(s)
                .append("commons-lang3").append(s)
                .append("3.4").append(s)
                .append("commons-lang3-3.4.jar");

        URL artifactURL = new URL("file", "", -1, filePath.toString());

        return artifactURL;
    }

    private URL buildGsonArtifactURLMock() throws MalformedURLException {
        String s = File.separator;
        StringBuilder filePath = new StringBuilder();
        filePath.append(s).append("home").append(s).append("user").append(s).append(".m2").append(s).append("repository").append(s)
                .append("com").append(s)
                .append("google").append(s)
                .append("code").append(s)
                .append("gson").append(s)
                .append("gson").append(s)
                .append("2.6.2").append(s)
                .append("gson-2.6.2.jar");

        URL artifactURL = new URL("file", "", -1, filePath.toString());

        return artifactURL;
    }

    private URL buildCommonsCliArtifactURLMock() throws MalformedURLException {
        String s = File.separator;
        StringBuilder filePath = new StringBuilder();
        filePath.append(s).append("home").append(s).append("user").append(s).append(".m2").append(s).append("repository").append(s)
                .append("commons-cli").append(s)
                .append("commons-cli").append(s)
                .append("1.2").append(s)
                .append("commons-cli-1.2.jar");

        URL artifactURL = new URL("file", "", -1, filePath.toString());

        return artifactURL;
    }

    private URL buildDom4jCliArtifactURLMock() throws MalformedURLException {
        String s = File.separator;
        StringBuilder filePath = new StringBuilder();
        filePath.append(s).append("home").append(s).append("user").append(s).append(".m2").append(s).append("repository").append(s)
                .append("dom4j").append(s)
                .append("dom4j").append(s)
                .append("1.6.1").append(s)
                .append("dom4j-1.6.1.jar");

        URL artifactURL = new URL("file", "", -1, filePath.toString());

        return artifactURL;
    }

    private void buildDefaultArtifacts() {
        rootArtifact = buildMavenArtifact("org.my.company", "core-artifact", "jar", "1.0.0", "compile");
        commonsLangArtifact = buildMavenArtifact("org.apache.commons", "commons-lang3", "jar", "3.4", "compile");
        gsonArtifact = buildMavenArtifact("com.google.code.gson", "gson", "jar", "2.6.2", "compile");
        commonsCliArtifact = buildMavenArtifact("commons-cli", "commons-cli", "jar", "1.2", "provided");
        dom4JArtifact = buildMavenArtifact("dom4j", "dom4j", "jar", "1.6.1", "provided");
    }

    private LinkedHashMap<MavenArtifact, Set<MavenArtifact>> buildDefaultDependencies() {
        LinkedHashMap<MavenArtifact, Set<MavenArtifact>> dependencies = new LinkedHashMap<>();

        // Dependencies
        Set<MavenArtifact> rootDependencies = new HashSet<>();
        rootDependencies.add(commonsLangArtifact);
        rootDependencies.add(gsonArtifact);
        rootDependencies.add(commonsCliArtifact);

        Set<MavenArtifact> commonsCliDependencies = new HashSet<>();
        commonsCliDependencies.add(dom4JArtifact);

        dependencies.put(rootArtifact, rootDependencies);
        dependencies.put(commonsCliArtifact, commonsCliDependencies);


        return dependencies;
    }
} 