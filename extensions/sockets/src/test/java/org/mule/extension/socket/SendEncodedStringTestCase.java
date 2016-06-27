/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.socket;

import static org.junit.Assert.assertEquals;
import org.mule.runtime.api.message.MuleMessage;
import org.mule.runtime.core.util.IOUtils;

import java.io.InputStream;

import org.junit.Test;

public class SendEncodedStringTestCase extends SocketExtensionTestCase
{
    private static final String WEIRD_CHAR_MESSAGE = "This is a messag\u00ea with weird chars \u00f1.";
    private static final String ENCODING = "iso-8859-1";

    @Override
    protected String getConfigFile()
    {
        return "send-encoded-string-config.xml";
    }

    @Test
    public void sendString() throws Exception
    {

        flowRunner("tcp-send")
                .withPayload(WEIRD_CHAR_MESSAGE)
                .run();

        MuleMessage message = receiveConnection();
        byte[] byteArray = IOUtils.toByteArray((InputStream) message.getPayload());
        String encodedString = new String(byteArray, ENCODING);
        assertEquals(encodedString, WEIRD_CHAR_MESSAGE);
    }
}