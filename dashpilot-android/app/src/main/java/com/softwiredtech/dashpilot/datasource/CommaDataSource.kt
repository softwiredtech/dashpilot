package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CommaDataSource(
    private val bridge: VehicleBridge,
    private val profile: VehicleProfile
) : IDataSource {
    private val _incoming = MutableSharedFlow<CarState>(
        replay = 1
    )

    @OptIn(FlowPreview::class)
    // Change sample ms to throttle up or down the flow.
    override val incomingMessages: Flow<CarState> = _incoming.sample(40)

    private val ctx = bridge.nativeCreateContext()
    private val decoderHandle = bridge.nativeCreateVehicleDecoder(
        profile.dbcContents, profile.busIndices, profile.type
    )
    private val reusableBuffer = DoubleArray(CarState.FIELD_COUNT)

    private var groupHandle: Long = 0

    // connect() runs the receive loop on an IO thread while disconnect() can
    // come from anywhere. A stop issued before the loop has started is lost
    // (the native loop then runs on freed sockets and decoder), so disconnect
    // keeps stopping until connect's loop has actually returned.
    private val lock = Any()
    private var closed = false          // guarded by lock
    private var loopStarting = false    // guarded by lock
    private val loopEnded = CountDownLatch(1)

    override fun connect(address: String) {
        synchronized(lock) {
            if (closed) return
            groupHandle = bridge.nativeCreateSubSockets(
                ctx, arrayOf("can", "selfdriveState", "selfdriveStateSP"), address
            )
            loopStarting = true
        }
        try {
            bridge.nativeStartReceiveLoop(
                decoderHandle, groupHandle, reusableBuffer
            ) { values ->
                _incoming.tryEmit(CanFrameDecoder.arrayToCarState(values))
            }
        } finally {
            loopEnded.countDown()
        }
    }

    override fun disconnect() {
        val waitForLoop = synchronized(lock) {
            closed = true
            loopStarting
        }
        if (waitForLoop) {
            do {
                bridge.nativeStopReceiveLoop()
            } while (!loopEnded.await(5, TimeUnit.MILLISECONDS))
        }
        bridge.nativeDeleteSubSockets(groupHandle)
        bridge.nativeDestroyVehicleDecoder(decoderHandle)
        bridge.nativeDeleteContext(ctx)
    }
}
