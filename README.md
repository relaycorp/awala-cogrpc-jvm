# CogRPC Binding for the JVM

Kotlin JVM library implementing the [Relaynet CogRPC binding](https://specs.relaynet.link/RS-008).

## gRPC Channel Support

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

## Development

This project uses [Gradle](https://gradle.org/), so the only system dependency is a Java JDK. To install the project along with its dependencies, run `./gradlew build` (or `gradlew.bat build` on Windows).

Additional Gradle tasks include:

- `test`: Runs the unit test suite.
- `dokka`: Generates the API documentation.
- `publish`: Publishes the library to the local Maven repository on `build/repository`.
