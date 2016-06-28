/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * A {@link ClassPathClassifier} is responsible for building the {@link ArtifactUrlClassification} that would be used for creating
 * the {@link ClassLoader} to run the test.
 */
public interface ClassPathClassifier
{
    /**
     * Implements the logic for classifying how the URLs provided for dependencies should be arranged
     * @param klass the class of the test being tested
     * @param classPathURLs current URLs for the classpath provided by JUnit (it is the complete list of URLs)
     * @param allDependencies Maven dependencies for the given artifact tested (with its duplications). The map has as key an artifact and values are its dependencies
     * @param mavenMultiModuleArtifactMapping mapper used to identify a multi-module project folder from its artifact metadata
     * @return a {@link ArtifactUrlClassification} with the corresponding {@link URL}s
     */
    ArtifactUrlClassification classify(Class<?> klass, Set<URL> classPathURLs, LinkedHashMap<MavenArtifact, Set<MavenArtifact>> allDependencies, MavenMultiModuleArtifactMapping mavenMultiModuleArtifactMapping);
}
