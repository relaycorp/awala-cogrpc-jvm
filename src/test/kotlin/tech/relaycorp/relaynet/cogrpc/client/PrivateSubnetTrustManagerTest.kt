package tech.relaycorp.relaynet.cogrpc.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext

class PrivateSubnetTrustManagerTest {
    @Test
    fun `Client-side certificate validation methods should not be implemented`() {
        assertThrows(NotImplementedError::class.java) {
            PrivateSubnetTrustManager.INSTANCE.checkClientTrusted(CERT_CHAIN, null)
        }
        assertThrows(NotImplementedError::class.java) {
            PrivateSubnetTrustManager.INSTANCE.checkClientTrusted(
                CERT_CHAIN,
                null,
                SSL_CONTEXT.createSSLEngine()
            )
        }
        assertThrows(NotImplementedError::class.java) {
            val socket = Socket()
            socket.use {
                PrivateSubnetTrustManager.INSTANCE.checkClientTrusted(CERT_CHAIN, null, socket)
            }
        }
    }

    @Test
    fun `Server-side certificate validation methods should do nothing`() {
        PrivateSubnetTrustManager.INSTANCE.checkServerTrusted(CERT_CHAIN, null)

        PrivateSubnetTrustManager.INSTANCE.checkServerTrusted(
            CERT_CHAIN,
            null,
            SSL_CONTEXT.createSSLEngine()
        )

        val socket = Socket()
        socket.use {
            PrivateSubnetTrustManager.INSTANCE.checkServerTrusted(CERT_CHAIN, null, socket)
        }
    }

    @Test
    fun `getAcceptedIssuers should return an empty array`() {
        assertEquals(0, PrivateSubnetTrustManager.INSTANCE.acceptedIssuers.size)
    }

    companion object {
        private val CERT_CHAIN = emptyArray<X509Certificate>()
        private val SSL_CONTEXT = SSLContext.getDefault()
    }
}
