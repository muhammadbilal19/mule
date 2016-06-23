/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

/**
 * Creates a {@link ClassLoader} to be used for running the JUnit test.
 */
public interface ClassLoaderRunnerFactory
{

    /**
     * @param testClass the test class about to be executed in order to allow getting its annotations
     * @param classSpace a classSpace that defines the set of {@link java.net.URL} to be used for building the classloader
     * @return the {@link ClassLoader} to be used for running the test.
     */
    ClassLoader createClassLoader(Class<?> testClass, ClassSpace classSpace);
}
