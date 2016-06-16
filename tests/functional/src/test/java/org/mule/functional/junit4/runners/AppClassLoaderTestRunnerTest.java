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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for validating logic for building the different classloaders.
 */
@RunWith(ArtifactClassloaderTestRunner.class)
@ArtifactClassLoaderRunnerConfig(mavenMultiModuleArtifactMapping = AppClassLoaderTestRunnerTest.ModuleArtifactMapping.class)
@DependencyGraphMavenDependenciesResolverConfig(dependenciesGraphFile = "/target/test-classes/isolation/x-project-dep-graph.dot")
public class AppClassLoaderTestRunnerTest
{
    @Test
    public void validateAppClassloader() {
        assertThat(Thread.currentThread().getContextClassLoader(), instanceOf(ArtifactClassLoader.class));
        ClassLoader classLoader = this.getClass().getClassLoader();
        assertThat(classLoader, instanceOf(ArtifactClassLoader.class));
        assertThat(classLoader, instanceOf(URLClassLoader.class));

        URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
        URL[] urls = urlClassLoader.getURLs();

        List<String> appArtifacts = stream(urls).map(url ->
           {
               File artifactId = new File(url.getFile()).getParentFile().getParentFile();
               File groupId = artifactId.getParentFile();
               return groupId.getName() + ":" + artifactId.getName();
           }).collect(Collectors.toList());

        assertThat(appArtifacts, containsInAnyOrder("junit:junit", "tests:functional", "hamcrest:hamcrest-core"));
    }

    public static class ModuleArtifactMapping implements MavenMultiModuleAritfactMapping
    {
        @Override
        public String mapModuleFolderNameFor(String artifactId)
        {
            return "/tests/functional/";
        }
    }
}
