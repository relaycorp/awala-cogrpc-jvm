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
import io.grpc.internal.ObjectPool
import io.grpc.netty.ProtocolNegotiators.GrpcNegotiationHandler
import io.grpc.netty.ProtocolNegotiators.WaitUntilActiveHandler
import io.netty.channel.ChannelHandler
import io.netty.handler.ssl.SslContext
import io.netty.util.AsciiString
import java.util.concurrent.Executor

internal class AndroidClientTlsProtocolNegotiator(
    sslContext: SslContext,
    private val executorPool: ObjectPool<out Executor>?
) : ProtocolNegotiator {

    private val sslContext: SslContext = Preconditions.checkNotNull(sslContext, "sslContext")
    private var executor: Executor? = null

    init {
        if (executorPool != null) {
            executor = executorPool.getObject()
        }
    }

    override fun scheme(): AsciiString = Utils.HTTPS

    override fun newHandler(grpcHandler: GrpcHttp2ConnectionHandler): ChannelHandler {
        val gnh: ChannelHandler = GrpcNegotiationHandler(grpcHandler)
        val cth: ChannelHandler =
            AndroidClientTlsHandler(gnh, sslContext, grpcHandler.authority, executor)
        return WaitUntilActiveHandler(cth)
    }

    override fun close() {
        if (executorPool != null && executor != null) {
            executorPool.returnObject(executor)
        }
    }
}
