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
public class MuleMavenMultiModuleArtifactMapping implements MavenMultiModuleArtifactMapping
{

    public static final Map<String, String> moduleMapping = new HashMap();

    static
    {
        moduleMapping.put("mule-core", "/core/");
        moduleMapping.put("mule-core-tests", "/core-tests/");

        moduleMapping.put("mule-module-file", "/extensions/file/");
        moduleMapping.put("mule-module-ftp", "/extensions/ftp/");
        moduleMapping.put("mule-module-http-ext", "/extensions/http/");
        moduleMapping.put("mule-module-sockets", "/extensions/sockets/");
        moduleMapping.put("mule-module-validation", "/extensions/validation/");

        moduleMapping.put("mule-module-artifact", "/modules/artifact/");
        moduleMapping.put("mule-module-container", "/modules/container/");
        moduleMapping.put("mule-module-extensions-spring-support", "/modules/extensions-spring-support/");
        moduleMapping.put("mule-module-extensions-support", "/modules/extensions-support/");
        moduleMapping.put("mule-module-file-extension-common", "/modules/file-extension-common/");
        moduleMapping.put("mule-module-spring-config", "/modules/spring-config/");
        moduleMapping.put("mule-module-launcher", "/modules/launcher/");
        moduleMapping.put("mule-module-reboot", "/modules/reboot/");
        moduleMapping.put("mule-module-tls", "/modules/tls/");
        moduleMapping.put("mule-module-jaas", "/modules/jaas/");
        moduleMapping.put("mule-module-schedulers", "/modules/schedulers/");
        moduleMapping.put("mule-module-cxf", "/modules/cxf/");
        moduleMapping.put("mule-module-oauth", "/modules/oauth/");
        moduleMapping.put("mule-module-pgp", "/modules/pgp/");
        moduleMapping.put("mule-module-scripting", "/modules/scripting/");
        moduleMapping.put("mule-module-jbossts", "/modules/jboss-transactions/");
        moduleMapping.put("mule-module-db", "/modules/db/");
        moduleMapping.put("mule-module-ws", "/modules/ws/");
        moduleMapping.put("mule-module-spring-extras", "/modules/spring-extras/");
        moduleMapping.put("mule-module-http", "/modules/http/");
        moduleMapping.put("mule-module-scripting-jruby", "/modules/scripting-jruby/");
        moduleMapping.put("mule-module-spring-security", "/modules/spring-security/");
        moduleMapping.put("mule-module-management", "/modules/management/");
        moduleMapping.put("mule-module-xml", "/modules/xml/");
        moduleMapping.put("mule-module-tomcat", "/modules/tomcat/");
        moduleMapping.put("mule-module-builders", "/modules/builders/");
        moduleMapping.put("mule-module-json", "/modules/json/");
        moduleMapping.put("mule-module-json", "/modules/json/");

        moduleMapping.put("mule-tests-functional", "/tests/functional/");
        moduleMapping.put("mule-tests-functional-plugins", "/tests/functional-plugins/");
        moduleMapping.put("mule-tests-infrastructure", "/tests/infrastructure/");
        moduleMapping.put("mule-tests-unit", "/tests/unit/");

        moduleMapping.put("mule-module-http-test", "/tests/http/");

        moduleMapping.put("mule-transport-sockets", "/transports/sockets/");
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
