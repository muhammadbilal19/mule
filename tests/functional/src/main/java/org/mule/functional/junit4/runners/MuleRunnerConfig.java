/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the configuration used for {@link MuleClassLoaderRunnerFactory} when creating the {@link ClassLoader} to
 * be used when running the test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface MuleRunnerConfig
{
    /**
     * @return a comma separated list of packages to be added as PARENT_ONLY for the
     * container classloader, default packages are "org.junit,junit,org.hamcrest,org.mockito".
     */
    String extraBootPackages() default "org.junit,junit,org.hamcrest,org.mockito";
}
