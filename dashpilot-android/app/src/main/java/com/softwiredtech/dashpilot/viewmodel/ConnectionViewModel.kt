package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.DisplaySettings
import com.softwiredtech.dashpilot.datamodel.dash.PREF_ALWAYS_ON_BLIND_SPOT_MONITOR
import com.softwiredtech.dashpilot.datamodel.dash.PREF_DARK_MODE
import com.softwiredtech.dashpilot.datamodel.dash.PREF_DARK_MODE_BACKGROUND_GRAY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_EXTRA_VEHICLE_BUS
import com.softwiredtech.dashpilot.datamodel.dash.PREF_RENDER_QUALITY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_SHOW_CAR_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_SHOW_ODOMETER
import com.softwiredtech.dashpilot.datamodel.dash.PREF_SHOW_PHONE_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_USE_IMPERIAL
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_DARK_MODE
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_DARK_MODE_BACKGROUND_GRAY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_EXTRA_VEHICLE_BUS
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_RENDER_QUALITY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_SHOW_CAR_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_SHOW_ODOMETER
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_SHOW_PHONE_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_USE_IMPERIAL
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.datasource.DashKitDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.util.NetworkUtil
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var connectionJob: Job? = null

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _bleManager = MutableStateFlow<DashKitBleManager?>(null)
    val bleManager: StateFlow<DashKitBleManager?> = _bleManager.asStateFlow()

    private val _hasAutoNavigatedToDashboard = MutableStateFlow(false)
    val hasAutoNavigatedToDashboard = _hasAutoNavigatedToDashboard.asStateFlow()

    fun markAutoNavigated() {
        _hasAutoNavigatedToDashboard.value = true
    }

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
        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            val vehicleName = "tesla" // TODO: make configurable via UI
            val prefs = context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
            val extraBus = prefs.getBoolean(PREF_EXTRA_VEHICLE_BUS, DEFAULT_EXTRA_VEHICLE_BUS)
            val configFile = when (dataSourceType) {
                DataSourceType.DASHKIT -> "config_dashkit.json"
                else -> if (extraBus) "config_comma_extra_bus.json" else "config_comma_normal.json"
            }
            val profile = VehicleProfileLoader.loadProfile(context, vehicleName, configFile)
            val bridge = VehicleBridge()
            val ds = when (dataSourceType) {
                DataSourceType.DASHKIT -> {
                    val manager = DashKitBleManager(context)
                    _bleManager.value = manager
                    val decoder = CanFrameDecoder(bridge, profile)
                    DashKitDataSource(manager, decoder)
                }
                DataSourceType.WEBSOCKET -> WebsocketDataSource()
                else -> CommaDataSource(bridge, profile)
            }

            if (finalServerAddress.isEmpty() && dataSourceType == DataSourceType.COMMA) {
                var localIp = networkUtil.getWifiIpAddress()
                while (localIp == null) {
                    delay(1000)
                    localIp = networkUtil.getWifiIpAddress()
                }
                finalServerAddress = networkUtil.findCanPublisher(initialIp = localIp)
            }

            _dataSource.value = ds

            _displaySettings.value = DisplaySettings(
                showPhoneBattery = prefs.getBoolean(PREF_SHOW_PHONE_BATTERY, DEFAULT_SHOW_PHONE_BATTERY),
                showCarBattery = prefs.getBoolean(PREF_SHOW_CAR_BATTERY, DEFAULT_SHOW_CAR_BATTERY),
                showOdometer = prefs.getBoolean(PREF_SHOW_ODOMETER, DEFAULT_SHOW_ODOMETER),
                useImperial = prefs.getBoolean(PREF_USE_IMPERIAL, DEFAULT_USE_IMPERIAL),
                darkMode = prefs.getBoolean(PREF_DARK_MODE, DEFAULT_DARK_MODE),
                alwaysOnBlindSpotMonitor = prefs.getBoolean(PREF_ALWAYS_ON_BLIND_SPOT_MONITOR, DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR),
                renderQuality = prefs.getInt(PREF_RENDER_QUALITY, DEFAULT_RENDER_QUALITY),
                darkModeBackgroundGray = prefs.getInt(PREF_DARK_MODE_BACKGROUND_GRAY, DEFAULT_DARK_MODE_BACKGROUND_GRAY),
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

    // Send a "left steering wheel scroll" event by spoofing VCLEFT_switchStatus.
    // Frame: ID 0x3C2 (962) on bus 1, mux=1, swcLeftScrollTicks=ticks
    // ticks is a signed 6-bit value [-32..31]; negative = scroll down (toward driver).
    fun sendSwcLeftScroll(ticks: Int = -1) {
        val ds = _dataSource.value ?: return
        val t = ticks.coerceIn(-32, 31) and 0x3F  // 6-bit two's complement
        val data = ByteArray(8)
        data[0] = 0x01                  // bits 0..1 = mux selector = 1
        data[2] = t.toByte()            // bits 16..21 = swcLeftScrollTicks
        ds.sendCanFrame(bus = 1, addr = 0x3C2, data = data)
    }

    fun disconnect() {
        val wasConnecting = _connectionStatus.value is ConnectionStatus.Connecting
        connectionJob?.cancel()
        connectionJob = null
        _dataSource.value?.disconnect()
        _dataSource.value = null
        _bleManager.value?.disconnect()
        _bleManager.value = null
        _dashState.value = null
        _hasAutoNavigatedToDashboard.value = false
        _connectionStatus.value = if (wasConnecting) {
            ConnectionStatus.Error("Discovery cancelled")
        } else {
            ConnectionStatus.Disconnected
        }
    }
}
