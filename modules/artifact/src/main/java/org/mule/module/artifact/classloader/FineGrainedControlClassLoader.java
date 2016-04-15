/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.artifact.classloader;

import static org.mule.module.artifact.classloader.ClassLoaderLookupStrategy.PARENT_FIRST;
import static org.mule.module.artifact.classloader.ClassLoaderLookupStrategy.PARENT_ONLY;
import static org.mule.runtime.core.util.Preconditions.checkArgument;

import java.net.URL;

/**
 * Defines a {@link ClassLoader} which enables the control of the class
 * loading lookup mode.
 * <p/>
 * By using a {@link ClassLoaderLookupPolicy} this classLoader can use
 * parent-first, parent-only or child-first classloading lookup mode per package.
 */
public class FineGrainedControlClassLoader extends GoodCitizenClassLoader implements ClassLoaderLookupPolicyProvider
{

    private final ClassLoaderLookupPolicy lookupPolicy;

    public FineGrainedControlClassLoader(URL[] urls, ClassLoader parent, ClassLoaderLookupPolicy lookupPolicy)
    {
        super(urls, parent);
        checkArgument(lookupPolicy != null, "Lookup policy cannot be null");
        this.lookupPolicy = lookupPolicy;
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
        Class<?> result = findLoadedClass(name);

        if (result != null)
        {
            return result;
        }

        final ClassLoaderLookupStrategy lookupStrategy = lookupPolicy.getLookupStrategy(name);

        if (lookupStrategy == PARENT_ONLY)
        {
            result = findParentClass(name);
        }
        else if (lookupStrategy == PARENT_FIRST)
        {
            try
            {
                result = findParentClass(name);
            }
            catch (ClassNotFoundException e)
            {
                result = findClass(name);
            }
        }
        else
        {
            try
            {
                result = findClass(name);
            }
            catch (ClassNotFoundException e)
            {
                result = findParentClass(name);
            }
        }

        if (resolve)
        {
            resolveClass(result);
        }

        return result;
    }

    protected Class<?> findParentClass(String name) throws ClassNotFoundException
    {
        if (getParent() != null)
        {
            return getParent().loadClass(name);
        }
        else
        {
            return findSystemClass(name);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException
    {
        return super.findClass(name);
    }

    @Override
    public ClassLoaderLookupPolicy getClassLoaderLookupPolicy()
    {
        return lookupPolicy;
    }
}
