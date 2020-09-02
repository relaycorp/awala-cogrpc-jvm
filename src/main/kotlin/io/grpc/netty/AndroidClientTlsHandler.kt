/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.grpc.netty;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.InternalChannelz;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import io.grpc.internal.GrpcAttributes;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import tech.relaycorp.relaynet.cogrpc.client.CogRPCClient;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.netty.ProtocolNegotiators.logSslEngineDetails;
import static io.grpc.netty.ProtocolNegotiators.parseAuthority;

class AndroidClientTlsHandler extends ProtocolNegotiators.ProtocolNegotiationHandler {

    private final SslContext sslContext;
    private final String host;
    private final int port;
    private Executor executor;

    AndroidClientTlsHandler(ChannelHandler next, SslContext sslContext, String authority,
                            Executor executor) {
        super(next);
        this.sslContext = checkNotNull(sslContext, "sslContext");
        ProtocolNegotiators.HostPort hostPort = parseAuthority(authority);
        this.host = hostPort.host;
        this.port = hostPort.port;
        this.executor = executor;
    }

    @Override
    protected void handlerAdded0(ChannelHandlerContext ctx) {
        SSLEngine sslEngine = sslContext.newEngine(ctx.alloc(), host, port);
        SSLParameters sslParams = sslEngine.getSSLParameters();

        try {
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
        } catch (NoSuchMethodError error) {
            logger.info("SSLParameters#setEndpointIdentificationAlgorithm not supported");
        }

        sslEngine.setSSLParameters(sslParams);
        ctx.pipeline().addBefore(ctx.name(), /* name= */ null, this.executor != null
                ? new SslHandler(sslEngine, false, this.executor)
                : new SslHandler(sslEngine, false));
    }

    @Override
    protected void userEventTriggered0(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent handshakeEvent = (SslHandshakeCompletionEvent) evt;
            if (handshakeEvent.isSuccess()) {
                SslHandler handler = ctx.pipeline().get(SslHandler.class);
                if (sslContext.applicationProtocolNegotiator().protocols()
                        .contains(handler.applicationProtocol())) {
                    // Successfully negotiated the protocol.
                    logSslEngineDetails(Level.FINER, ctx, "TLS negotiation succeeded.", null);
                    propagateTlsComplete(ctx, handler.engine().getSession());
                } else {
                    Exception ex =
                            unavailableException("Failed ALPN negotiation: Unable to find compatible protocol");
                    logSslEngineDetails(Level.FINE, ctx, "TLS negotiation failed.", ex);
                    ctx.fireExceptionCaught(ex);
                }
            } else {
                ctx.fireExceptionCaught(handshakeEvent.cause());
            }
        } else {
            super.userEventTriggered0(ctx, evt);
        }
    }

    private void propagateTlsComplete(ChannelHandlerContext ctx, SSLSession session) {
        InternalChannelz.Security security = new InternalChannelz.Security(new InternalChannelz.Tls(session));
        ProtocolNegotiationEvent existingPne = getProtocolNegotiationEvent();
        Attributes attrs = existingPne.getAttributes().toBuilder()
                .set(GrpcAttributes.ATTR_SECURITY_LEVEL, SecurityLevel.PRIVACY_AND_INTEGRITY)
                .set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session)
                .build();
        replaceProtocolNegotiationEvent(existingPne.withAttributes(attrs).withSecurity(security));
        fireProtocolNegotiationEvent(ctx);
    }

    private static RuntimeException unavailableException(String msg) {
        return Status.UNAVAILABLE.withDescription(msg).asRuntimeException();
    }

    private static Logger logger = Logger.getLogger(CogRPCClient.class.getName());
}
