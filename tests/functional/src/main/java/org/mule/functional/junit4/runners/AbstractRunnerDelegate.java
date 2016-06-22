/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4.runners;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Just a JUnit {@link Runner} that delegates to.
 */
public abstract class AbstractRunnerDelegate extends Runner
{

    protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    protected abstract Runner getDelegateRunner();

    @Override
    public Description getDescription()
    {
        return doGetDelegate().getDescription();
    }

    @Override
    public void run(RunNotifier notifier)
    {
        doGetDelegate().run(notifier);
    }

    private Runner doGetDelegate()
    {
        Runner delegate = getDelegateRunner();
        if (delegate == null)
        {
            throw new IllegalStateException("The runner has not defined a delegate");
        }
        return delegate;
    }
}
