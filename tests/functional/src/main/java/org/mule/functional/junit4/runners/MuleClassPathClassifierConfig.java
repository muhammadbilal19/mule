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
 * Defines a configuration for {@link MuleClassPathClassifier}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface MuleClassPathClassifierConfig
{
    /**
     * @return a comma separated list of groupId:artifactId:type (it does support wildcards org.mule:*:* or *:mule-core:* but
     * only starts with for partial matching org.mule*:*:*) that would be used in order to exclude artifacts that should not be added to
     * the application classloader due to they will be already exposed through plugin or container. This will not be applied to those
     * artifacts that are declared as test scope but it will be used for filtering its dependencies.
     * Default exclusion is "org.mule*:*:*"
     */
    String appExclusions() default "org.mule*:*:*";

    /**
     * @return flag to enable the runner to have a plugin class space (and classloader) between the application classloader
     * and the container classloader, this will contain any compile dependency declared in the pom being tested.
     * It is mostly used for testing extensions. Default value is false.
     */
    boolean usePluginClassSpace() default false;
}
