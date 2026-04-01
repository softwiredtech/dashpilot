package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.datamodel.DashState
import com.softwiredtech.dashpilot.datamodel.DisplaySettings
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.PandaBleDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class ConnectionViewModel : ViewModel() {
    private val _dataSource = MutableStateFlow<IDataSource?>(null)

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _displaySettings = MutableStateFlow(DisplaySettings())

    private val _dashState = MutableStateFlow<Flow<DashState>?>(null)
    val dashState = _dashState.asStateFlow()

    private fun phoneBatteryFlow(context: Context): Flow<Int> = flow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            emit(level)
            delay(30_000)
        }
    }

    fun connect(context: Context, serverAddress: String, dataSourceType: String) {
        if (_connectionStatus.value != ConnectionStatus.Disconnected) return

        _connectionStatus.value = ConnectionStatus.Connecting
        viewModelScope.launch(Dispatchers.IO) {
            val vehicleName = "tesla" // TODO: make configurable via UI
            val extraBus = context.getSharedPreferences("dash_prefs", Context.MODE_PRIVATE)
                .getBoolean("extra_vehicle_bus", false)
            val configFile = if (extraBus) "config_comma_extra_bus.json" else "config_comma_normal.json"
            val profile = VehicleProfileLoader.loadProfile(context, vehicleName, configFile)
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

            val prefs = context.getSharedPreferences("dash_prefs", Context.MODE_PRIVATE)
            _displaySettings.value = DisplaySettings(
                showPhoneBattery = prefs.getBoolean("show_phone_battery", true),
                showCarBattery = prefs.getBoolean("show_car_battery", true),
                showOdometer = prefs.getBoolean("show_odometer", true)
            )

            val combined = combine(
                ds.incomingMessages,
                phoneBatteryFlow(context),
                _displaySettings
            ) { carState, battery, settings ->
                DashState(
                    carState = carState,
                    phoneBattery = battery,
                    currentTime = System.currentTimeMillis(),
                    displaySettings = settings
                )
            }
            _dashState.value = combined

            launch(Dispatchers.IO) { ds.connect(serverAddress) }
            ds.incomingMessages.first()
            _connectionStatus.value = ConnectionStatus.Connected
        }
    }

    fun updateDisplaySettings(settings: DisplaySettings) {
        _displaySettings.value = settings
    }

    fun disconnect() {
        _dataSource.value?.disconnect()
        _dataSource.value = null
        _dashState.value = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }
}
