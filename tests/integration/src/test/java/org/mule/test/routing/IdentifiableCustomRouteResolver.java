/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.routing;

import org.mule.runtime.core.DefaultMuleMessage;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.core.api.MessagingException;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.api.processor.MessageProcessor;
import org.mule.runtime.core.routing.IdentifiableDynamicRouteResolver;

import java.util.ArrayList;
import java.util.List;

public class IdentifiableCustomRouteResolver implements IdentifiableDynamicRouteResolver
{

    private final String ID_EXPRESSION = "#[flowVars['id']]";

    static List<MessageProcessor> routes = new ArrayList<MessageProcessor>();

    @Override
    public List<MessageProcessor> resolveRoutes(MuleEvent event)
    {
        return routes;
    }

    @Override
    public String getRouteIdentifier(MuleEvent event) throws MessagingException
    {
        return event.getMuleContext().getExpressionManager().parse(ID_EXPRESSION, event);
    }

    public static class AddLetterMessageProcessor implements MessageProcessor
    {

        private String letter;

        public AddLetterMessageProcessor(String letter)
        {
            this.letter = letter;
        }

        @Override
        public MuleEvent process(MuleEvent event) throws MuleException
        {
            try
            {
                event.setMessage(new DefaultMuleMessage(letter, event.getMessage(), event.getMuleContext()));
                return event;
            }
            catch (Exception e)
            {
                throw new DefaultMuleException(e);
            }
        }

    }

}
