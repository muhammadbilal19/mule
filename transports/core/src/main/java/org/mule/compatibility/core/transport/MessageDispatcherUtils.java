/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.compatibility.core.transport;


import org.mule.compatibility.core.api.transport.MessageDispatcher;
import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.api.lifecycle.Disposable;
import org.mule.runtime.core.api.lifecycle.Startable;
import org.mule.runtime.core.api.lifecycle.Stoppable;

public class MessageDispatcherUtils
{

    /**
     * Applies lifecycle to a MessageDispatcher based on the lifecycle state of its connector.
     */
    public static void applyLifecycle(MessageDispatcher dispatcher) throws MuleException
    {
        String phase = ((AbstractConnector)dispatcher.getConnector()).getLifecycleManager().getCurrentPhase();
        if(phase.equals(Startable.PHASE_NAME) && !dispatcher.getLifecycleState().isStarted())
        {
            if(!dispatcher.getLifecycleState().isInitialised())
            {
                dispatcher.initialise();
            }
            dispatcher.start();
        }
        else if(phase.equals(Stoppable.PHASE_NAME) && dispatcher.getLifecycleState().isStarted())
        {
            dispatcher.stop();
        }
        else if(Disposable.PHASE_NAME.equals(phase))
        {
            dispatcher.dispose();
        }
    }

}
