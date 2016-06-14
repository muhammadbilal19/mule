/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import org.mule.runtime.core.util.SerializationUtils;
import org.mule.runtime.module.artifact.classloader.DisposableClassLoader;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 */
public abstract class AbstractClassLoaderIsolatedTestRunner extends Runner
{
    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final Object innerRunner;
    protected final Class<?> innerRunnerClass;

    protected ClassLoader artifactClassLoader;

    /**
     * Creates a Runner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public AbstractClassLoaderIsolatedTestRunner(Class<?> klass) throws InitializationError
    {
        try
        {
            logger.debug("Running with runner: '{}'", this.getClass().getName());
            artifactClassLoader = buildArtifactClassloader(klass);
            innerRunnerClass = artifactClassLoader.loadClass(getDelegateRunningToOn(klass).getName());
            Class<?> testClass = artifactClassLoader.loadClass(klass.getName());
            innerRunner = innerRunnerClass.cast(innerRunnerClass.getConstructor(Class.class).newInstance(testClass));
        }
        catch (Exception e)
        {
            throw new InitializationError(e);
        }
    }

    protected abstract ClassLoader buildArtifactClassloader(Class<?> klass) throws Exception;

    /**
     * @param testClass
     * @return the delegate {@link Runner} to be used or {@link JUnit4} if no one is defined.
     */
    protected Class<? extends Runner> getDelegateRunningToOn(Class<?> testClass)
    {
        Class<? extends Runner> runnerClass = JUnit4.class;
        ClassLoaderIsolatedTestRunnerDelegateTo annotation = testClass.getAnnotation(ClassLoaderIsolatedTestRunnerDelegateTo.class);

        if (annotation != null)
        {
            runnerClass = annotation.value();
        }

        return runnerClass;
    }

    @Override
    public Description getDescription()
    {
        try
        {
            final byte[] serialized = SerializationUtils.serialize((Serializable) innerRunnerClass.getMethod("getDescription").invoke(innerRunner));
            return (Description) SerializationUtils.deserialize(serialized);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e)
        {
            throw new RuntimeException("Could not get description", e);
        }
    }

    @Override
    public void run(RunNotifier notifier)
    {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(artifactClassLoader);
            innerRunnerClass.getMethod("run", RunNotifier.class).invoke(innerRunner, notifier);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e)
        {
            notifier.fireTestFailure(new Failure(getDescription(), e));
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(original);

            if (artifactClassLoader instanceof DisposableClassLoader)
            {
                try
                {
                    ((DisposableClassLoader) artifactClassLoader).dispose();
                }
                catch (Exception e)
                {
                    // Ignore
                }
            }
            artifactClassLoader = null;
        }
    }

}
