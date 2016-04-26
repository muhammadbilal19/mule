/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.launcher.application;

import org.mule.module.launcher.descriptor.ApplicationDescriptor;
import org.mule.util.SplashScreen;

import java.util.Set;

public class ApplicationStartedSplashScreen extends SplashScreen
{
    public void doBody(ApplicationDescriptor descriptor)
    {
        doBody(String.format("Started app '%s'", descriptor.getName()));
        listAppProperties(descriptor);
        listPlugins(descriptor);
        listLibraries(descriptor);
        listOverrides(descriptor);
    }

    private void listPlugins(ApplicationDescriptor descriptor)
    {
        Set<String> pluginNames = descriptor.getPluginNames();
        if (!pluginNames.isEmpty())
        {
            doBody("Application plugins:");
            for (String pluginName : pluginNames)
            {
                doBody(String.format(VALUE_FORMAT, pluginName));
            }
        }
    }

    private void listAppProperties(ApplicationDescriptor descriptor)
    {
        listItems(descriptor.getAppProperties(), "Application properties:");
    }

    private void listOverrides(ApplicationDescriptor descriptor)
    {
        listItems(descriptor.getLoaderOverride(), "Class loader overrides:");
    }

    private void listLibraries(ApplicationDescriptor descriptor)
    {
        listItems(descriptor.getLibraries(), "Application libraries:");
    }
}
