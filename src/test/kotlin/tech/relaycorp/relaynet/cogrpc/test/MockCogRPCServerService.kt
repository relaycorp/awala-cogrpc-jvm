package tech.relaycorp.relaynet.cogrpc.test

import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.Channel
import tech.relaycorp.relaynet.cogrpc.CargoDelivery
import tech.relaycorp.relaynet.cogrpc.CargoDeliveryAck
import tech.relaycorp.relaynet.cogrpc.CargoRelayGrpc

class MockCogRPCServerService : CargoRelayGrpc.CargoRelayImplBase() {

    var deliverCargoReceived: StreamObserver<CargoDeliveryAck>? = null
        private set
    var deliverCargoReturned: StreamObserver<CargoDelivery> = NoopStreamObserver()

    var collectCargoReceived: StreamObserver<CargoDelivery>? = null
        private set
    var collectCargoReturned: StreamObserver<CargoDeliveryAck> = NoopStreamObserver()
    private val collectCargoChannel = Channel<Unit>(1)

    override fun deliverCargo(
        responseObserver: StreamObserver<CargoDeliveryAck>
    ): StreamObserver<CargoDelivery> {
        deliverCargoReceived = responseObserver
        return deliverCargoReturned
    }

    override fun collectCargo(
        responseObserver: StreamObserver<CargoDelivery>
    ): StreamObserver<CargoDeliveryAck> {
        collectCargoReceived = responseObserver
        assert(collectCargoChannel.trySend(Unit).isSuccess)
        return collectCargoReturned
    }

    suspend fun addDeliveryToCollectionCall(delivery: CargoDelivery) {
        collectCargoChannel.receive()
        collectCargoReceived!!.onNext(delivery)
    }

    fun endCollectionCall() {
        collectCargoReceived!!.onCompleted()
    }
}
