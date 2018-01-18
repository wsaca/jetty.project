//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.client.impl;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jetty.client.HttpResponse;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.HandshakeRequest;
import org.eclipse.jetty.websocket.common.HandshakeResponse;
import org.eclipse.jetty.websocket.common.JettyWebSocketFrameHandler;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClientUpgradeRequest;

public class ClientUpgradeRequestImpl extends WebSocketCoreClientUpgradeRequest
{
    private final WebSocketClient containerContext;
    private final Object websocketPojo;
    private CompletableFuture<Session> futureSession = new CompletableFuture<>();

    public ClientUpgradeRequestImpl(WebSocketClient clientContainer, WebSocketCoreClient coreClient, UpgradeRequest request, URI requestURI, Object websocketPojo)
    {
        super(coreClient, requestURI);
        this.containerContext = clientContainer;
        this.websocketPojo = websocketPojo;

        if (request != null)
        {
            // Copy request details into actual request
            HttpFields fields = getHeaders();
            request.getHeadersMap().forEach((name, values) -> fields.put(name, values));
            method(request.getMethod());
            version(HttpVersion.fromString(request.getHttpVersion()));
        }
    }

    @Override
    public FrameHandler getFrameHandler(WebSocketCoreClient coreClient, HttpResponse response)
    {
        HandshakeRequest handshakeRequest = new DelegatedClientHandshakeRequest(this);
        HandshakeResponse handshakeResponse = new DelegatedClientHandshakeResponse(response);

        JettyWebSocketFrameHandler frameHandler = containerContext.newFrameHandler(websocketPojo, containerContext.getPolicy(),
                handshakeRequest, handshakeResponse, futureSession);

        return frameHandler;
    }

    public CompletableFuture<Session> getFutureSession()
    {
        return futureSession;
    }
}
