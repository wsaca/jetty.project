package org.eclipse.jetty.websocket.core;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpResponse;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClientUpgradeRequest;
import org.eclipse.jetty.websocket.core.extensions.WebSocketExtensionRegistry;
import org.eclipse.jetty.websocket.core.frames.Frame;
import org.eclipse.jetty.websocket.core.frames.OpCode;
import org.eclipse.jetty.websocket.core.io.BatchMode;
import org.eclipse.jetty.websocket.core.server.Negotiation;
import org.eclipse.jetty.websocket.core.server.RFC6455Handshaker;
import org.eclipse.jetty.websocket.core.server.WebSocketNegotiator;
import org.eclipse.jetty.websocket.core.server.WebSocketUpgradeHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebSocketTest
{
    private WebSocketServer server;
    private WebSocketClient client;

    @Before
    public void setup() throws Exception
    {
    }

    @Test
    public void testClientConnectionFailure() throws Exception
    {
        int port = 8080;

        TestFrameHandler serverHandler = new TestFrameHandler();
        TestFrameHandler clientHandler = new TestFrameHandler()
        {
            @Override
            public void onFrame(Frame frame, Callback callback) throws Exception
            {
                super.onFrame(frame, callback);
                if(frame.getOpCode() == OpCode.CLOSE)
                    getChannel().abort();
            }
        };

        server = new WebSocketServer(port, serverHandler);
        client = new WebSocketClient("localhost", port, clientHandler);

        server.start();
        client.start();

        client.sendText("hello world");
        client.close();

        Thread.sleep(100);

        Assert.assertFalse(client.isOpen());
    }


    static class WebSocketClient
    {
        private static Logger LOG = Log.getLogger(WebSocketClient.class);

        private URI baseWebSocketUri;
        private WebSocketCoreClient client;
        private TestFrameHandler handler;

        public WebSocketClient(String hostname, int port, TestFrameHandler frameHandler) throws Exception
        {
            this.baseWebSocketUri = new URI("ws://" + hostname + ":" + port);
            this.client = new WebSocketCoreClient();

            this.client.getPolicy().setMaxBinaryMessageSize(20 * 1024 * 1024);
            this.client.getPolicy().setMaxTextMessageSize(20 * 1024 * 1024);
            this.handler = frameHandler;
        }

        public void start() throws Exception
        {
            WebSocketCoreClientUpgradeRequest request = new WebSocketCoreClientUpgradeRequest(client, baseWebSocketUri.resolve("/test"))
            {
                @Override
                public FrameHandler getFrameHandler(WebSocketCoreClient coreClient, WebSocketPolicy upgradePolicy, HttpResponse response)
                {
                    return handler;
                }
            };
            request.setSubProtocols("test");
            this.client.start();
            Future<FrameHandler.Channel> response = client.connect(request);
            response.get(5, TimeUnit.SECONDS);
        }


        public void sendText(String line)
        {
            LOG.info("sending {}...", line);
            Frame frame = new Frame(OpCode.TEXT);
            frame.setFin(true);
            frame.setPayload(line);

            handler.getChannel().sendFrame(frame, Callback.NOOP, BatchMode.AUTO);
        }

        public void close()
        {
            handler.getChannel().close(CloseStatus.NORMAL, "WebSocketClient Initiated Close", Callback.NOOP);
        }

        public boolean isOpen()
        {
            return handler.getChannel().isOpen();
        }
    }


    static class TestFrameHandler implements FrameHandler
    {
        private static Logger LOG = Log.getLogger(TestFrameHandler.class);
        private Channel channel;

        public Channel getChannel()
        {
            return channel;
        }

        @Override
        public void onOpen(Channel channel) throws Exception
        {
            LOG.info("onOpen {}", channel);
            this.channel = channel;
        }

        @Override
        public void onFrame(Frame frame, Callback callback) throws Exception
        {
            LOG.info("onFrame: " + BufferUtil.toDetailString(frame.getPayload()));
            callback.succeeded();
        }

        @Override
        public void onClosed(CloseStatus closeStatus) throws Exception
        {
            LOG.info("onClosed {}",closeStatus);
        }

        @Override
        public void onError(Throwable cause) throws Exception
        {
            LOG.warn("onError",cause);
        }
    }



    static class WebSocketServer
    {
        private static Logger LOG = Log.getLogger(WebSocketServer.class);
        private final Server server;
        private final TestFrameHandler frameHandler;


        public void start() throws Exception
        {
            server.start();
        }

        public WebSocketServer(int port, TestFrameHandler frameHandler)
        {
            this.frameHandler = frameHandler;
            server = new Server();
            ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());

            connector.addBean(new WebSocketPolicy(WebSocketBehavior.SERVER));
            connector.addBean(new RFC6455Handshaker());
            connector.setPort(port);
            connector.setIdleTimeout(1000000);
            server.addConnector(connector);

            ContextHandler context = new ContextHandler("/");
            server.setHandler(context);
            WebSocketNegotiator negotiator =  new TestWebSocketNegotiator(new DecoratedObjectFactory(), new WebSocketExtensionRegistry(), connector.getByteBufferPool(), port, frameHandler);

            WebSocketUpgradeHandler handler = new WebSocketUpgradeHandler(negotiator);
            context.setHandler(handler);
            handler.setHandler(new AbstractHandler()
            {
                @Override
                public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
                {
                    response.setStatus(200);
                    response.setContentType("text/plain");
                    response.getOutputStream().println("Hello World!");
                    baseRequest.setHandled(true);
                }
            });
        }

        public void close()
        {
            frameHandler.getChannel().close(CloseStatus.NORMAL, "WebSocketServer Initiated Close", Callback.NOOP);
        }
    }


    static class TestWebSocketNegotiator implements WebSocketNegotiator
    {
        final DecoratedObjectFactory objectFactory;
        final WebSocketExtensionRegistry extensionRegistry;
        final ByteBufferPool bufferPool;
        private final int port;
        private final FrameHandler frameHandler;

        public TestWebSocketNegotiator(DecoratedObjectFactory objectFactory, WebSocketExtensionRegistry extensionRegistry, ByteBufferPool bufferPool, int port, FrameHandler frameHandler)
        {
            this.objectFactory = objectFactory;
            this.extensionRegistry = extensionRegistry;
            this.bufferPool = bufferPool;
            this.port = port;
            this.frameHandler = frameHandler;
        }

        @Override
        public FrameHandler negotiate(Negotiation negotiation) throws IOException
        {
            List<String> offeredSubprotocols = negotiation.getOfferedSubprotocols();
            if (!offeredSubprotocols.contains("test"))
                return null;
            negotiation.setSubprotocol("test");
            return frameHandler;
        }

        @Override
        public WebSocketPolicy getCandidatePolicy()
        {
            return null;
        }

        @Override
        public WebSocketExtensionRegistry getExtensionRegistry()
        {
            return extensionRegistry;
        }

        @Override
        public DecoratedObjectFactory getObjectFactory()
        {
            return objectFactory;
        }

        @Override
        public ByteBufferPool getByteBufferPool()
        {
            return bufferPool;
        }
    }
}