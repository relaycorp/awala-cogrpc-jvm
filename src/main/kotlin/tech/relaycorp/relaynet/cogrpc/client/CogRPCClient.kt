package tech.relaycorp.relaynet.cogrpc.client

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import tech.relaycorp.relaynet.CargoDeliveryRequest
import tech.relaycorp.relaynet.cogrpc.AuthorizationMetadata
import tech.relaycorp.relaynet.cogrpc.CargoDelivery
import tech.relaycorp.relaynet.cogrpc.CargoDeliveryAck
import tech.relaycorp.relaynet.cogrpc.CargoRelayGrpc
import tech.relaycorp.relaynet.cogrpc.readBytesAndClose
import tech.relaycorp.relaynet.cogrpc.toCargoDelivery
import tech.relaycorp.relaynet.cogrpc.toCargoDeliveryAck
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

open class CogRPCClient
private constructor(
    serverAddress: String,
    val channelBuilderProvider: ChannelBuilderProvider<*>,
    val requireTls: Boolean
) {
    private val serverUrl = URL(serverAddress)

    init {
        if (requireTls && serverUrl.protocol != "https") {
            throw CogRPCException(message = "Cannot connect to $serverAddress with TLS required")
        }
    }

    private val address by lazy {
        val fallbackPort = if (serverUrl.protocol == "https") 443 else 80
        InetSocketAddress(
            serverUrl.host,
            serverUrl.port.let { if (it != -1) it else fallbackPort }
        )
    }

    internal val channel by lazy {
        val useTls = requireTls || serverUrl.protocol == "https"
        val isHostPrivateAddress = InetAddress.getByName(serverUrl.host).isSiteLocalAddress
        val privateSubnetTrustManager =
            if (useTls && isHostPrivateAddress) PrivateSubnetTrustManager.INSTANCE else null
        channelBuilderProvider
            .invoke(address, privateSubnetTrustManager)
            .run { if (useTls) useTransportSecurity() else usePlaintext() }
            .build()
    }

    fun deliverCargo(cargoes: Iterable<CargoDeliveryRequest>): Flow<String> {
        if (cargoes.none()) return emptyFlow()
        var deliveryObserver: StreamObserver<CargoDelivery>? = null
        val cargoesToAck = mutableListOf<String>()
        val ackChannel = BroadcastChannel<String>(1)
        val ackObserver = object : StreamObserver<CargoDeliveryAck> {
            override fun onNext(value: CargoDeliveryAck) {
                logger.info("deliverCargo ack ${value.id}")
                ackChannel.trySendBlocking(value.id)
                cargoesToAck.remove(value.id)
                if (cargoesToAck.isEmpty()) {
                    logger.info("deliverCargo complete")
                    deliveryObserver?.onCompleted()
                }
            }

            override fun onError(t: Throwable) {
                logger.log(Level.WARNING, "Ending deliverCargo due to ack error", t)
                println("\"Ending deliverCargo due to ack error\", ${t.stackTrace}")
                ackChannel.close(CogRPCException(t))
                deliveryObserver?.onCompleted()
            }

            override fun onCompleted() {
                logger.info("deliverCargo ack closed")
                ackChannel.close()
                if (cargoesToAck.any()) {
                    println(
                        "Ending deliverCargo but server did not acknowledge all cargo deliveries"
                    )

                    logger.info(
                        "Ending deliverCargo but server did not acknowledge all cargo deliveries"
                    )
                    deliveryObserver?.onCompleted()
                }
            }
        }

        val client = buildClient()
        deliveryObserver = client.deliverCargo(ackObserver)

        cargoes.forEach { delivery ->
            logger.info("deliverCargo next ${delivery.localId}")
            cargoesToAck.add(delivery.localId)
            deliveryObserver.onNext(delivery.toCargoDelivery())
        }

        return ackChannel.asFlow()
    }

    fun collectCargo(cca: (() -> InputStream)): Flow<InputStream> {
        val ackChannel = Channel<String>(Channel.UNLIMITED)
        return channelFlow {
            val collectObserver = object : StreamObserver<CargoDelivery> {
                override fun onNext(value: CargoDelivery) {
                    logger.info("collectCargo ${value.id}")
                    this@channelFlow.trySendBlocking(value.cargo.newInput())
                    ackChannel.trySend(value.id)
                }

                override fun onError(t: Throwable) {
                    logger.log(Level.WARNING, "collectCargo error", t)
                    this@channelFlow.close(
                        if (t.isStatusPermissionDenied()) {
                            CCARefusedException()
                        } else {
                            CogRPCException(t)
                        }
                    )
                }

                override fun onCompleted() {
                    logger.info("collectCargo complete")
                    this@channelFlow.close()
                    ackChannel.close()
                }
            }

            val client = buildAuthorizedClient(cca().readBytesAndClose())
            val ackObserver = client.collectCargo(collectObserver)
            ackChannel
                .receiveAsFlow()
                .onEach { ackObserver.onNext(it.toCargoDeliveryAck()) }
                .onCompletion { ackObserver.onCompleted() }
                .collect()
        }
    }

    fun close() {
        logger.info("Closing CogRPCClient")
        channel.shutdown().awaitTermination(CALL_DEADLINE.inWholeSeconds, TimeUnit.SECONDS)
    }

    private fun buildClient() =
        CargoRelayGrpc.newStub(channel)
            .withDeadlineAfter(CALL_DEADLINE.inWholeSeconds, TimeUnit.SECONDS)

    private fun buildAuthorizedClient(cca: ByteArray) =
        MetadataUtils.attachHeaders(
            buildClient(),
            AuthorizationMetadata.makeMetadata(cca)
        )

    private fun Throwable.isStatusPermissionDenied() =
        this is StatusRuntimeException && status == Status.PERMISSION_DENIED

    open class CogRPCException(throwable: Throwable? = null, message: String? = null) :
        Exception(message, throwable)

    class CCARefusedException : CogRPCException()

    object Builder {
        fun build(
            serverAddress: String,
            channelBuilderProvider: ChannelBuilderProvider<*>,
            requireTls: Boolean = true
        ) = CogRPCClient(serverAddress, channelBuilderProvider, requireTls)
    }

    companion object {
        internal val logger = Logger.getLogger(CogRPCClient::class.java.name)
        internal val CALL_DEADLINE = 5.seconds
    }
}
