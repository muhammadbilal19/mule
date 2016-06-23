/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.net.URL;

/**
 * A {@link ClassSpace} is a set of URLs that point to elements that defined the space (jars, folders, etc).
 * It also has support to be composed in hierarchy by chaining the spaces as parent/child.
 */
public final class DefaultClassSpace implements ClassSpace
{
    private final URL[] urls;
    private final URL[] resources;
    private final ClassSpace childSpace;

    public DefaultClassSpace(final URL[] urls, final URL[] resources, final ClassSpace childSpace)
    {
        this.urls = urls;
        this.resources = resources;
        this.childSpace = childSpace;
    }

    @Override
    public URL[] getURLs()
    {
        return urls;
    }

    @Override
    public URL[] getResources()
    {
        return resources;
    }

    @Override
    public ClassSpace getChild()
    {
        return childSpace;
    }
}
