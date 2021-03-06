/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.management.agents;

import static org.junit.Assert.assertEquals;
import org.mule.runtime.module.management.mbean.MBeanServerFactory;
import org.mule.functional.junit4.FunctionalTestCase;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.Test;

public class MuleContextRestartTestCase extends FunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "mule-context-restart-config-flow.xml";
    }

    @Test
    public void testContextRestart() throws Exception
    {
        muleContext.stop();
        checkCleanShutdown();

        // do it again ;)
        muleContext.start();
        muleContext.stop();
        checkCleanShutdown();
    }

    protected void checkCleanShutdown() throws MalformedObjectNameException
    {
        // check there are no leftover mbeans in mule domain
        final String contextId = muleContext.getConfiguration().getId();
        MBeanServer server = MBeanServerFactory.getOrCreateMBeanServer();
        ObjectName oname = ObjectName.getInstance("Mule." + contextId + ":*");
        Set mbeans = server.queryMBeans(oname, null);

        assertEquals("Not all MBeans unregistered on context stop.", 0, mbeans.size());
    }

}
