package tech.relaycorp.relaynet.cogrpc.client

import io.grpc.ManagedChannelBuilder
import java.net.InetSocketAddress

typealias ChannelBuilderProvider<T> = (
    address: InetSocketAddress,
    privateSubsetTrustManager: PrivateSubnetTrustManager?
) -> ManagedChannelBuilder<T>
