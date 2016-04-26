/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.launcher.domain;

import org.mule.api.config.MuleProperties;
import org.mule.module.launcher.descriptor.DomainDescriptor;
import org.mule.util.SplashScreen;

public class DomainStartedSplashScreen extends SplashScreen
{
    public void doBody(DomainDescriptor descriptor)
    {
        doBody(String.format("Started domain '%s'", descriptor.getName()));
        if (Boolean.parseBoolean(System.getProperty(MuleProperties.MULE_RUNTIME_VERBOSE, "true")))
        {
            listLibraries(descriptor);
            listOverrides(descriptor);
        }
    }

    private void listOverrides(DomainDescriptor descriptor)
    {
        listItems(descriptor.getLoaderOverride(), "Class loader overrides:");
    }

    private void listLibraries(DomainDescriptor descriptor)
    {
        listItems(descriptor.getLibraries(), "Domain libraries:");
    }
}
