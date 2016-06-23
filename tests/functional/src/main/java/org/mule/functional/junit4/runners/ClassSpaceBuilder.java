/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Builder for a {@link ClassSpace}
 */
public final class ClassSpaceBuilder
{
    private List<URL[]> urls = new ArrayList<>();

    public static final ClassSpaceBuilder builder()
    {
        return new ClassSpaceBuilder();
    }

    public final ClassSpaceBuilder withSpace(URL[] urls)
    {
        this.urls.add(urls);
        return this;
    }

    public final ClassSpace build()
    {
        ClassSpace classSpace = null;
        ListIterator<URL[]> listIterator = urls.listIterator();
        while(listIterator.hasNext())
        {
            URL[] urls = listIterator.next();
            if (classSpace == null)
            {
                classSpace = new DefaultClassSpace(urls, null);
            }
            else
            {
                classSpace = new DefaultClassSpace(urls, classSpace);
            }
        }
        return classSpace;
    }
}
