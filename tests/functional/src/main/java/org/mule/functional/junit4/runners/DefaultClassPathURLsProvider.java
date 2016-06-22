/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation that uses java system properties to get the classpath urls.
 */
public class DefaultClassPathURLsProvider implements ClassPathURLsProvider
{

    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * @return Gets the urls from the {@code java.class.path} and {@code sun.boot.class.path} system properties
     */
    @Override
    public Set<URL> getURLs()
    {
        final Set<URL> urls = new HashSet<>();
        addUrlsFromSystemProperty(urls, "java.class.path");
        addUrlsFromSystemProperty(urls, "sun.boot.class.path");

        if (logger.isDebugEnabled())
        {
            StringBuilder builder = new StringBuilder("ClassPath:");
            urls.stream().forEach(url -> builder.append(File.pathSeparator).append(url));
            logger.debug(builder.toString());
        }

        return urls;
    }

    protected void addUrlsFromSystemProperty(final Collection<URL> urls, final String propertyName)
    {
        for (String file : System.getProperty(propertyName).split(":"))
        {
            try
            {
                urls.add(new File(file).toURI().toURL());
            }
            catch (MalformedURLException e)
            {
                throw new IllegalArgumentException("Cannot create a URL from file path: " + file, e);
            }
        }
    }

}
