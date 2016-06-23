/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

/**
 * Defines the multi-module folder name for an artifactId. It is useful when the convention of using the same artifactId
 * as folder name for the module. This will allow to have different names.
 */
public interface MavenMultiModuleArtifactMapping
{

    /**
     * @param artifactId
     * @return the relative folder path for the given artifactId.
     */
    String mapModuleFolderNameFor(String artifactId);
}
