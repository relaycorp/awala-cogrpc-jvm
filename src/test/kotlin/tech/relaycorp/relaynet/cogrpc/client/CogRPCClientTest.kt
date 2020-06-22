package tech.relaycorp.relaynet.cogrpc.client

import io.grpc.BindableService
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.internal.testing.StreamRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.relaynet.CargoDeliveryRequest
import tech.relaycorp.relaynet.cogrpc.CargoDelivery
import tech.relaycorp.relaynet.cogrpc.CargoDeliveryAck
import tech.relaycorp.relaynet.cogrpc.test.MockCogRPCServerService
import tech.relaycorp.relaynet.cogrpc.test.NoopStreamObserver
import tech.relaycorp.relaynet.cogrpc.test.TestCogRPCServer
import tech.relaycorp.relaynet.cogrpc.test.Wait.waitFor
import tech.relaycorp.relaynet.cogrpc.test.Wait.waitForNotNull
import tech.relaycorp.relaynet.cogrpc.toAck
import tech.relaycorp.relaynet.cogrpc.toCargoDelivery
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class CogRPCClientTest {

    private var testServer: TestCogRPCServer? = null

    @AfterEach
    internal fun tearDown() {
        testServer?.stop()
        testServer = null
    }

    @Test
    internal fun `deliver cargo and receive ack`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)
            val cargo = buildRequest()

            // Server acks and completes instantaneously
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {
                override fun onNext(value: CargoDelivery) {
                    mockServerService.deliverCargoReceived?.onNext(cargo.toCargoDelivery().toAck())
                }

                override fun onCompleted() {
                    mockServerService.deliverCargoReceived?.onCompleted()
                }
            }

            val ackFlow = client.deliverCargo(listOf(cargo))

            assertEquals(
                cargo.localId,
                ackFlow.first()
            )

            client.close()
            testServer?.stop()
        }
    }

    @Test
    internal fun `deliver no cargo`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)
            val acks = client.deliverCargo(emptyList()).toList()

            assertTrue(acks.isEmpty())
            client.close()
            testServer?.stop()
        }
    }

    @Test
    internal fun `deliver cargo without ack`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)
            val cargo = buildRequest()

            // Server never acks, just completes
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {
                override fun onCompleted() {
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
    internal fun `deliver cargo throws exception if deadline is exceeded`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)
            val cargo = buildRequest()

            // Server acks and completes instantaneously
            mockServerService.deliverCargoReturned = object : NoopStreamObserver<CargoDelivery>() {
                override fun onNext(value: CargoDelivery) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    Thread.sleep(CogRPCClient.CALL_DEADLINE.inMilliseconds.toLong())
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
    internal fun `collect cargo, ack and complete`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)
            val ackRecorder = StreamRecorder.create<CargoDeliveryAck>()
            mockServerService.collectCargoReturned = ackRecorder

            // Client call
            val cca = buildMessageSerialized()
            val collectFlow = client.collectCargo { cca }

            // Server sends cargo
            val cargo = buildRequest()
            launch(Dispatchers.IO) {
                waitForNotNull { mockServerService.collectCargoReceived }
                    .onNext(cargo.toCargoDelivery())
            }

            assertNotNull(collectFlow.first())

            waitFor { ackRecorder.values.any() }
            assertEquals(
                cargo.localId,
                ackRecorder.values.first().id
            )

            mockServerService.collectCargoReceived?.onCompleted()
            assertTrue(ackRecorder.awaitCompletion(100, TimeUnit.MILLISECONDS))

            client.close()
            testServer?.stop()
        }
    }

    @Test
    internal fun `collect cargo throws exception if deadline is exceeded`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)
            val ackRecorder = StreamRecorder.create<CargoDeliveryAck>()
            mockServerService.collectCargoReturned = ackRecorder

            // Client call
            val cca = buildMessageSerialized()
            val collectFlow = client.collectCargo { cca }

            // Server sends cargo with a delay bigger than the deadline
            val cargo = buildRequest()
            launch(Dispatchers.IO) {
                @Suppress("BlockingMethodInNonBlockingContext")
                Thread.sleep(CogRPCClient.CALL_DEADLINE.inMilliseconds.toLong())
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
    internal fun `collect cargo throws exception if CCA is refused`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)

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
    internal fun `collect cargo throws CogRPCException if server returns unhandled status error`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)

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
    internal fun `collect cargo throws CogRPCException if server returns unhandled error`() {
        runBlocking {
            val mockServerService = MockCogRPCServerService()
            buildAndStartServer(mockServerService)
            val client = CogRPCClient.Builder.build(ADDRESS, false)

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

    private fun buildRequest() =
        CargoDeliveryRequest(
            UUID.randomUUID().toString()
        ) { buildMessageSerialized() }

    companion object {
        private const val HOST = "localhost"
        private const val PORT = 8080
        private const val ADDRESS = "http://$HOST:$PORT"
    }
}
