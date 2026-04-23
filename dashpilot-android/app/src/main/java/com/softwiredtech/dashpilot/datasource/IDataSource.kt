package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.flow.Flow

interface IDataSource {
    fun connect(address: String)
    fun disconnect()
    val incomingMessages: Flow<CarState>
}
