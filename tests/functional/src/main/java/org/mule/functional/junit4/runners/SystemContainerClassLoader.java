/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * Classloader that delegates the resolution to the system classloader only if the class belogns to the exported
 * packages listed.
 */
public class SystemContainerClassLoader extends URLClassLoader
{
    private final Set<String> exportedPackages;

    public SystemContainerClassLoader(URL[] urls, Set<String> exportedPackages)
    {
        super(urls, null);
        this.exportedPackages = exportedPackages;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException
    {
        Class<?> result = findLoadedClass(name);

        if (result != null)
        {
            return result;
        }

        if (isExportedPackage(name))
        {
            return getSystemClassLoader().loadClass(name);
        }
        else
        {
            throw new ClassNotFoundException(name);
        }
    }

    private boolean isExportedPackage(String name)
    {
        for (String aPackage : exportedPackages)
        {
            if (name.startsWith(aPackage))
            {
                return true;
            }
        }
        return false;
    }
}
