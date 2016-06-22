/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Object representation of a maven artifact.
 */
public class MavenArtifact
{

    public static final String DOT_CHARACTER = ".";
    public static final String MAVEN_COMPILE_SCOPE = "compile";
    public static final String MAVEN_TEST_SCOPE = "test";
    public static final String MAVEN_PROVIDED_SCOPE = "provided";
    public static final String MAVEN_DEPENDENCIES_DELIMITER = ":";

    private String groupId;
    private String artifactId;
    private String type;
    private String version;
    private String scope;
    private Set<MavenArtifact> dependencies = Collections.EMPTY_SET;

    public MavenArtifact(String groupId, String artifactId, String type, String version, String scope)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.version = version;
        this.scope = scope;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getGroupIdAsPath()
    {
        return getGroupId().replace(DOT_CHARACTER, File.separator);
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getType()
    {
        return type;
    }

    public String getVersion()
    {
        return version;
    }

    public String getScope()
    {
        return scope;
    }

    public boolean isCompileScope()
    {
        return MAVEN_COMPILE_SCOPE.equals(scope);
    }

    public boolean isTestScope()
    {
        return MAVEN_TEST_SCOPE.equals(scope);
    }

    public boolean isProvidedScope()
    {
        return MAVEN_PROVIDED_SCOPE.equals(scope);
    }

    public Set<MavenArtifact> getDependencies()
    {
        return dependencies;
    }

    public void setDependencies(Set<MavenArtifact> dependencies)
    {
        this.dependencies = dependencies;
    }

    public void removeTestDependencies()
    {
        dependencies = dependencies.stream().filter(dep -> !dep.isTestScope()).collect(Collectors.toSet());
    }

    @Override
    public String toString()
    {
        return groupId + MAVEN_DEPENDENCIES_DELIMITER + artifactId + MAVEN_DEPENDENCIES_DELIMITER + type + MAVEN_DEPENDENCIES_DELIMITER + version + MAVEN_DEPENDENCIES_DELIMITER + scope;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        MavenArtifact that = (MavenArtifact) o;

        if (!groupId.equals(that.groupId))
        {
            return false;
        }
        if (!artifactId.equals(that.artifactId))
        {
            return false;
        }
        return type.equals(that.type);

    }

    @Override
    public int hashCode()
    {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }
}
