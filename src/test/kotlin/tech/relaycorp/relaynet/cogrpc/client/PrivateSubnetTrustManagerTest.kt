package tech.relaycorp.relaynet.cogrpc.client

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate

class PrivateSubnetTrustManagerTest {
    @Test
    fun `Client-side certificate validation method should not be implemented`() {
        assertThrows(NotImplementedError::class.java) {
            PrivateSubnetTrustManager.INSTANCE.checkClientTrusted(CERT_CHAIN, null)
        }
    }

    @Test
    fun `Server-side certificate validation method should not require a chain`() {
        PrivateSubnetTrustManager.INSTANCE.checkServerTrusted(null, null)
    }

    @Test
    fun `Validity period of server-side certificate chain should be validated`() {
        PrivateSubnetTrustManager.INSTANCE.checkServerTrusted(CERT_CHAIN, null)

        verify(CERT).checkValidity()
    }

    @Test
    fun `getAcceptedIssuers should return an empty array`() {
        assertEquals(0, PrivateSubnetTrustManager.INSTANCE.acceptedIssuers.size)
    }

    companion object {
        private val CERT = mock<X509Certificate>()
        private val CERT_CHAIN = arrayOf(CERT)
    }
}
