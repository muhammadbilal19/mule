/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ftp.internal.ftp.command;

import static java.lang.String.format;
import org.mule.extension.ftp.api.FtpConnector;
import org.mule.extension.ftp.api.ftp.FtpFileSystem;
import org.mule.extension.ftp.internal.AbstractFtpCopyDelegate;
import org.mule.extension.ftp.internal.ftp.ClassicFtpFileAttributes;
import org.mule.extension.ftp.internal.ftp.connection.ClassicFtpFileSystem;
import org.mule.runtime.api.message.MuleEvent;
import org.mule.runtime.module.extension.file.api.FileAttributes;
import org.mule.runtime.module.extension.file.api.command.CopyCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

/**
 * A {@link ClassicFtpCommand} which implements the {@link CopyCommand} contract
 *
 * @since 4.0
 */
public final class FtpCopyCommand extends ClassicFtpCommand implements CopyCommand
{

    /**
     * {@inheritDoc}
     */
    public FtpCopyCommand(ClassicFtpFileSystem fileSystem, FTPClient client)
    {
        super(fileSystem, client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void copy(String sourcePath, String targetPath, boolean overwrite, boolean createParentDirectories, MuleEvent event)
    {
        copy(sourcePath, targetPath, overwrite, createParentDirectories, event, new RegularFtpCopyDelegate(this, fileSystem));
    }

    private class RegularFtpCopyDelegate extends AbstractFtpCopyDelegate
    {

        public RegularFtpCopyDelegate(FtpCommand command, FtpFileSystem fileSystem)
        {
            super(command, fileSystem);
        }

        @Override
        protected void copyDirectory(Path sourcePath, Path target, boolean overwrite, FtpFileSystem writerConnection, MuleEvent event)
        {
            changeWorkingDirectory(sourcePath);
            FTPFile[] files;
            try
            {
                files = client.listFiles();
            }
            catch (IOException e)
            {
                throw exception(format("Could not list contents of directory '%s' while trying to copy it to ", sourcePath, target), e);
            }

            for (FTPFile file : files)
            {
                if (isVirtualDirectory(file.getName()))
                {
                    continue;
                }

                FileAttributes fileAttributes = new ClassicFtpFileAttributes(sourcePath.resolve(file.getName()), file);

                if (fileAttributes.isDirectory())
                {
                    Path targetPath = target.resolve(fileAttributes.getName());
                    copyDirectory(Paths.get(fileAttributes.getPath()), targetPath, overwrite, writerConnection, event);
                }
                else
                {
                    copyFile(fileAttributes, target.resolve(fileAttributes.getName()), overwrite, writerConnection, event);
                }
            }
        }

        @Override
        protected void copyFile(FileAttributes source, Path target, boolean overwrite, FtpFileSystem writerConnection, MuleEvent event)
        {
            try
            {
                super.copyFile(source, target, overwrite, writerConnection, event);
            }
            finally
            {
                fileSystem.awaitCommandCompletion();
            }
        }
    }
}
