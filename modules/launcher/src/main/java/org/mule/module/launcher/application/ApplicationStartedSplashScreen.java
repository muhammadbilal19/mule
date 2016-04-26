/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.launcher.application;

import org.mule.api.config.MuleProperties;
import org.mule.module.launcher.descriptor.ApplicationDescriptor;
import org.mule.util.SplashScreen;

public class ApplicationStartedSplashScreen extends SplashScreen
{
    public void doBody(ApplicationDescriptor descriptor)
    {
        doBody(String.format("Started app '%s'", descriptor.getName()));
        if (Boolean.parseBoolean(System.getProperty(MuleProperties.MULE_RUNTIME_VERBOSE, "true")))
        {
            listAppProperties(descriptor);
            listPlugins(descriptor);
            listLibraries(descriptor);
            listOverrides(descriptor);
        }
    }

    private void listPlugins(ApplicationDescriptor descriptor)
    {
        listItems(descriptor.getPluginNames(), "Application plugins:");
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
