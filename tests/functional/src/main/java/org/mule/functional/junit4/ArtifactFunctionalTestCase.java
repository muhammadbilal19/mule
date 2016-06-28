/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4;

import static org.mule.functional.junit4.runners.AnnotationUtils.getAnnotationAttributeFrom;
import static org.mule.functional.junit4.runners.AnnotationUtils.getAnnotationAttributeFromHierarchy;
import org.mule.functional.junit4.runners.ArtifactClassLoaderRunnerConfig;
import org.mule.functional.junit4.runners.ArtifactClassloaderTestRunner;
import org.mule.functional.junit4.runners.ClassLoaderIsolatedExtensionsManagerConfigurationBuilder;
import org.mule.functional.junit4.runners.RunnerDelegateTo;
import org.mule.runtime.core.api.config.ConfigurationBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.runner.RunWith;

@RunWith(ArtifactClassloaderTestRunner.class)
public abstract class ArtifactFunctionalTestCase extends FunctionalTestCase
{
    @Override
    protected void addBuilders(List<ConfigurationBuilder> builders)
    {
        super.addBuilders(builders);
        Class<?> runner = getAnnotationAttributeFrom(this.getClass(), RunWith.class, "value");
        if (runner == null || !runner.equals(ArtifactClassloaderTestRunner.class))
        {
            throw new IllegalStateException(this.getClass().getName() + " extends " + ArtifactFunctionalTestCase.class.getName()
                                            + " so it should be annotated to only run with: " + ArtifactClassloaderTestRunner.class + ". See " + RunnerDelegateTo.class + " for defining a delegate runner to be used.");
        }

        List<Class<?>[]> extensionsAnnotatedClasses = getAnnotationAttributeFromHierarchy(this.getClass(), ArtifactClassLoaderRunnerConfig.class, "extensions");
        if (!extensionsAnnotatedClasses.isEmpty())
        {
            Set<Class<?>> extensionsAnnotatedClassesNoDups = extensionsAnnotatedClasses.stream().flatMap(Arrays::stream).collect(Collectors.toSet());
            builders.add(0, new ClassLoaderIsolatedExtensionsManagerConfigurationBuilder(extensionsAnnotatedClassesNoDups.toArray(new Class<?>[0])));
        }
    }

}
