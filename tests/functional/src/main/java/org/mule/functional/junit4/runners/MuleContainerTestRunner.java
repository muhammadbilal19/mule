/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import org.junit.runners.model.InitializationError;

/**
 * TODO: document!
 */
public class MuleContainerTestRunner extends AbstractClassLoaderIsolatedTestRunner
{
    /**
     * Creates a Runner to run {@code klass}
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public MuleContainerTestRunner(Class<?> klass) throws InitializationError
    {
        super(klass);
    }

    @Override
    protected ClassLoader buildArtifactClassloader(Class<?> klass)
    {
        return null;
    }
}
