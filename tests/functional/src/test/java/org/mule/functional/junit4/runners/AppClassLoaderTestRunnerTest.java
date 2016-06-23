/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import static java.util.Arrays.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoader;

import java.io.File;
import java.net.URLClassLoader;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for validating logic for building the different classloaders.
 */
@RunWith(ArtifactClassloaderTestRunner.class)
@ArtifactClassLoaderRunnerConfig(
        mavenMultiModuleArtifactMapping = AppClassLoaderTestRunnerTest.ModuleArtifactMapping.class
)
@MuleClassPathClassifierConfig(usePluginClassSpace = true)
@DependencyGraphMavenDependenciesResolverConfig(dependenciesGraphFile = "/target/test-classes/isolation/x-project-dep-graph.dot")
public class AppClassLoaderTestRunnerTest
{
    private List<String> appArtifacts;
    private List<String> pluginArtifacts;

    @Before
    public void before()
    {
        assertThat(Thread.currentThread().getContextClassLoader(), instanceOf(ArtifactClassLoader.class));
        ClassLoader classLoader = this.getClass().getClassLoader();
        assertThat(classLoader, instanceOf(ArtifactClassLoader.class));
        assertThat(classLoader, instanceOf(URLClassLoader.class));

        URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
        appArtifacts = getArtifacts(urlClassLoader);
        pluginArtifacts = getArtifacts((URLClassLoader) urlClassLoader.getParent());
    }

    private List<String> getArtifacts(URLClassLoader urlClassLoader)
    {
        return stream(urlClassLoader.getURLs()).map(url ->
                                             {
                                                 File artifactId = new File(url.getFile()).getParentFile().getParentFile();
                                                 File groupId = artifactId.getParentFile();
                                                 return groupId.getName() + ":" + artifactId.getName();
                                             }).collect(Collectors.toList());
    }

    @Test
    public void validateAppClassloader()
    {
        assertThat(appArtifacts, containsInAnyOrder("tests:functional", "junit:junit", "hamcrest:hamcrest-core"));
    }

    @Test
    public void validatePluginClassLoader()
    {
        assertThat(pluginArtifacts, containsInAnyOrder("tests:functional", "guava:guava", "commons-logging:commons-logging", "commons-beanutils:commons-beanutils"));

    }

    public static class ModuleArtifactMapping implements MavenMultiModuleArtifactMapping
    {
        @Override
        public String mapModuleFolderNameFor(String artifactId)
        {
            return "/tests/functional/";
        }
    }
}
