package com.softwiredtech.dashpilot.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
import com.softwiredtech.dashpilot.datamodel.dash.getPinnedControl
import com.softwiredtech.dashpilot.datamodel.dash.setPinnedControl
import com.softwiredtech.dashpilot.datamodel.dash.getFingerActions
import com.softwiredtech.dashpilot.datamodel.dash.setFingerActions
import com.softwiredtech.dashpilot.datamodel.dash.getWiperOffAutomation
import com.softwiredtech.dashpilot.datamodel.dash.setWiperOffAutomation
import com.softwiredtech.dashpilot.ui.controls.controlById
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.datasource.DashKitDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import com.softwiredtech.dashpilot.ble.VehicleControl
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ConnectionViewModel(private var networkUtil: NetworkUtil) : ViewModel() {
    private val _dataSource = MutableStateFlow<IDataSource?>(null)
    private var connectionJob: Job? = null

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _bleManager = MutableStateFlow<DashKitBleManager?>(null)
    val bleManager: StateFlow<DashKitBleManager?> = _bleManager.asStateFlow()

    var blePermissionGate: ((onGranted: () -> Unit) -> Unit)? = null

    private val _hasAutoNavigatedToDashboard = MutableStateFlow(false)
    val hasAutoNavigatedToDashboard = _hasAutoNavigatedToDashboard.asStateFlow()

    fun markAutoNavigated() {
        _hasAutoNavigatedToDashboard.value = true
    }

    private val _pinnedControl = MutableStateFlow<String?>(null)
    val pinnedControl = _pinnedControl.asStateFlow()

    fun loadPinnedControl(context: Context) {
        _pinnedControl.value = getPinnedControl(context)
    }

    fun togglePinnedControl(context: Context, id: String) {
        val next = if (_pinnedControl.value == id) null else id
        setPinnedControl(context, next)
        _pinnedControl.value = next
    }

    private val _wiperOffAutomation = MutableStateFlow(false)
    val wiperOffAutomation = _wiperOffAutomation.asStateFlow()

    private val _fingerActions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val fingerActions = _fingerActions.asStateFlow()

    fun loadAutomations(context: Context) {
        _wiperOffAutomation.value = getWiperOffAutomation(context)
        _fingerActions.value = getFingerActions(context)
    }

    fun updateWiperOffAutomation(context: Context, value: Boolean) {
        setWiperOffAutomation(context, value)
        _wiperOffAutomation.value = value
        pushWiperOff(value)
    }

    private fun pushWiperOff(enabled: Boolean) {
        _bleManager.value?.let { VehicleControl.sendWiperOff(it, enabled) }
    }

    fun setFingerAction(context: Context, fingers: Int, id: String?) {
        val next = _fingerActions.value.toMutableMap()
        if (id.isNullOrBlank()) next.remove(fingers) else next[fingers] = id
        _fingerActions.value = next
        setFingerActions(context, next)
        pushFingerAction(fingers, id)
    }

    fun changeFingerCount(context: Context, from: Int, to: Int) {
        if (from == to) return
        val current = _fingerActions.value
        val id = current[from] ?: return
        val next = current.toMutableMap()
        next.remove(from)
        next[to] = id
        _fingerActions.value = next
        setFingerActions(context, next)
        pushFingerAction(from, null)  // clear the old slot on the firmware
        pushFingerAction(to, id)
    }

    fun removeFingerAction(context: Context, fingers: Int) {
        setFingerAction(context, fingers, null)
    }

    private fun pushFingerAction(fingers: Int, id: String?) {
        val actionValue = controlById(id)?.gestureValue ?: VehicleControl.GESTURE_ACTION_NONE
        _bleManager.value?.let { VehicleControl.sendFingerAction(it, fingers, actionValue) }
    }

    enum class StartupTarget { LOADING, ONBOARDING_DASHKIT, AUTOCONNECT_DASHKIT, DEFAULT }

    private val _startupTarget = MutableStateFlow(StartupTarget.LOADING)
    val startupTarget = _startupTarget.asStateFlow()
    private var startupDiscoveryStarted = false

    private var lastDataSourceType: String? = null
    private var lastServerAddress: String = ""

    // Briefly scan for an advertising DashKit and decide the launch route:
    //  - found + bonded   -> auto-connect DashKit
    //  - found + unbonded -> open onboarding (pair flow)
    //  - not found / no permission -> default (setup + comma)
    fun runStartupDiscovery(context: Context, hasBlePermission: Boolean) {
        if (startupDiscoveryStarted) return
        startupDiscoveryStarted = true

        if (!hasBlePermission) {
            _startupTarget.value = StartupTarget.DEFAULT
            return
        }

        viewModelScope.launch {
            val target = withTimeoutOrNull(STARTUP_SCAN_TIMEOUT_MS) {
                scanForDashKit(context)
            } ?: StartupTarget.DEFAULT
            _startupTarget.value = target
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDashKit(context: Context): StartupTarget =
        suspendCancellableCoroutine { cont ->
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                if (cont.isActive) cont.resume(StartupTarget.DEFAULT)
                return@suspendCancellableCoroutine
            }

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.device.name ?: result.scanRecord?.deviceName
                    if (name != DASHKIT_DEVICE_NAME) return
                    val bonded = result.device.bondState == BluetoothDevice.BOND_BONDED
                    try { scanner.stopScan(this) } catch (_: Exception) {}
                    if (cont.isActive) {
                        cont.resume(
                            if (bonded) StartupTarget.AUTOCONNECT_DASHKIT
                            else StartupTarget.ONBOARDING_DASHKIT
                        )
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("ConnectionViewModel", "Startup scan failed: $errorCode")
                    if (cont.isActive) cont.resume(StartupTarget.DEFAULT)
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            try {
                scanner.startScan(null, settings, callback)
            } catch (e: Exception) {
                Log.e("ConnectionViewModel", "Startup scan could not start: ${e.message}")
                if (cont.isActive) cont.resume(StartupTarget.DEFAULT)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try { scanner.stopScan(callback) } catch (_: Exception) {}
            }
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

    private fun hasBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun connect(context: Context, manualServerAddress: String, dataSourceType: String) {
        if (dataSourceType == DataSourceType.DASHKIT && !hasBluetoothPermission(context)) {
            val gate = blePermissionGate
            if (gate != null) {
                gate { connect(context, manualServerAddress, dataSourceType) }
            } else {
                _connectionStatus.value = ConnectionStatus.Error("Missing BLE permission")
            }
            return
        }

        val current = _connectionStatus.value
        if (current !is ConnectionStatus.Disconnected && current !is ConnectionStatus.Error) return

        lastDataSourceType = dataSourceType
        lastServerAddress = manualServerAddress

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
                    launch {
                        manager.connectionState.collect { st ->
                            if (st is ConnectionStatus.Error &&
                                _connectionStatus.value is ConnectionStatus.Connecting
                            ) {
                                _connectionStatus.value = st
                            }
                        }
                    }
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

            // The firmware also persists the bindings in NVS, but re-sync every
            // finger count on each connect so its state matches the app exactly
            // (e.g. after a DashKit reboot or an edit made while disconnected).
            // The GATT services are discovered by the time the first CAN frame
            // arrives above.
            _bleManager.value?.let { mgr ->
                val actions = getFingerActions(context)
                _fingerActions.value = actions
                for (fingers in 3..5) {
                    val actionValue = controlById(actions[fingers])?.gestureValue
                        ?: VehicleControl.GESTURE_ACTION_NONE
                    VehicleControl.sendFingerAction(mgr, fingers, actionValue)
                }

                // Re-sync the wiper-off automation flag for the same reason.
                val wiperOff = getWiperOffAutomation(context)
                _wiperOffAutomation.value = wiperOff
                VehicleControl.sendWiperOff(mgr, wiperOff)
            }
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

    private fun teardownConnection() {
        connectionJob?.cancel()
        connectionJob = null
        _dataSource.value?.disconnect()
        _dataSource.value = null
        _bleManager.value?.disconnect()
        _bleManager.value = null
        _dashState.value = null
        _hasAutoNavigatedToDashboard.value = false
    }

    fun disconnect() {
        val wasConnecting = _connectionStatus.value is ConnectionStatus.Connecting
        teardownConnection()
        _connectionStatus.value = if (wasConnecting) {
            ConnectionStatus.Error("Discovery cancelled")
        } else {
            ConnectionStatus.Disconnected
        }
    }

    fun onAppForegrounded(context: Context) {
        val type = lastDataSourceType ?: return
        val address = lastServerAddress
        teardownConnection()
        _connectionStatus.value = ConnectionStatus.Disconnected
        connect(context, address, type)
    }

    companion object {
        private const val DASHKIT_DEVICE_NAME = "DashKit"
        private const val STARTUP_SCAN_TIMEOUT_MS = 3000L
    }
}
