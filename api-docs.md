# Module cogrpc

This is the JVM implementation of the [Relaynet CogRPC binding](https://specs.relaynet.network/RS-008), meant to be used on Android 5+ and Java 8+ platforms.

## Install

This package can be retrieved from [JCenter](https://bintray.com/relaycorp/maven/tech.relaycorp.cogrpc). For example, using the Gradle Groovy DSL:

```
implementation 'tech.relaycorp:cogrpc:1.1.6'
```

## Usage

The first step is to select the gRPC channel to use. The rest of the documentation assumes the OkHTTP channel integration from [`relaynet-cogrpc-jvm-okhttp`](https://github.com/relaycorp/relaynet-cogrpc-jvm-okhttp).

Next, initialize the client, depending on the nature of your software:

- If it's a private gateway communicating with a courier, pass the private IP address of the courier in the private network. For example:
  ```kotlin
  val client = CogRPCClient(
      "https://192.168.43.1",
      OkHTTPChannelBuilderProvider.Companion::makeBuilder
  )
  ```
- If it's a courier communicating with a public gateway, pass the **resolved** CogRPC address to the client. For example:
  ```kotlin
  val resolvedCogrpcURL = resolveCRCAddress("frankfurt.relaycorp.cloud")
  val client = CogRPCClient(resolvedCogrpcURL, OkHTTPChannelBuilderProvider.Companion::makeBuilder)
  ```
  
  Per the CogRPC spec, you MUST use DNSSEC when resolving the domain name above. You can satisfy that requirement with DNS-over-HTTPS (e.g., [`tech.relaycorp.doh`](https://github.com/relaycorp/doh-jvm)).

### Deliver cargo

Simply wrap each cargo in a `tech.relaycorp.relaynet.CargoDeliveryRequest` and pass all the cargoes bound to the same public gateway to [tech.relaycorp.relaynet.cogrpc.client.CogRPCClient.deliverCargo].

### Collect cargo

Simply call [tech.relaycorp.relaynet.cogrpc.client.CogRPCClient.collectCargo] and store(-and-forward) the cargo received.

### Close connection

Make sure to close the connection when you're done by calling [tech.relaycorp.relaynet.cogrpc.client.CogRPCClient.close].

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
