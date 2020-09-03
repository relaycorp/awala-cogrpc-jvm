package tech.relaycorp.relaynet.cogrpc.client

import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Trust manager that accepts self-issued certificates in a private subnet.
 */
class PrivateSubnetTrustManager private constructor() : X509ExtendedTrustManager() {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?
    ) {
        throw clientValidationNotImplementedError
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?
    ) {
        throw clientValidationNotImplementedError
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw clientValidationNotImplementedError
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?
    ) {
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?
    ) {
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        val INSTANCE = PrivateSubnetTrustManager()
        private val clientValidationNotImplementedError =
            NotImplementedError("Client-side certificate validation is unsupported")

    }
}
