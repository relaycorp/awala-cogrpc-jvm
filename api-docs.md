# Module cogrpc

This is the JVM implementation of the [Relaynet CogRPC binding](https://specs.relaynet.network/RS-008), meant to be used on Android 5+ and Java 8+ platforms.

## Install

This package can be retrieved from [JCenter](https://bintray.com/relaycorp/maven/tech.relaycorp.cogrpc). For example, using the Gradle Groovy DSL:

```
implementation 'tech.relaycorp:cogrpc:1.1.6'
```

## Usage

TODO

### gRPC Channel Support

To use this library on Android, use the OkHTTP channel integration in [`relaynet-cogrpc-jvm-okhttp`](https://github.com/relaycorp/relaynet-cogrpc-jvm-okhttp).

We don't have a ready-made integration for Netty at this point, but passing the following `ChannelBuilderProvider` to `CogRPCClient.Builder.build()` _should_ work:

```kotlin
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder

fun makeSSLContext(trustManager: PrivateSubnetTrustManager) =
    GrpcSslContexts.forClient().trustManager(trustManager).build()

val provider : ChannelBuilderProvider<NettyChannelBuilder> = { address, trustManager ->
    NettyChannelBuilder.forAddress(address)
        .let { if (trustManager) it.sslContext(makeSSLContext(trustManager)) else it }
}
```

# Package tech.relaycorp.cogrpc

This package contains the CogRPC implementation.
