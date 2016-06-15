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
 * This is just a temporary hack for accessing folders in the multi-module Mule project per artifactId.
 * TODO: find a way to get this from maven mule parent pom
 */
public class MavenModuleMapping
{
    public static final Map<String, String> moduleMapping = new HashMap();

    static
    {
        // Test artifacts
        moduleMapping.put("mule-tests-functional", "/tests/functional/");
        moduleMapping.put("mule-tests-unit", "/tests/unit/");
        moduleMapping.put("mule-tests-infrastructure", "/tests/infrastructure/");

        // Bootstrap artifacts
        moduleMapping.put("mule-module-artifact", "/modules/artifact/");

        // Modules
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
}
