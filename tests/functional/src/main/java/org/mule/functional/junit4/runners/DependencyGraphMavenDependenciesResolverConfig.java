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
 * Defines configuration needed by {@link DependencyGraphMavenDependenciesResolver}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DependencyGraphMavenDependenciesResolverConfig
{

    /**
     * @return the file name for getting the maven dependencies graph with depgraph-maven-plugin
     */
    String dependenciesGraphFile() default "target/test-classes/dependency-graph.dot";

}
