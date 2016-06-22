/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.util.Map;
import java.util.Set;

/**
 * Resolves maven dependencies for the artifact being tested.
 */
public interface MavenDependenciesResolver
{

    /**
     * @param testClass
     * @return based on the testClass it would generate the dependencies for the maven artifact that the class belongs to.
     * It will return a {@link Map} with each dependency as key and for each key a {@link Set} of its dependencies.
     */
    Map<MavenArtifact, Set<MavenArtifact>> buildDependencies(Class<?> testClass);
}
