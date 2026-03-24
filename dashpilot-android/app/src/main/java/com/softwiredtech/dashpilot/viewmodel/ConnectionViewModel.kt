package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.PandaBleDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConnectionViewModel : ViewModel() {

    private val _dataSource = MutableStateFlow<IDataSource?>(null)
    val dataSource = _dataSource.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus = _connectionStatus.asStateFlow()

    fun connect(context: Context, serverAddress: String, dataSourceType: String) {
        if (_connectionStatus.value != ConnectionStatus.Disconnected) return

        val vehicleName = "tesla" // TODO: make configurable via UI
        val profile = VehicleProfileLoader.loadProfile(context, vehicleName)
        val bridge = VehicleBridge()
        val ds = when (dataSourceType) {
            "ble" -> {
                val decoder = CanFrameDecoder(bridge, profile)
                PandaBleDataSource(context, decoder)
            }
            "websocket" -> WebsocketDataSource()
            else -> CommaDataSource(bridge, profile)
        }
        _dataSource.value = ds
        _connectionStatus.value = ConnectionStatus.Connecting
        viewModelScope.launch(Dispatchers.IO) {
            ds.connect(serverAddress)
        }
        viewModelScope.launch {
            ds.incomingMessages.first()
            _connectionStatus.value = ConnectionStatus.Connected
        }
    }

    fun disconnect() {
        _dataSource.value?.disconnect()
        _dataSource.value = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }
}
