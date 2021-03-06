/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.parsers;

import org.mule.runtime.config.spring.parsers.beans.ChildBean;
import org.mule.runtime.config.spring.parsers.beans.OrphanBean;

import org.junit.Test;

public class NamedDynamicTestCase extends AbstractNamespaceTestCase
{
    @Override
    protected String getConfigFile()
    {
        return "org/mule/config/spring/parsers/named-dynamic-test.xml";
    }

    @Test
    public void testDynamicNamed1()
    {
        OrphanBean orphan1 = (OrphanBean) assertBeanExists("orphan1", OrphanBean.class);
        assertBeanPopulated(orphan1, "orphan1");
        ChildBean child1 = (ChildBean) assertContentExists(orphan1.getChild(), ChildBean.class);
        assertBeanPopulated(child1, "child1");
    }
}
