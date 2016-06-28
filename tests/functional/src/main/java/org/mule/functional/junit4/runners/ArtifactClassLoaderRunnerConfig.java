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
 * Specifies a configuration needed by {@link ArtifactClassloaderTestRunner} in order to
 * run the test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface ArtifactClassLoaderRunnerConfig
{

    /**
     * @return a class of {@link ClassPathURLsProvider} that defines the implementation to be used for the runner.
     * If no class is defined it will use the default implementation. See {@link DefaultClassPathURLsProvider}
     */
    Class<? extends ClassPathURLsProvider> classPathURLsProvider() default DefaultClassPathURLsProvider.class;

    /**
     * @return a class of {@link MavenDependenciesResolver} that defines the implementation to be used for the runner.
     * If no class is defined it will use the default implementation. See {@link DependencyGraphMavenDependenciesResolver}
     */
    Class<? extends MavenDependenciesResolver> mavenDependenciesResolver() default DependencyGraphMavenDependenciesResolver.class;

    /**
     * @return a class of {@link MavenMultiModuleArtifactMapping} that defines the implementation to be used for the runner.
     * If no class is defined it will use the default implementation. See {@link MuleMavenMultiModuleArtifactMapping}
     */
    Class<? extends MavenMultiModuleArtifactMapping> mavenMultiModuleArtifactMapping() default MuleMavenMultiModuleArtifactMapping.class;

    /**
     * @return a class of {@link ClassLoaderRunnerFactory} that defines the implementation to be used for the runner.
     * If no class is defined it will use the default implementation. See {@link MuleClassLoaderRunnerFactory}
     */
    Class<? extends ClassLoaderRunnerFactory> classLoaderRunnerFactory() default MuleClassLoaderRunnerFactory.class;

    /**
     * @return a class of {@link ClassPathClassifier} that defines the implementation to be used for the runner.
     * If no class is defined it will use the default implementation. See {@link MuleClassPathClassifier}
     */
    Class<? extends ClassPathClassifier> classPathClassifier() default MuleClassPathClassifier.class;

    /**
     * @return a comma separated list of packages to be added as PARENT_ONLY for the
     * container classloader, default packages are "org.junit,junit,org.hamcrest,org.mockito".
     */
    String extraBootPackages() default "org.junit,junit,org.hamcrest,org.mockito";


    /**
     * @return array of classes defining extensions that need to be added as plugins to the classloader used
     * to execute the test. Non null;
     */
    Class[] extensions() default {};

    /**
     * @return a comma separated list of groupId:artifactId:type (it does support wildcards org.mule:*:* or *:mule-core:* but
     * only starts with for partial matching org.mule*:*:*) that would be used in order to exclude artifacts that should not be added to
     * the application classloader due to they will be already exposed through plugin or container. This will not be applied to those
     * artifacts that are declared as test scope but it will be used for filtering its dependencies.
     * Default exclusion is "org.mule:*:*,org.mule.modules*:*:*,org.mule.transports:*:*,org.mule.mvel:*:*,org.mule.common:*:*"
     */
    String appPackageExclusions() default "org.mule:*:*,org.mule.modules*:*:*,org.mule.transports:*:*,org.mule.mvel:*:*,org.mule.common:*:*";
}
