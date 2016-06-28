/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.net.URL;
import java.util.List;
import java.util.Set;

public class ArtifactUrlClassification
{

    private final Set<URL> container;
    private final List<Set<URL>> plugins;
    private final Set<URL> application;

    public ArtifactUrlClassification(Set<URL> container, List<Set<URL>> plugins, Set<URL> application)
    {
        this.container = container;
        this.plugins = plugins;
        this.application = application;
    }

    public Set<URL> getContainer()
    {
        return container;
    }

    public List<Set<URL>> getPlugins()
    {
        return plugins;
    }

    public Set<URL> getApplication()
    {
        return application;
    }
}
