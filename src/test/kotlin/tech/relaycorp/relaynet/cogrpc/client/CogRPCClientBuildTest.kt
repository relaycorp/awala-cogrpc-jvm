package tech.relaycorp.relaynet.cogrpc.client

import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.MalformedURLException

class CogRPCClientBuildTest {
    private lateinit var spiedChannelBuilder: NettyChannelBuilder
    private var privateSubsetTrustManager: PrivateSubnetTrustManager? = null
    private val channelBuilderProvider: ChannelBuilderProvider<NettyChannelBuilder> = { addr, tm ->
        privateSubsetTrustManager = tm
        spiedChannelBuilder = spy(NettyChannelBuilder.forAddress(addr))
        spiedChannelBuilder
    }

    @BeforeEach
    internal fun setUp() {
        privateSubsetTrustManager = null
    }

    @Test
    fun `invalid address throws exception`() {
        assertThrows<MalformedURLException> {
            CogRPCClient.Builder.build("invalid", channelBuilderProvider)
        }
    }

    @Test
    fun `TLS is required by default`() {
        val client = CogRPCClient.Builder.build("https://example.org", channelBuilderProvider)
        assertTrue(client.requireTls)
    }

    @Test
    fun `HTTPS URL defaults to port 443`() {
        val client = CogRPCClient.Builder.build("https://example.org", channelBuilderProvider)

        assertEquals("example.org:443", client.channel.authority())
    }

    @Test
    fun `HTTP URL with TLS required should throw an exception`() {
        val serverAddress = "http://example.com"
        val exception =
            assertThrows<CogRPCClient.CogRPCException> {
                CogRPCClient.Builder.build(serverAddress, channelBuilderProvider)
            }

        assertEquals("Cannot connect to $serverAddress with TLS required", exception.message)
    }

    @Test
    fun `HTTP URL defaults to port 80`() {
        val client =
            CogRPCClient.Builder.build("http://example.org", channelBuilderProvider, false)

        assertEquals("example.org:80", client.channel.authority())
    }

    @Test
    fun `Channel should use TLS if URL is HTTPS`() {
        val spiedClient = spy(
            CogRPCClient.Builder.build("https://1.1.1.1", channelBuilderProvider, true)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder, never()).usePlaintext()
    }

    @Test
    fun `Channel should use TLS if TLS is not required but URL is HTTPS`() {
        val spiedClient = spy(
            CogRPCClient.Builder.build("https://1.1.1.1", channelBuilderProvider, false)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder).useTransportSecurity()
        verify(spiedChannelBuilder, never()).usePlaintext()
    }

    @Test
    fun `TLS server certificate should be validated if host is not private IP`() {
        val spiedClient = spy(
            CogRPCClient.Builder.build("https://1.1.1.1", channelBuilderProvider, true)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder, never()).usePlaintext()
        assertNull(privateSubsetTrustManager)
    }

    @Test
    fun `TLS server certificate should not be validated if host is private IP`() {
        val hostName = "192.168.43.1"
        val spiedClient = spy(
            CogRPCClient.Builder.build("https://$hostName", channelBuilderProvider, true)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder, never()).usePlaintext()
        assertEquals(PrivateSubnetTrustManager.INSTANCE, privateSubsetTrustManager)
    }

    @Test
    fun `TLS should not be used if URL is HTTP`() {
        val spiedClient = spy(
            CogRPCClient.Builder.build("http://192.168.43.1", channelBuilderProvider, false)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder).usePlaintext()
        assertNull(privateSubsetTrustManager)
    }
}
