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

    public static final Map<String, String[]> moduleMapping = new HashMap();

    static
    {
        mapModule("mule-core", "/core/");
        mapModule("mule-core-tests", "/core-tests/");

        mapModule("mule-module-file", "/extensions/file/", "org.mule.extension.file.api.FileConnector");
        mapModule("mule-module-ftp", "/extensions/ftp/", "org.mule.extension.ftp.api.FtpConnector");
        mapModule("mule-module-http-ext", "/extensions/http/", "org.mule.extension.http.api.HttpConnector");
        mapModule("mule-module-sockets", "/extensions/sockets/", "org.mule.module.socket.api.SocketsExtension");
        mapModule("mule-module-validation", "/extensions/validation/", "org.mule.extension.validation.internal.ValidationExtension");

        mapModule("mule-module-artifact", "/modules/artifact/");
        mapModule("mule-module-container", "/modules/container/");
        mapModule("mule-module-extensions-spring-support", "/modules/extensions-spring-support/");
        mapModule("mule-module-extensions-support", "/modules/extensions-support/");
        mapModule("mule-module-file-extension-common", "/modules/file-extension-common/");
        mapModule("mule-module-spring-config", "/modules/spring-config/");
        mapModule("mule-module-launcher", "/modules/launcher/");
        mapModule("mule-module-reboot", "/modules/reboot/");
        mapModule("mule-module-tls", "/modules/tls/");
        mapModule("mule-module-jaas", "/modules/jaas/");
        mapModule("mule-module-schedulers", "/modules/schedulers/");
        mapModule("mule-module-cxf", "/modules/cxf/");
        mapModule("mule-module-oauth", "/modules/oauth/");
        mapModule("mule-module-pgp", "/modules/pgp/");
        mapModule("mule-module-scripting", "/modules/scripting/");
        mapModule("mule-module-jbossts", "/modules/jboss-transactions/");
        mapModule("mule-module-db", "/modules/db/");
        mapModule("mule-module-ws", "/modules/ws/");
        mapModule("mule-module-spring-extras", "/modules/spring-extras/");
        mapModule("mule-module-http", "/modules/http/");
        mapModule("mule-module-scripting-jruby", "/modules/scripting-jruby/");
        mapModule("mule-module-spring-security", "/modules/spring-security/");
        mapModule("mule-module-management", "/modules/management/");
        mapModule("mule-module-xml", "/modules/xml/");
        mapModule("mule-module-tomcat", "/modules/tomcat/");
        mapModule("mule-module-builders", "/modules/builders/");
        mapModule("mule-module-json", "/modules/json/");
        mapModule("mule-module-json", "/modules/json/");

        mapModule("mule-tests-functional", "/tests/functional/");
        mapModule("mule-tests-functional-plugins", "/tests/functional-plugins/");
        mapModule("mule-tests-infrastructure", "/tests/infrastructure/");
        mapModule("mule-tests-unit", "/tests/unit/");
        mapModule("mule-tests-integration", "/tests/integration/");


        mapModule("mule-module-http-test", "/tests/http/");

        mapModule("mule-transport-sockets", "/transports/sockets/");

        // These mappings are required when a pom project is used to compose mule-api, mule-extensions-api and mule
        mapModule("mule-extensions-api", "mule-extensions-api/mule-extensions-api/");
        mapModule("mule-extensions-api-persistence", "mule-extensions-api/mule-extensions-api-persistence/");
        mapModule("mule-api", "mule-api/");
    }

    private static void mapModule(String artifactId, String... mappings)
    {
        moduleMapping.put(artifactId, mappings);
    }

    @Override
    public String mapModuleFolderNameFor(String artifactId)
    {
        return getArtifactMapping(artifactId)[0];
    }

    @Override
    public String getMavenArtifactIdFor(Class<?> extensionClass)
    {
        for(String key : moduleMapping.keySet())
        {
            String[] values = moduleMapping.get(key);
            if (values.length >= 2 && extensionClass.getName().equals(values[1]))
            {
                return key;
            }
        }
        throw new IllegalArgumentException("Couldn't find an artifactId mapping for extension class: '" + extensionClass.getName() + "'");
    }

    public String[] getArtifactMapping(String artifactId)
    {
        if (!moduleMapping.containsKey(artifactId))
        {
            throw new IllegalArgumentException("Cannot locate artifact as multi-module dependency: '" + artifactId + "'");
        }

        return moduleMapping.get(artifactId);
    }

}
