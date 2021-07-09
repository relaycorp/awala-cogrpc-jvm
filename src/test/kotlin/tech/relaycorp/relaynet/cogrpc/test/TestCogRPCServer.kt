package tech.relaycorp.relaynet.cogrpc.test

import io.grpc.BindableService
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TestCogRPCServer(
    private val host: String,
    private val port: Int,
    private val service: BindableService
) {
    private var server: Server? = null

    private val logger = Logger.getLogger(TestCogRPCServer::class.java.name)

    fun start() {
        server = NettyServerBuilder
            .forAddress(InetSocketAddress(host, port))
            .addService(service)
            .build()
            .start()
    }

    fun stop() {
        if (server?.shutdown()?.awaitTermination(3, TimeUnit.SECONDS) == false) {
            logger.info("Forcing test server to shut down")
            server?.shutdownNow()
            server?.awaitTermination()
        }
        server = null
    }
}
