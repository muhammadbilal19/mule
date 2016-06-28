/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test;

import org.mule.extension.validation.internal.ValidationExtension;
import org.mule.functional.junit4.ArtifactFunctionalTestCase;
import org.mule.functional.junit4.runners.ArtifactClassLoaderRunnerConfig;

@ArtifactClassLoaderRunnerConfig(extensions = ValidationExtension.class)
public abstract class AbstractIntegrationTestCase extends ArtifactFunctionalTestCase
{

}
