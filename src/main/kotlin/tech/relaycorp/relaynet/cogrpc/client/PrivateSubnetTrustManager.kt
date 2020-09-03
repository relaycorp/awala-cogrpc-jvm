package tech.relaycorp.relaynet.cogrpc.client

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Trust manager that accepts self-issued certificates in a private subnet.
 */
class PrivateSubnetTrustManager private constructor() : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw NotImplementedError("Client-side certificate validation is unsupported")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        val INSTANCE = PrivateSubnetTrustManager()
    }
}
