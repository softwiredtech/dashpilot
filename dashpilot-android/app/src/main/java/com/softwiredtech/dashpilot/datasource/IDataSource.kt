package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.flow.Flow

interface IDataSource {
    fun connect(address: String)
    fun disconnect()
    val incomingMessages: Flow<CarState>

    // Transmit a single CAN frame. Default no-op for sources that don't
    // support CAN TX (e.g. read-only websocket / comma sources).
    fun sendCanFrame(
        bus: Int,
        addr: Int,
        data: ByteArray,
        extended: Boolean = false,
        fd: Boolean = false,
        brs: Boolean = false,
    ) {}
}
