/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ftp;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.transformer.types.MimeTypes.JSON;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.extension.ftp.api.FtpFileAttributes;
import org.mule.module.extension.file.api.stream.AbstractFileInputStream;

import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;

import org.apache.commons.net.ftp.FTPFile;
import org.junit.Test;

public class FtpReadTestCase extends FtpConnectorTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "ftp-read-config.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        createHelloWorldFile();
    }

    @Test
    public void read() throws Exception
    {
        MuleMessage message = readHelloWorld().getMessage();

        assertThat(message.getDataType().getMimeType(), is(JSON));

        AbstractFileInputStream payload = (AbstractFileInputStream) message.getPayload();
        assertThat(payload.isLocked(), is(false));
        assertThat(getPayloadAsString(message), is(HELLO_WORLD));
    }

    @Test
    public void readWithForcedMimeType() throws Exception
    {
        MuleEvent event = flowRunner("readWithForcedMimeType").withFlowVariable("path", HELLO_PATH).run();
        assertThat(event.getMessage().getDataType().getMimeType(), equalTo("test/test"));
    }

    @Test
    public void readUnexisting() throws Exception
    {
        expectedException.expectCause(instanceOf(IllegalArgumentException.class));
        readPath("files/not-there.txt");
    }

    @Test
    public void readDirectory() throws Exception
    {
        expectedException.expectCause(instanceOf(IllegalArgumentException.class));
        readPath("files");
    }

    @Test
    public void readLockReleasedOnContentConsumed() throws Exception
    {
        MuleMessage message = readWithLock();
        getPayloadAsString(message);

        assertThat(isLocked(message), is(false));
    }

    @Test
    public void readLockReleasedOnEarlyClose() throws Exception
    {
        MuleMessage message = readWithLock();
        ((InputStream) message.getPayload()).close();

        assertThat(isLocked(message), is(false));
    }

    @Test
    public void getProperties() throws Exception
    {
        FtpFileAttributes fileAttributes = (FtpFileAttributes) readHelloWorld().getMessage().getAttributes();
        FTPFile file = ftpClient.get(HELLO_PATH);

        assertThat(fileAttributes.getName(), equalTo(file.getName()));
        assertThat(fileAttributes.getPath(), equalTo(Paths.get("/", BASE_DIR, HELLO_PATH).toString()));
        assertThat(fileAttributes.getSize(), is(file.getSize()));
        assertTime(fileAttributes.getTimestamp(), file.getTimestamp());
        assertThat(fileAttributes.isDirectory(), is(false));
        assertThat(fileAttributes.isSymbolicLink(), is(false));
        assertThat(fileAttributes.isRegularFile(), is(true));
    }

    private void assertTime(LocalDateTime dateTime, Calendar calendar)
    {
        assertThat(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), is(calendar.toInstant().toEpochMilli()));
    }

    private MuleMessage readWithLock() throws Exception
    {
        MuleMessage message = flowRunner("readWithLock").run().getMessage();

        assertThat(isLocked(message), is(true));
        return message;
    }
}
