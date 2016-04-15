/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.integration.insert;

import static org.junit.Assert.assertEquals;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.runtime.core.api.client.LocalMuleClient;
import org.mule.module.db.integration.AbstractDbIntegrationTestCase;
import org.mule.module.db.integration.TestDbConfig;
import org.mule.module.db.integration.model.AbstractTestDatabase;

import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized;

public class InsertDefaultTestCase extends AbstractDbIntegrationTestCase
{

    public InsertDefaultTestCase(String dataSourceConfigResource, AbstractTestDatabase testDatabase)
    {
        super(dataSourceConfigResource, testDatabase);
    }

    @Parameterized.Parameters
    public static List<Object[]> parameters()
    {
        return TestDbConfig.getResources();
    }

    @Override
    protected String[] getFlowConfigurationResources()
    {
        return new String[] {"integration/insert/insert-default-config.xml"};
    }

    @Test
    public void testRequestResponse() throws Exception
    {
        final MuleEvent responseEvent = flowRunner("jdbcInsertUpdateCountRequestResponse").withPayload(TEST_MESSAGE).run();

        final MuleMessage response = responseEvent.getMessage();
        assertEquals(1, response.getPayload());
    }

    @Test
    public void testOneWay() throws Exception
    {
        flowRunner("jdbcInsertUpdateCountOneWay").withPayload(TEST_MESSAGE).asynchronously().run();

        LocalMuleClient client = muleContext.getClient();
        MuleMessage response = client.request("test://testOut", RECEIVE_TIMEOUT);

        assertEquals(1, response.getPayload());
    }
}
