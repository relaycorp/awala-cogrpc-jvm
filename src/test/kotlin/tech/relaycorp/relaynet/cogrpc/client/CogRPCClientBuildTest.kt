package tech.relaycorp.relaynet.cogrpc.client

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.MalformedURLException

class CogRPCClientBuildTest {
    private lateinit var spiedChannelBuilder: OkHttpChannelBuilder
    private lateinit var spiedChannelBuilderProvider: (InetSocketAddress) -> OkHttpChannelBuilder

    @BeforeEach
    internal fun setUp() {
        spiedChannelBuilder = spy(OkHttpChannelBuilder.forAddress("127.0.0.1", 80))
        spiedChannelBuilderProvider = { spiedChannelBuilder }
    }

    @Test
    internal fun `invalid address throws exception`() {
        assertThrows<MalformedURLException> { CogRPCClient.Builder.build("invalid") }
    }

    @Test
    internal fun `TLS is required by default`() {
        val client = CogRPCClient.Builder.build("https://example.org")
        assertTrue(client.requireTls)
    }

    @Test
    internal fun `HTTPS URL defaults to port 443`() {
        val client = CogRPCClient.Builder.build("https://example.org")

        assertEquals("example.org:443", client.channel.authority())
    }

    @Test
    internal fun `HTTP URL with TLS required should throw an exception`() {
        val serverAddress = "http://example.com"
        val exception =
            assertThrows<CogRPCClient.CogRPCException> {
                CogRPCClient.Builder.build(
                    serverAddress
                )
            }

        assertEquals("Cannot connect to $serverAddress with TLS required", exception.message)
    }

    @Test
    internal fun `HTTP URL defaults to port 80`() {
        val client = CogRPCClient.Builder.build("http://example.org", false)

        assertEquals("example.org:80", client.channel.authority())
    }

    @Test
    internal fun `Channel should use TLS if URL is HTTPS`() {
        val spiedClient = spy(
            CogRPCClient("https://1.1.1.1", true, spiedChannelBuilderProvider)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder, never()).usePlaintext()
    }

    @Test
    internal fun `Channel should use TLS if TLS is not required but URL is HTTPS`() {
        val spiedClient = spy(
            CogRPCClient("https://1.1.1.1", false, spiedChannelBuilderProvider)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder).useTransportSecurity()
        verify(spiedChannelBuilder, never()).usePlaintext()
    }

    @Test
    internal fun `TLS server certificate should be validated if host is not private IP`() {
        val spiedClient = spy(
            CogRPCClient("https://1.1.1.1", true, spiedChannelBuilderProvider)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder, never()).usePlaintext()
        verify(spiedChannelBuilder, never()).sslSocketFactory(any())
    }

    @Test
    internal fun `TLS server certificate should not be validated if host is private IP`() {
        val spiedClient = spy(
            CogRPCClient("https://192.168.43.1", true, spiedChannelBuilderProvider)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder, never()).usePlaintext()
        verify(spiedChannelBuilder).sslSocketFactory(spiedClient.insecureSocketFactory)
    }

    @Test
    internal fun `TLS should not be used if URL is HTTP`() {
        val spiedClient = spy(
            CogRPCClient("http://192.168.43.1", false, spiedChannelBuilderProvider)
        )

        assertTrue(spiedClient.channel is ManagedChannel)
        verify(spiedChannelBuilder).usePlaintext()
        verify(spiedChannelBuilder, never()).sslSocketFactory(any())
    }
}
