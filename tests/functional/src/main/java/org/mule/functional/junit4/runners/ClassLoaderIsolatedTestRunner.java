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
 * JUnit {@link Runner} implementation that uses a classloader to load the test class and delegate runner
 * to run the tests.
 */
public class ClassLoaderIsolatedTestRunner extends Runner
{

    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final Object innerRunner;
    protected final Class<?> innerRunnerClass;

    protected ClassLoader classLoader;

    /**
     * Creates a Runner to run {@code final innerklass} using the given {@link ClassLoader}
     *
     * @param classLoader to be used for loading the test class and delegate runner
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public ClassLoaderIsolatedTestRunner(final ClassLoader classLoader, final Class<?> klass) throws InitializationError
    {
        try
        {
            logger.debug("Running with runner: '{}'", this.getClass().getName());
            this.classLoader = classLoader;
            innerRunnerClass = this.classLoader.loadClass(getDelegateRunningToOn(klass).getName());
            Class<?> testClass = this.classLoader.loadClass(klass.getName());
            innerRunner = innerRunnerClass.cast(innerRunnerClass.getConstructor(Class.class).newInstance(testClass));
        }
        catch (Exception e)
        {
            throw new InitializationError(e);
        }
    }

    /**
     * @param testClass
     * @return the delegate {@link Runner} to be used or {@link JUnit4} if no one is defined.
     */
    protected Class<? extends Runner> getDelegateRunningToOn(final Class<?> testClass)
    {
        Class<? extends Runner> runnerClass = JUnit4.class;
        RunnerDelegateTo annotation = testClass.getAnnotation(RunnerDelegateTo.class);

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
    public void run(final RunNotifier notifier)
    {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(classLoader);
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

            if (classLoader instanceof DisposableClassLoader)
            {
                try
                {
                    ((DisposableClassLoader) classLoader).dispose();
                }
                catch (Exception e)
                {
                    // Ignore
                }
            }
            classLoader = null;
        }
    }

}
