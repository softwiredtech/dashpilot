package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.DisplaySettings
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.DashKitDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.util.NetworkUtil
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class ConnectionViewModel(private var networkUtil: NetworkUtil) : ViewModel() {
    private val _dataSource = MutableStateFlow<IDataSource?>(null)

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _displaySettings = MutableStateFlow(DisplaySettings())
    private val _speedCameraDistance = MutableStateFlow(-1)

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

    fun connect(context: Context, manualServerAddress: String, dataSourceType: String) {
        val current = _connectionStatus.value
        if (current !is ConnectionStatus.Disconnected && current !is ConnectionStatus.Error) return

        var finalServerAddress = manualServerAddress
        _connectionStatus.value = ConnectionStatus.Connecting
        viewModelScope.launch(Dispatchers.IO) {
            val vehicleName = "tesla" // TODO: make configurable via UI
            val prefs = context.getSharedPreferences("dash_prefs", Context.MODE_PRIVATE)
            val extraBus = prefs.getBoolean("extra_vehicle_bus", false)
            val configFile = if (extraBus) "config_comma_extra_bus.json" else "config_comma_normal.json"
            val profile = VehicleProfileLoader.loadProfile(context, vehicleName, configFile)
            val bridge = VehicleBridge()
            val ds = when (dataSourceType) {
                "ble" -> {
                    val decoder = CanFrameDecoder(bridge, profile)
                    DashKitDataSource(context, decoder)
                }
                "websocket" -> WebsocketDataSource()
                else -> CommaDataSource(bridge, profile)
            }

            if (finalServerAddress.isEmpty() && dataSourceType == "comma") {
                val localIp = networkUtil.getWifiIpAddress()
                val found = localIp?.let { networkUtil.findCanPublisher(initialIp = it) }
                if (found == null) {
                    _connectionStatus.value = ConnectionStatus.Error("No device found")
                    return@launch
                }
                finalServerAddress = found
            }

            _dataSource.value = ds


            _displaySettings.value = DisplaySettings(
                showPhoneBattery = prefs.getBoolean("show_phone_battery", true),
                showCarBattery = prefs.getBoolean("show_car_battery", true),
                showOdometer = prefs.getBoolean("show_odometer", true),
                useImperial = prefs.getBoolean("use_imperial", false),
                darkMode = prefs.getBoolean("dark_mode", false),
                alwaysOnBlindSpotMonitor = prefs.getBoolean("always_on_blind_spot_monitor", true),
                renderQuality = prefs.getInt("render_quality", 3)
            )

            val combined = combine(
                ds.incomingMessages,
                phoneBatteryFlow(context),
                _displaySettings,
                _speedCameraDistance
            ) { carState, battery, settings, camDist ->
                val converted = if (settings.useImperial) carState.toImperial() else carState
                DashState(
                    carState = converted,
                    phoneBattery = battery,
                    currentTime = System.currentTimeMillis(),
                    displaySettings = settings,
                    speedCameraDistance = camDist
                )
            }
            _dashState.value = combined

            launch(Dispatchers.IO) { ds.connect(finalServerAddress) }
            ds.incomingMessages.first()
            _connectionStatus.value = ConnectionStatus.Connected
        }
    }

    fun updateDisplaySettings(settings: DisplaySettings) {
        _displaySettings.value = settings
    }

    fun bindSpeedCamera(flow: StateFlow<Pair<*, Int>?>) {
        viewModelScope.launch {
            flow.collect { _speedCameraDistance.value = it?.second ?: -1 }
        }
    }

    fun disconnect() {
        _dataSource.value?.disconnect()
        _dataSource.value = null
        _dashState.value = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }
}
