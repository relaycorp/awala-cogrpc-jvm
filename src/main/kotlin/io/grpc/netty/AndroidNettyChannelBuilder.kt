package io.grpc.netty

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.net.SocketAddress
import javax.net.ssl.SSLException

/*
    Custom NettyChannelBuilder` to ensure we're using our custom `AndroidClientTlsHandler`.
    That custom handler includes a fix for Android API < 24, for the missing method
    `SSLParameters#setEndpointIdentificationAlgorithm`.
 */
class AndroidNettyChannelBuilder
private constructor(
    private val address: SocketAddress
) {

    private var useTls = false
    private var trustAllCertificates = false

    companion object {
        fun forAddress(address: SocketAddress) = AndroidNettyChannelBuilder(address)
    }

    fun useTls(useTls: Boolean): AndroidNettyChannelBuilder {
        this.useTls = useTls
        return this
    }

    fun trustAllCertificates(trustAllCertificates: Boolean): AndroidNettyChannelBuilder {
        this.trustAllCertificates = trustAllCertificates
        return this
    }

    fun build() =
        NettyChannelBuilder
            .forAddress(address)
            .apply {
                if (!useTls) {
                    usePlaintext()
                    return@apply
                }

                val localSslContext =
                    if (trustAllCertificates) insecureTlsContext else regularTlsContext
                sslContext(localSslContext)

                protocolNegotiatorFactory(
                    AndroidProtocolNegotiatorFactory(localSslContext, null)
                )
                useTransportSecurity()
            }
            .build()

    private val insecureTlsContext by lazy {
        GrpcSslContexts.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
    }

    private val regularTlsContext by lazy {
        try {
            GrpcSslContexts.forClient().build()
        } catch (ex: SSLException) {
            throw RuntimeException(ex)
        }
    }
}
