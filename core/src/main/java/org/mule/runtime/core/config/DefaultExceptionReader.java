/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.config;

import org.mule.runtime.core.api.config.ExceptionReader;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the default exception reader used if there is no specific one registered
 * for the current exception.
 */
public final class DefaultExceptionReader implements ExceptionReader
{

    private Map<?, ?> info = new HashMap<Object, Object>();

    public String getMessage(Throwable t)
    {
        return t.getMessage();
    }

    public Throwable getCause(Throwable t)
    {
        return t.getCause();
    }

    public Class<?> getExceptionType()
    {
        return Throwable.class;
    }

    /**
     * Returns a map of the non-stanard information stored on the exception
     * 
     * @return a map of the non-stanard information stored on the exception
     */
    public Map<?, ?> getInfo(Throwable t)
    {
        return info;
    }
}
