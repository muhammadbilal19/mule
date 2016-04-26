/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.launcher.descriptor;

import static org.mule.module.launcher.MuleFoldersUtil.LIB_FOLDER;
import static org.mule.module.launcher.MuleFoldersUtil.getDomainFolder;

import java.io.File;
import java.util.List;

/**
 * Represents the description of a domain.
 */
public class DomainDescriptor extends ArtifactDescriptor
{
    public List<String> getLibraries()
    {
        return getLibraries(new File(getDomainFolder(this.getName()), LIB_FOLDER));
    }
}
