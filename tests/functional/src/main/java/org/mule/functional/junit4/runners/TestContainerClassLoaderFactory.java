/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import org.mule.runtime.container.internal.ContainerClassLoaderFactory;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Extends the default {@link ContainerClassLoaderFactory} for testing in order to add boot packages.
 *
 * @since 4.0
 */
public class TestContainerClassLoaderFactory extends ContainerClassLoaderFactory
{

    private Set<String> extraBootPackages;

    public TestContainerClassLoaderFactory(Set<String> extraBootPackages)
    {
        this.extraBootPackages = extraBootPackages;
    }

    @Override
    public Set<String> getBootPackages()
    {
        return ImmutableSet.<String>builder().addAll(super.getBootPackages()).addAll(extraBootPackages).build();
    }

    @Override
    public Set<String> getSystemPackages()
    {
        return super.getSystemPackages();
    }
}
