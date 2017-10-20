//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.core.autobahn;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.AbstractFrameHandler;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.frames.BinaryFrame;
import org.eclipse.jetty.websocket.core.frames.TextFrame;
import org.eclipse.jetty.websocket.core.io.BatchMode;

class AutobahnFrameHandler extends AbstractFrameHandler
{
    private static Logger LOG = Log.getLogger(AutobahnFrameHandler.class);

    @Override
    public void onOpen()
    {        
        LOG.info("onOpen {}", getWebSocketChannel());
        super.onOpen();
    }

    @Override
    public void onText(Utf8StringBuilder utf8, Callback callback, boolean fin)
    {
        LOG.info("onText {} {} {}", utf8.length(),fin, getWebSocketChannel());
        if (fin)
        {
            getWebSocketChannel().outgoingFrame(new TextFrame().setPayload(utf8.toString()),
                    callback,
                    BatchMode.OFF);
        }
        else
        {
            callback.succeeded();
        }
    }

    @Override
    public void onBinary(ByteBuffer payload, Callback callback, boolean fin)
    {        
        LOG.info("onBinary {} {} {}", payload==null?0:payload.remaining(),fin,getWebSocketChannel());
        if (fin)
        {       
            BinaryFrame echo = new BinaryFrame();
            if (payload!=null)
                echo.setPayload(payload);
            getWebSocketChannel().outgoingFrame(echo,callback,BatchMode.OFF);
        }
        else
        {
            callback.succeeded();
        }
    }
    
    @Override
    public void onClosed(CloseStatus closeStatus)
    {
        LOG.info("onClosed {}",closeStatus);  
    }

    @Override
    public void onError(Throwable cause)
    {
        LOG.warn("onError",cause);
    }
}
