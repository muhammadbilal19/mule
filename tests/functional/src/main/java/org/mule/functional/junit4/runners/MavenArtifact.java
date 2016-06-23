/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.io.File;

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

    @Override
    public String toString()
    {
        return groupId + MAVEN_DEPENDENCIES_DELIMITER + artifactId + MAVEN_DEPENDENCIES_DELIMITER + type + MAVEN_DEPENDENCIES_DELIMITER + (version != null ? version : "") + MAVEN_DEPENDENCIES_DELIMITER + (scope != null ? scope : "");
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
        if (!type.equals(that.type))
        {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null)
        {
            return false;
        }
        return scope.equals(that.scope);

    }

    @Override
    public int hashCode()
    {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + scope.hashCode();
        return result;
    }

    public static MavenArtifactBuilder builder()
    {
        return new MavenArtifactBuilder();
    }

    /**
     * Builder for {@link MavenArtifact}
     */
    public static class MavenArtifactBuilder
    {

        private String groupId;
        private String artifactId;
        private String type;
        private String version;
        private String scope;

        public MavenArtifactBuilder withGroupId(String groupId)
        {
            this.groupId = groupId;
            return this;
        }

        public MavenArtifactBuilder withArtifactId(String artifactId)
        {
            this.artifactId = artifactId;
            return this;
        }

        public MavenArtifactBuilder withType(String type)
        {
            this.type = type;
            return this;
        }

        public MavenArtifactBuilder withVersion(String version)
        {
            this.version = version;
            return this;
        }

        public MavenArtifactBuilder withScope(String scope)
        {
            this.scope = scope;
            return this;
        }

        public MavenArtifact build()
        {
            return new MavenArtifact(groupId, artifactId, type, version, scope);
        }
    }
}
