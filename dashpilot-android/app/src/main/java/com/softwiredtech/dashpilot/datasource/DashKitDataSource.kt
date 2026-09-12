package com.softwiredtech.dashpilot.datasource

import android.content.Context
import com.softwiredtech.dashkitconnect.CarState
import com.softwiredtech.dashkitconnect.DashKitBleManager
import com.softwiredtech.dashkitconnect.can.DashKitCarStateSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample

class DashKitDataSource(
    context: Context,
    private val manager: DashKitBleManager,
) : IDataSource {

    private val source = DashKitCarStateSource(context, manager)

    @OptIn(FlowPreview::class)
    override val incomingMessages: Flow<CarState> = source.carState.sample(40)

    override fun connect(address: String) {
        source.start()
        manager.connect()
    }

    override fun disconnect() {
        source.stop()
    }
}
