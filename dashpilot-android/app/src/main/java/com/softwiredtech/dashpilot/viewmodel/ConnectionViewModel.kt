package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.PandaBleDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel : ViewModel() {

    private val _dataSource = MutableStateFlow<IDataSource?>(null)
    val dataSource = _dataSource.asStateFlow()

    val isConnected: Boolean get() = _dataSource.value != null

    fun connect(context: Context, serverAddress: String, dataSourceType: String) {
        if (_dataSource.value != null) return

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
        viewModelScope.launch(Dispatchers.IO) {
            ds.connect(serverAddress)
        }
    }

    fun disconnect() {
        _dataSource.value?.disconnect()
        _dataSource.value = null
    }
}