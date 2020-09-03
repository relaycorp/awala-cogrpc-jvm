package tech.relaycorp.relaynet.cogrpc.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate

class PrivateSubnetTrustManagerTest {
    @Test
    fun `Client-side certificate validation methods should not be implemented`() {
        assertThrows(NotImplementedError::class.java) {
            PrivateSubnetTrustManager.INSTANCE.checkClientTrusted(CERT_CHAIN, null)
        }
    }

    @Test
    fun `Server-side certificate validation methods should do nothing`() {
        PrivateSubnetTrustManager.INSTANCE.checkServerTrusted(CERT_CHAIN, null)
    }

    @Test
    fun `getAcceptedIssuers should return an empty array`() {
        assertEquals(0, PrivateSubnetTrustManager.INSTANCE.acceptedIssuers.size)
    }

    companion object {
        private val CERT_CHAIN = emptyArray<X509Certificate>()
    }
}
