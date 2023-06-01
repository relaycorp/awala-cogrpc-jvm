package tech.relaycorp.relaynet.cogrpc.client

import app.cash.turbine.test
import io.grpc.BindableService
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.internal.testing.StreamRecorder
import io.grpc.netty.NettyChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.relaynet.CargoDeliveryRequest
import tech.relaycorp.relaynet.cogrpc.CargoDelivery
import tech.relaycorp.relaynet.cogrpc.CargoDeliveryAck
import tech.relaycorp.relaynet.cogrpc.client.CogRPCClient.Companion.logger
import tech.relaycorp.relaynet.cogrpc.test.MockCogRPCServerService
import tech.relaycorp.relaynet.cogrpc.test.NoopStreamObserver
import tech.relaycorp.relaynet.cogrpc.test.TestCogRPCServer
import tech.relaycorp.relaynet.cogrpc.test.Wait.waitForNotNull
import tech.relaycorp.relaynet.cogrpc.toAck
import tech.relaycorp.relaynet.cogrpc.toCargoDelivery
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class CogRPCClientTest {

    private var testServer: TestCogRPCServer? = null

    private val channelBuilderProvider: ChannelBuilderProvider<NettyChannelBuilder> =
        { address, _ -> NettyChannelBuilder.forAddress(address) }

    @AfterEach
    internal fun tearDown() {
        testServer?.stop()
        testServer = null
    }

    @Test
    fun `deliver cargo and receive ack`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val cargo = buildDeliveryRequest()

            // Server acks and completes instantaneously
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {

                override fun onNext(value: CargoDelivery) {
                    mockServerService.deliverCargoReceived?.onNext(cargo.toCargoDelivery().toAck())
                }
                override fun onCompleted() {
                    mockServerService.deliverCargoReceived?.onCompleted()
                }
            }

            client.deliverCargo(listOf(cargo)).test {
                assertEquals(
                    cargo.localId,
                    awaitItem()
                )
                awaitComplete()
            }

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `deliver no cargo`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val acks = client.deliverCargo(emptyList()).toList()

            assertTrue(acks.isEmpty())
            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `deliver cargo without ack`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val cargo = buildDeliveryRequest()

            // Server never acks, just completes when it gets one cargo
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {
                override fun onNext(value: CargoDelivery) {
                    logger.info("mockServerService onNext")
                    mockServerService.deliverCargoReceived?.onCompleted()
                }
            }

            val acks = client.deliverCargo(listOf(cargo)).toList()
            assertTrue(acks.isEmpty())

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `deliver cargo with error`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val cargo = buildDeliveryRequest()

            // Server never acks, just throws error when cargo is received
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {
                override fun onNext(value: CargoDelivery) {
                    mockServerService.deliverCargoReceived?.onError(Exception())
                }
            }

            assertThrows<CogRPCClient.CogRPCException> {
                runBlocking {
                    client.deliverCargo(listOf(cargo)).collect()
                }
            }
            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `deliver cargo throws exception if deadline is exceeded`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val cargo = buildDeliveryRequest()

            // Server acks and completes instantaneously
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {
                override fun onNext(value: CargoDelivery) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    Thread.sleep(CogRPCClient.CALL_DEADLINE.inWholeMilliseconds)
                    mockServerService.deliverCargoReceived?.onNext(cargo.toCargoDelivery().toAck())
                }
            }

            val exception = assertThrows<CogRPCClient.CogRPCException> {
                runBlocking {
                    client.deliverCargo(listOf(cargo)).collect()
                }
            }
            assertEquals(StatusRuntimeException::class, exception.cause!!::class)
            assertEquals(
                Status.DEADLINE_EXCEEDED.code,
                (exception.cause as StatusRuntimeException).status.code
            )

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `collect cargo, ack and complete`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val ackRecorder = StreamRecorder.create<CargoDeliveryAck>()
            mockServerService.collectCargoReturned = ackRecorder

            // Server sends cargo when client makes call and ends the call when ACK is received
            var ackReceivedByServer: CargoDeliveryAck? = null
            val deliveryRequest = buildDeliveryRequest()
            client.collectCargo { buildMessageSerialized() }
                .onStart {
                    launch(Dispatchers.IO) {
                        mockServerService.addDeliveryToCollectionCall(
                            deliveryRequest.toCargoDelivery()
                        )
                    }
                }
                .collect {
                    launch(Dispatchers.IO) {
                        ackReceivedByServer = waitForNotNull { ackRecorder.values.firstOrNull() }
                        mockServerService.endCollectionCall()
                    }
                }

            assertEquals(
                deliveryRequest.localId,
                ackReceivedByServer?.id
            )

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `collect cargo throws exception if deadline is exceeded`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)
            val ackRecorder = StreamRecorder.create<CargoDeliveryAck>()
            mockServerService.collectCargoReturned = ackRecorder

            // Client call
            val cca = buildMessageSerialized()
            val collectFlow = client.collectCargo { cca }

            // Server sends cargo with a delay bigger than the deadline
            val cargo = buildDeliveryRequest()
            launch(Dispatchers.IO) {
                @Suppress("BlockingMethodInNonBlockingContext")
                Thread.sleep(CogRPCClient.CALL_DEADLINE.inWholeMilliseconds)
                try {
                    waitForNotNull { mockServerService.collectCargoReceived }
                        .onNext(cargo.toCargoDelivery())
                } catch (exception: StatusRuntimeException) {
                    // Call can already be closed, since the deadline is up
                }
            }

            val exception = assertThrows<CogRPCClient.CogRPCException> {
                runBlocking {
                    collectFlow.collect()
                }
            }
            assertEquals(StatusRuntimeException::class, exception.cause!!::class)
            assertEquals(
                Status.DEADLINE_EXCEEDED.code,
                (exception.cause as StatusRuntimeException).status.code
            )

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `collect cargo throws exception if CCA is refused`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)

            // Client call
            val cca = buildMessageSerialized()
            val collectFlow = client.collectCargo { cca }

            // Server refuses CCA
            launch(Dispatchers.IO) {
                waitForNotNull { mockServerService.collectCargoReceived }
                    .onError(StatusRuntimeException(Status.PERMISSION_DENIED))
            }

            assertThrows<CogRPCClient.CCARefusedException> {
                runBlocking {
                    collectFlow.collect()
                }
            }

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `collect cargo throws CogRPCException if server returns unhandled status error`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)

            // Client call
            val cca = buildMessageSerialized()
            val collectFlow = client.collectCargo { cca }

            // Server refuses CCA
            launch(Dispatchers.IO) {
                waitForNotNull { mockServerService.collectCargoReceived }
                    .onError(StatusRuntimeException(Status.INTERNAL))
            }

            assertThrows<CogRPCClient.CogRPCException> {
                runBlocking {
                    collectFlow.collect()
                }
            }

            client.close()
            testServer?.stop()
        }
    }

    @Test
    fun `collect cargo throws CogRPCException if server returns unhandled error`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, channelBuilderProvider, false)

            // Client call
            val cca = buildMessageSerialized()
            val collectFlow = client.collectCargo { cca }

            // Server refuses CCA
            launch(Dispatchers.IO) {
                waitForNotNull { mockServerService.collectCargoReceived }
                    .onError(Exception())
            }

            assertThrows<CogRPCClient.CogRPCException> {
                runBlocking {
                    collectFlow.collect()
                }
            }

            client.close()
            testServer?.stop()
        }
    }

    private fun buildAndStartServer(service: BindableService) {
        testServer = TestCogRPCServer(HOST, PORT, service).apply { start() }
    }

    private fun buildMessageSerialized() =
        "DATA".byteInputStream()

    private fun buildDeliveryRequest() =
        CargoDeliveryRequest(
            UUID.randomUUID().toString()
        ) { buildMessageSerialized() }

    companion object {
        private const val HOST = "localhost"
        private const val PORT = 8080
        private const val ADDRESS = "http://$HOST:$PORT"
    }
}
