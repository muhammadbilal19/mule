/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.manager;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mule.runtime.core.api.registry.ServiceRegistry;
import org.mule.extension.api.ExtensionManager;
import org.mule.extension.api.introspection.ExtensionDiscoverer;
import org.mule.extension.api.introspection.ExtensionFactory;
import org.mule.extension.api.introspection.RuntimeExtensionModel;
import org.mule.extension.api.introspection.declaration.DescribingContext;
import org.mule.extension.api.introspection.declaration.fluent.ExtensionDeclarer;
import org.mule.extension.api.introspection.declaration.spi.Describer;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class ExtensionDiscovererTestCase extends AbstractMuleTestCase
{

    @Mock
    private ExtensionManager extensionManager;

    @Mock
    private ExtensionFactory extensionFactory;

    @Mock
    private ServiceRegistry serviceRegistry;

    @Mock
    private ExtensionDeclarer extensionDeclarer;

    @Mock
    private Describer describer;

    @Mock
    private RuntimeExtensionModel extensionModel;

    private ExtensionDiscoverer discoverer;

    @Before
    public void setUp()
    {
        discoverer = new DefaultExtensionDiscoverer(extensionFactory, serviceRegistry);
    }

    @Test
    public void scan() throws Exception
    {
        when(serviceRegistry.lookupProviders(Describer.class, getClass().getClassLoader())).thenReturn(Arrays.asList(describer));
        when(describer.describe(any(DescribingContext.class))).thenReturn(extensionDeclarer);
        when(extensionFactory.createFrom(extensionDeclarer)).thenReturn(extensionModel);

        List<RuntimeExtensionModel> extensionModels = discoverer.discover(getClass().getClassLoader());
        assertThat(extensionModels, hasSize(1));

        assertThat(extensionModels.get(0), is(sameInstance(extensionModel)));

        verify(serviceRegistry).lookupProviders(Describer.class, getClass().getClassLoader());
        verify(describer).describe(any(DescribingContext.class));
        verify(extensionFactory).createFrom(extensionDeclarer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullClassLoader()
    {
        discoverer.discover(null);
    }
}

