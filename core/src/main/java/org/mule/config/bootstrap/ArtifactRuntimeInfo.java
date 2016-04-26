/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.config.bootstrap;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime info which was used to run this artifact.
 */
public class ArtifactRuntimeInfo
{
    private String name;
    protected boolean redeploymentEnabled = false;
    protected Set<String> loaderOverride = new HashSet<String>(0);
    private String domain;
    private Map<String, String> properties = new HashMap<String, String>(0);
    private File logConfigFile;
    private Set<String> plugins = new HashSet<String>(0);
    private List<URL> sharedPluginLibs = new ArrayList<>(0);
    private List<String> libraries = new ArrayList<String>(0);

    public ArtifactRuntimeInfo(String name, boolean redeploymentEnabled, Set<String> loaderOverride, String domain, Map<String, String> properties, File logConfigFile, Set<String> plugins, List<URL> sharedPluginLibs, List<String> libraries)
    {
        this(name, redeploymentEnabled, loaderOverride);
        this.domain = domain;
        this.properties = properties;
        this.logConfigFile = logConfigFile;
        this.plugins = plugins;
        this.sharedPluginLibs = sharedPluginLibs;
        this.libraries = libraries;
    }

    public ArtifactRuntimeInfo(String name, boolean redeploymentEnabled, Set<String> loaderOverride)
    {
        this.name = name;
        this.redeploymentEnabled = redeploymentEnabled;
        this.loaderOverride = loaderOverride;
    }

    public String getName()
    {
        return name;
    }

    public boolean isRedeploymentEnabled()
    {
        return redeploymentEnabled;
    }

    public Set<String> getLoaderOverride()
    {
        return loaderOverride;
    }

    public String getDomain()
    {
        return domain;
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

    public String getLogConfigFile()
    {
        return logConfigFile != null ? logConfigFile.getAbsolutePath() : null;
    }

    public Set<String> getPlugins()
    {
        return plugins;
    }

    public List<URL> getSharedPluginLibs()
    {
        return sharedPluginLibs;
    }

    public List<String> getLibraries()
    {
        return libraries;
    }
}
