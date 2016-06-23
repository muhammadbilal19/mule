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
public interface ClassSpace
{

    /**
     * @return list of URLs that would define this space.
     */
    URL[] getURLs();

    /**
     * @return list of URLs that define the resources of this space.
     */
    URL[] getResources();

    /**
     * @return a child (can be null) for building a hierarchical {@link ClassSpace}
     */
    ClassSpace getChild();
}
