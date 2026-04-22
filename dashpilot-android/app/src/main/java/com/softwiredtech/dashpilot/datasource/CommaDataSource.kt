package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.CarState
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample

class CommaDataSource(
    private val bridge: VehicleBridge,
    private val profile: VehicleProfile
) : IDataSource {
    private val _incoming = MutableSharedFlow<CarState>(
        replay = 1
    )

    @OptIn(FlowPreview::class)
    // Change sample ms to throttle up or down the flow.
    override val incomingMessages: Flow<CarState> = _incoming.sample(25)

    private val ctx = bridge.nativeCreateContext()
    private val decoderHandle = bridge.nativeCreateVehicleDecoder(
        profile.dbcContents, profile.busIndices, profile.type
    )
    private val reusableBuffer = DoubleArray(CarState.FIELD_COUNT)

    private var groupHandle: Long = 0

    override fun connect(address: String) {
        groupHandle = bridge.nativeCreateSubSockets(
            ctx, arrayOf("can", "selfdriveState", "selfdriveStateSP"), address
        )
        bridge.nativeStartReceiveLoop(
            decoderHandle, groupHandle, reusableBuffer
        ) { values ->
            _incoming.tryEmit(CanFrameDecoder.arrayToCarState(values))
        }
    }

    override fun disconnect() {
        bridge.nativeStopReceiveLoop()
        bridge.nativeDeleteSubSockets(groupHandle)
        bridge.nativeDestroyVehicleDecoder(decoderHandle)
        bridge.nativeDeleteContext(ctx)
    }
}
