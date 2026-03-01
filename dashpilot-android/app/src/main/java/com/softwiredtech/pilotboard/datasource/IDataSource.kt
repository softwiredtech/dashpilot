package com.softwiredtech.pilotboard.datasource

import com.softwiredtech.pilotboard.datamodel.WorldMessage
import kotlinx.coroutines.flow.Flow

interface IDataSource {
    fun connect(address: String)
    fun disconnect()
    val incomingMessages: Flow<WorldMessage>
}
