/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.util.HashMap;
import java.util.Map;

/**
 * Mule default implementation for getting modules based on artifactIds.
 * TODO: Find a better way to get this from the reactor-maven-plugin.
 */
public class MuleMavenMultiModuleArtifactMapping implements MavenMultiModuleAritfactMapping
{
    public static final Map<String, String> moduleMapping = new HashMap();

    static
    {
        moduleMapping.put("mule-tests-functional", "/tests/functional/");
        moduleMapping.put("mule-tests-unit", "/tests/unit/");
        moduleMapping.put("mule-tests-infrastructure", "/tests/infrastructure/");
        moduleMapping.put("mule-module-artifact", "/modules/artifact/");
        moduleMapping.put("mule-module-file", "/extensions/file/");
        moduleMapping.put("mule-module-ftp", "/extensions/ftp/");
        moduleMapping.put("mule-module-validation", "/extensions/validation/");
        moduleMapping.put("mule-core", "/core/");
        moduleMapping.put("mule-core-tests", "/core-tests/");
        moduleMapping.put("mule-module-extensions-spring-support", "/modules/extensions-spring-support/");
        moduleMapping.put("mule-module-tls", "/modules/tls/");
        moduleMapping.put("mule-module-extensions-support", "/modules/extensions-support/");
        moduleMapping.put("mule-module-spring-config", "/modules/spring-config/");
        moduleMapping.put("mule-module-file-extension-common", "/modules/file-extension-common/");
        moduleMapping.put("mule-transport-sockets", "/transports/sockets/");
        moduleMapping.put("mule-module-container", "/modules/container/");
        moduleMapping.put("mule-module-launcher", "/modules/launcher/");
        moduleMapping.put("mule-module-reboot", "/modules/reboot/");
    }

    @Override
    public String mapModuleFolderNameFor(String artifactId)
    {
        if (!moduleMapping.containsKey(artifactId))
        {
            throw new IllegalArgumentException("Cannot locate artifact as multi-module dependency: '" + artifactId + "', mapping used is: " + moduleMapping);
        }

        return moduleMapping.get(artifactId);
    }
}
