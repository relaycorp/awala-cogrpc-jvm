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
package io.grpc.netty

import com.google.common.base.Preconditions
import io.grpc.Grpc
import io.grpc.InternalChannelz
import io.grpc.InternalChannelz.Tls
import io.grpc.SecurityLevel
import io.grpc.Status
import io.grpc.internal.GrpcAttributes
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import tech.relaycorp.relaynet.cogrpc.client.CogRPCClient
import java.util.concurrent.Executor
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.SSLSession

internal class AndroidClientTlsHandler(next: ChannelHandler?, sslContext: SslContext, authority: String?,
                                       executor: Executor?) : ProtocolNegotiators.ProtocolNegotiationHandler(next) {
    private val sslContext: SslContext
    private val host: String
    private val port: Int
    private val executor: Executor?
    override fun handlerAdded0(ctx: ChannelHandlerContext) {
        val sslEngine = sslContext.newEngine(ctx.alloc(), host, port)
        val sslParams = sslEngine.sslParameters
        try {
            sslParams.endpointIdentificationAlgorithm = "HTTPS"
        } catch (error: NoSuchMethodError) {
            logger.info("SSLParameters#setEndpointIdentificationAlgorithm not supported")
        }
        sslEngine.sslParameters = sslParams
        ctx.pipeline().addBefore(ctx.name(),  /* name= */null, if (executor != null) SslHandler(sslEngine, false, executor) else SslHandler(sslEngine, false))
    }

    @Throws(Exception::class)
    override fun userEventTriggered0(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is SslHandshakeCompletionEvent) {
            val handshakeEvent = evt
            if (handshakeEvent.isSuccess) {
                val handler = ctx.pipeline().get(SslHandler::class.java)
                if (sslContext.applicationProtocolNegotiator().protocols()
                        .contains(handler.applicationProtocol())) {
                    // Successfully negotiated the protocol.
                    ProtocolNegotiators.logSslEngineDetails(Level.FINER, ctx, "TLS negotiation succeeded.", null)
                    propagateTlsComplete(ctx, handler.engine().session)
                } else {
                    val ex: Exception = unavailableException("Failed ALPN negotiation: Unable to find compatible protocol")
                    ProtocolNegotiators.logSslEngineDetails(Level.FINE, ctx, "TLS negotiation failed.", ex)
                    ctx.fireExceptionCaught(ex)
                }
            } else {
                ctx.fireExceptionCaught(handshakeEvent.cause())
            }
        } else {
            super.userEventTriggered0(ctx, evt)
        }
    }

    private fun propagateTlsComplete(ctx: ChannelHandlerContext, session: SSLSession) {
        val security = InternalChannelz.Security(Tls(session))
        val existingPne = protocolNegotiationEvent
        val attrs = existingPne.attributes.toBuilder()
            .set(GrpcAttributes.ATTR_SECURITY_LEVEL, SecurityLevel.PRIVACY_AND_INTEGRITY)
            .set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session)
            .build()
        replaceProtocolNegotiationEvent(existingPne.withAttributes(attrs).withSecurity(security))
        fireProtocolNegotiationEvent(ctx)
    }

    companion object {
        private fun unavailableException(msg: String): RuntimeException {
            return Status.UNAVAILABLE.withDescription(msg).asRuntimeException()
        }

        private val logger = Logger.getLogger(CogRPCClient::class.java.name)
    }

    init {
        this.sslContext = Preconditions.checkNotNull(sslContext, "sslContext")
        val hostPort = ProtocolNegotiators.parseAuthority(authority)
        host = hostPort.host
        port = hostPort.port
        this.executor = executor
    }
}