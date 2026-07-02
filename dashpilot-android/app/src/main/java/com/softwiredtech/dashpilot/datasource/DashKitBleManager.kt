package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface GattListener {
    fun onServicesReady(gatt: BluetoothGatt) {}
    fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {}
    fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {}
    fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {}
    fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {}
    fun onDisconnected() {}
}

@SuppressLint("MissingPermission")
class DashKitBleManager(private val context: Context) {

    companion object {
        private const val TAG = "DashKitBleManager"
        private const val DEVICE_NAME = "DashKit"
    }

    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState

    var gatt: BluetoothGatt? = null
        private set

    var mtu: Int = 23
        private set

    private var scanning = false
    private val listeners = mutableListOf<GattListener>()

    // Guards against running the post-bond discovery sequence more than once
    // per connection (e.g. bonded-at-connect plus a stray bond broadcast).
    private var discoveryStarted = false
    private var bondReceiverRegistered = false

    // Re-pairing after the phone "forgot" the bond fails the first attempt:
    // DashKit reboots to clear residual pairing state, which drops the link
    // before it is bonded. Reconnect once automatically so the freshly-booted
    // DashKit pairs cleanly without the user having to tap "pair" again. Reset
    // on each user-initiated connect().
    private var rePairRetryDone = false

    // DashKit uses "Just Works" pairing. Android must bond before it can touch
    // the encryption-protected characteristics, so we wait for the bond to
    // complete before requesting the MTU / discovering services.
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.bluetoothDevice() ?: return
            val g = gatt ?: return
            if (device.address != g.device.address) return

            val state = intent.getIntExtra(
                BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR
            )
            if (state == BluetoothDevice.BOND_BONDED) {
                Log.d(TAG, "Bonded with DashKit")
                startDiscovery(g)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    fun addGattListener(listener: GattListener) {
        synchronized(listeners) { listeners.add(listener) }
        // If already connected with services discovered, notify immediately
        val g = gatt
        if (g != null && _connectionState.value == ConnectionStatus.Connected) {
            listener.onServicesReady(g)
        }
    }

    fun removeGattListener(listener: GattListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun forEachListener(action: (GattListener) -> Unit) {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach(action)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            if (name == DEVICE_NAME) {
                Log.d(TAG, "Found DashKit: ${result.device.address}")
                stopScan()
                result.device.connectGatt(
                    context, false, gattCallback,
                    android.bluetooth.BluetoothDevice.TRANSPORT_LE
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            _connectionState.value = ConnectionStatus.Error("BLE scan failed (error $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server")
                gatt = g
                discoveryStarted = false
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                // DashKit's characteristics require an encrypted (paired) link.
                // Ensure we are bonded before discovering services.
                when (g.device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        Log.d(TAG, "Already bonded; proceeding to discovery")
                        startDiscovery(g)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        Log.d(TAG, "Bonding already in progress; waiting")
                    }
                    else -> {
                        Log.d(TAG, "Not bonded; starting pairing")
                        if (!g.device.createBond()) {
                            Log.e(TAG, "createBond() failed to start")
                            _connectionState.value =
                                ConnectionStatus.Error("Could not start pairing")
                        }
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                val reachedConnected = _connectionState.value == ConnectionStatus.Connected
                try { g.close() } catch (_: Exception) {}
                gatt = null
                discoveryStarted = false
                if (!reachedConnected) {
                    if (!rePairRetryDone) {
                        // Pairing did not complete — DashKit reboots to clear
                        // residual pairing state, dropping the link. Reconnect
                        // once automatically to pair against the fresh boot.
                        rePairRetryDone = true
                        Log.d(TAG, "Link dropped before pairing completed; reconnecting once")
                        startScan()
                        return
                    }
                    Log.e(TAG, "Pairing failed")
                    _connectionState.value = ConnectionStatus.Error("Pairing failed")
                    forEachListener { it.onDisconnected() }
                    return
                }
                _connectionState.value = ConnectionStatus.Disconnected
                forEachListener { it.onDisconnected() }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtu = newMtu
                Log.d(TAG, "MTU changed to $newMtu")
            }
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.value = ConnectionStatus.Error("Service discovery failed")
                return
            }
            Log.d(TAG, "Discovered ${g.services.size} services:")
            for (s in g.services) {
                Log.d(TAG, "  service: ${s.uuid}")
                for (c in s.characteristics) {
                    Log.d(TAG, "    char: ${c.uuid} props=${c.properties}")
                }
            }
            _connectionState.value = ConnectionStatus.Connected
            forEachListener { it.onServicesReady(g) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite status=${if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else status.toString()}")
            forEachListener { it.onDescriptorWrite(g, descriptor, status) }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            forEachListener { it.onCharacteristicChanged(g, characteristic, value) }
        }

        // API < 33
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            forEachListener { it.onCharacteristicChanged(g, characteristic, value) }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            forEachListener { it.onCharacteristicWrite(g, characteristic, status) }
        }

        // API 33+
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            forEachListener { it.onCharacteristicRead(g, characteristic, value, status) }
        }

        // API < 33
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: ByteArray(0)
            forEachListener { it.onCharacteristicRead(g, characteristic, value, status) }
        }
    }

    // Runs once the link is bonded: clear Android's stale GATT cache, then
    // request the MTU which (in onMtuChanged) kicks off service discovery.
    private fun startDiscovery(g: BluetoothGatt) {
        if (discoveryStarted) return
        discoveryStarted = true
        try {
            val refresh = g.javaClass.getMethod("refresh")
            val result = refresh.invoke(g) as Boolean
            Log.d(TAG, "GATT cache refresh: $result")
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear GATT cache: ${e.message}")
        }
        // Delay to let cache clear take effect, then request MTU
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            g.requestMtu(512)
        }, 600)
    }

    fun connect() {
        if (_connectionState.value == ConnectionStatus.Connected ||
            _connectionState.value == ConnectionStatus.Connecting) return
        _connectionState.value = ConnectionStatus.Connecting
        rePairRetryDone = false
        registerBondReceiver()
        startScan()
    }

    fun disconnect() {
        stopScan()
        unregisterBondReceiver()
        gatt?.close()
        gatt = null
        discoveryStarted = false
        _connectionState.value = ConnectionStatus.Disconnected
        forEachListener { it.onDisconnected() }
    }

    private fun registerBondReceiver() {
        if (bondReceiverRegistered) return
        context.registerReceiver(
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
        bondReceiverRegistered = true
    }

    private fun unregisterBondReceiver() {
        if (!bondReceiverRegistered) return
        try { context.unregisterReceiver(bondReceiver) } catch (_: Exception) {}
        bondReceiverRegistered = false
    }

    private fun startScan() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
            .adapter ?: run {
            Log.e(TAG, "Bluetooth not available")
            _connectionState.value = ConnectionStatus.Error("Bluetooth not available")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            _connectionState.value = ConnectionStatus.Error("BLE scanner not available")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed — missing permission: ${e.message}")
            _connectionState.value = ConnectionStatus.Error("Missing BLE permission")
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
            _connectionState.value = ConnectionStatus.Error("Scan failed: ${e.message}")
        }
    }

    private fun stopScan() {
        if (!scanning) return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanning = false
    }
}
