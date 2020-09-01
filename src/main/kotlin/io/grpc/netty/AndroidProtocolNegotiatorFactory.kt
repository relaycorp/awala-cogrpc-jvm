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

import io.grpc.internal.ObjectPool
import io.netty.handler.ssl.SslContext
import java.util.concurrent.Executor

internal class AndroidProtocolNegotiatorFactory(
    private val sslContext: SslContext,
    private val executorPool: ObjectPool<out Executor?>?
) : NettyChannelBuilder.ProtocolNegotiatorFactory {
    override fun buildProtocolNegotiator(): ProtocolNegotiator =
        AndroidClientTlsProtocolNegotiator(sslContext, executorPool)
}
