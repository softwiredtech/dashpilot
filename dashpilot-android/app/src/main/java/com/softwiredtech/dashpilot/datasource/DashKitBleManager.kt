package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.UUID
import com.softwiredtech.dashpilot.ble.VehicleControl
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

// Dedicated type so BLE connection failures group under their own
// Crashlytics issues instead of a generic java.lang.Exception.
class BleConnectionException(message: String) : Exception(message)

@SuppressLint("MissingPermission")
class DashKitBleManager(private val context: Context) {

    companion object {
        private const val TAG = "DashKitBleManager"
        private const val DEVICE_NAME = "DashKit"

        // A connection attempt is one scan + connect cycle. Retry a few times
        // with a pause in between (DashKit may be mid-reboot, or the previous
        // link may still be tearing down) before surfacing an error. The pause
        // also keeps us under Android's 5-scans-per-30s throttle, which
        // silently suppresses scan results when exceeded.
        private const val MAX_ATTEMPTS = 3
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val RETRY_DELAY_MS = 2_000L

        // A drop this soon after reaching Connected is treated as a setup
        // failure rather than a mid-session loss. Encryption is only initiated
        // when the data source subscribes (service discovery runs unencrypted),
        // so a stale bond shows up as repeated drops just after Connected.
        private const val QUICK_DROP_WINDOW_MS = 15_000L
        private const val MAX_QUICK_DROPS = 3

        // Matches PAIRING_WINDOW_S in the firmware.
        private const val PAIRING_WINDOW_MS = 120_000L

        // Keepalive interval; the firmware drops the link after 60 s without a
        // ping (KEEPALIVE_TIMEOUT_S), so this allows a few missed writes.
        private const val PING_INTERVAL_MS = 15_000L

        // Android's GATT client allows only one operation in flight, so a CCCD
        // write racing another subscribe can be refused; retry a few times.
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_SUBSCRIBE_ATTEMPTS = 3
        private const val SUBSCRIBE_RETRY_DELAY_MS = 250L
    }

    private val crashlytics = FirebaseCrashlytics.getInstance()

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

    // Set only when the app itself tears down the link (disconnect()). Any other
    // drop after a healthy connection — most commonly a DashKit reboot — is
    // treated as transient and triggers an automatic reconnect.
    @Volatile
    private var userInitiatedDisconnect = false

    // Connect attempt issued from a scan result but not yet CONNECTED. Tracked
    // so disconnect() and the connect timeout can cancel it — an abandoned
    // connectGatt() would otherwise keep running (and eventually hold an
    // unmanaged link to DashKit).
    @Volatile
    private var pendingGatt: BluetoothGatt? = null

    // Scan+connect cycles used so far for the current connection intent.
    // Reset on connect(), on a successful connection, and when starting an
    // auto-reconnect after an unexpected drop.
    @Volatile
    private var attemptCount = 0

    // Stale-bond recovery. If DashKit no longer holds the key matching our OS
    // bond (its bond store was erased, or this phone's entry was evicted), every
    // reconnect completes service discovery and then dies when the subscription
    // triggers encryption. After MAX_QUICK_DROPS such cycles we delete the OS
    // bond and pair freshly — DashKit accepts that whenever its pairing window
    // is open. One reset per connection intent, so a refused re-pair can't loop.
    @Volatile
    private var quickDropCount = 0
    @Volatile
    private var bondResetDone = false
    @Volatile
    private var connectedAtMs = 0L

    // Set when this phone asks DashKit to open its pairing window. The firmware
    // drops us to free the single connection slot for the new device; until the
    // window ends we must not auto-reconnect and steal the slot back.
    @Volatile
    private var pairingWindowUntilMs = 0L

    /**
     * Called after sending the enter-pairing command: the upcoming disconnect
     * is expected, and auto-reconnect stays paused for the window duration.
     */
    fun expectPairingWindowDrop() {
        pairingWindowUntilMs = android.os.SystemClock.elapsedRealtime() + PAIRING_WINDOW_MS
    }

    // All timeout/retry scheduling (plus the post-bond discovery delay) runs on
    // this handler; disconnect() clears it wholesale.
    private val handler = Handler(Looper.getMainLooper())

    // Keepalive: reschedules itself while Connected and in the foreground.
    private val pingRunnable = object : Runnable {
        override fun run() {
            if (!appInForeground || _connectionState.value != ConnectionStatus.Connected) return
            VehicleControl.sendPing(this@DashKitBleManager)
            handler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    // Backgrounded apps deliberately stop pinging so the firmware frees the
    // slot; the process keeps running, so the gate must be explicit.
    @Volatile
    private var appInForeground = true

    fun onAppForegrounded() {
        appInForeground = true
        if (_connectionState.value == ConnectionStatus.Connected) {
            handler.removeCallbacks(pingRunnable)
            handler.post(pingRunnable)
        }
    }

    fun onAppBackgrounded() {
        appInForeground = false
        handler.removeCallbacks(pingRunnable)
    }

    private val retryRunnable = Runnable {
        if (!userInitiatedDisconnect && _connectionState.value == ConnectionStatus.Connecting) {
            startScan()
        }
    }

    private val scanTimeoutRunnable = Runnable {
        if (!scanning) return@Runnable
        Log.w(TAG, "Scan timed out without finding DashKit (attempt $attemptCount/$MAX_ATTEMPTS)")
        crashlytics.log("BLE: scan timed out (attempt $attemptCount/$MAX_ATTEMPTS)")
        stopScan()
        retryOrFail("DashKit not found")
    }

    private val connectTimeoutRunnable = Runnable {
        val pending = pendingGatt ?: return@Runnable
        pendingGatt = null
        Log.w(TAG, "Connect attempt timed out (attempt $attemptCount/$MAX_ATTEMPTS)")
        crashlytics.log("BLE: connect attempt timed out (attempt $attemptCount/$MAX_ATTEMPTS)")
        try { pending.close() } catch (_: Exception) {}
        retryOrFail("Could not connect to DashKit")
    }

    // Retry after a short pause while attempts remain, otherwise publish an
    // error so the UI leaves "Connecting…" instead of hanging forever.
    private fun retryOrFail(errorMessage: String) {
        if (userInitiatedDisconnect) return
        if (attemptCount < MAX_ATTEMPTS) {
            handler.postDelayed(retryRunnable, RETRY_DELAY_MS)
            return
        }
        crashlytics.recordException(
            BleConnectionException("$errorMessage (after $attemptCount attempts)")
        )
        _connectionState.value = ConnectionStatus.Error(errorMessage)
        forEachListener { it.onDisconnected() }
    }

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
            if (name != DEVICE_NAME) return
            // Duplicate advertisements are often delivered after stopScan();
            // never issue a second connectGatt() while one attempt or a live
            // connection exists.
            if (pendingGatt != null || gatt != null) return
            Log.d(TAG, "Found DashKit: ${result.device.address}")
            crashlytics.log("BLE: found DashKit, connecting")
            stopScan()
            handler.removeCallbacks(scanTimeoutRunnable)
            pendingGatt = result.device.connectGatt(
                context, false, gattCallback,
                android.bluetooth.BluetoothDevice.TRANSPORT_LE
            )
            handler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            crashlytics.recordException(BleConnectionException("BLE scan failed (error $errorCode)"))
            _connectionState.value = ConnectionStatus.Error("BLE scan failed (error $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (pendingGatt !== g) {
                    // This attempt was already abandoned (timed out or torn
                    // down by disconnect()); don't let it hold a link.
                    Log.w(TAG, "Ignoring stale GATT connection")
                    try { g.close() } catch (_: Exception) {}
                    return
                }
                handler.removeCallbacks(connectTimeoutRunnable)
                pendingGatt = null
                Log.d(TAG, "Connected to GATT server")
                crashlytics.log("BLE: GATT connected (bondState=${g.device.bondState})")
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
                            crashlytics.recordException(BleConnectionException("Could not start pairing (createBond failed)"))
                            _connectionState.value =
                                ConnectionStatus.Error("Could not start pairing")
                        }
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val wasPending = pendingGatt === g
                if (wasPending) {
                    handler.removeCallbacks(connectTimeoutRunnable)
                    pendingGatt = null
                }
                if (!wasPending && g !== gatt) {
                    // A client we already abandoned or closed; nothing to do
                    // beyond making sure it releases its resources.
                    try { g.close() } catch (_: Exception) {}
                    return
                }
                Log.d(TAG, "Disconnected from GATT server")
                val reachedConnected = _connectionState.value == ConnectionStatus.Connected
                try { g.close() } catch (_: Exception) {}
                gatt = null
                discoveryStarted = false
                if (!reachedConnected) {
                    // Link dropped before the connection was fully up: DashKit
                    // refused/tore down a pairing, the previous link was still
                    // clearing, or the connect attempt itself failed (e.g.
                    // status 133). Retry a few times before surfacing an error.
                    Log.w(TAG, "Link dropped before setup completed (status=$status)")
                    crashlytics.log("BLE: link dropped before setup completed (status=$status, attempt $attemptCount/$MAX_ATTEMPTS)")
                    // An unbonded device at this point means the pairing itself
                    // did not stick — DashKit only accepts new phones while its
                    // pairing window is open.
                    val message =
                        if (g.device.bondState == BluetoothDevice.BOND_BONDED) {
                            "Could not connect to DashKit"
                        } else {
                            "DashKit did not accept pairing. Open \"Pair a new device\" in DashPilot settings on an already-paired phone, then try again."
                        }
                    retryOrFail(message)
                    return
                }
                if (!userInitiatedDisconnect) {
                    if (android.os.SystemClock.elapsedRealtime() < pairingWindowUntilMs) {
                        // We asked DashKit to open its pairing window and it
                        // dropped us to free the only connection slot for the
                        // new device. Don't fight for it — stay disconnected;
                        // foregrounding the app (or a manual retry) reconnects
                        // once the window is over.
                        Log.d(TAG, "Dropped for pairing window; auto-reconnect paused")
                        crashlytics.log("BLE: dropped for pairing window; auto-reconnect paused")
                        _connectionState.value = ConnectionStatus.Disconnected
                        forEachListener { it.onDisconnected() }
                        return
                    }
                    if (!appInForeground) {
                        // Likely our own keepalive eviction; reconnecting from
                        // the background would squat on the slot without
                        // pinging. Foregrounding restores the session.
                        Log.d(TAG, "Dropped while backgrounded; reconnect deferred to foreground")
                        crashlytics.log("BLE: dropped while backgrounded; reconnect deferred to foreground")
                        _connectionState.value = ConnectionStatus.Disconnected
                        forEachListener { it.onDisconnected() }
                        return
                    }
                    val quickDrop =
                        android.os.SystemClock.elapsedRealtime() - connectedAtMs < QUICK_DROP_WINDOW_MS
                    quickDropCount = if (quickDrop) quickDropCount + 1 else 0
                    if (quickDrop && quickDropCount >= MAX_QUICK_DROPS && !bondResetDone &&
                        g.device.bondState == BluetoothDevice.BOND_BONDED
                    ) {
                        // Stale bond: discovery keeps succeeding but the link
                        // dies as soon as the subscription forces encryption.
                        // Delete our side of the bond so the next cycle pairs
                        // freshly instead of looping forever.
                        Log.w(TAG, "Repeated drops right after connect on a bonded link; removing stale bond to re-pair")
                        crashlytics.log("BLE: removing stale bond after $quickDropCount quick drops; re-pairing")
                        bondResetDone = true
                        quickDropCount = 0
                        removeBond(g.device)
                        // removeBond is asynchronous — reconnect after a pause
                        // so the next attempt starts from a clean, unbonded
                        // state and pairs freshly.
                        _connectionState.value = ConnectionStatus.Connecting
                        forEachListener { it.onDisconnected() }
                        attemptCount = 0
                        handler.postDelayed(retryRunnable, RETRY_DELAY_MS)
                        return
                    }
                    // Unexpected drop after a healthy connection (typically a
                    // DashKit reboot). The bond survives the reboot, so restart
                    // the scan: it reconnects, re-discovers services and the
                    // data source re-subscribes via onServicesReady.
                    Log.d(TAG, "Link dropped after connect; auto-reconnecting")
                    crashlytics.recordException(BleConnectionException("Link dropped unexpectedly after connect (status=$status); auto-reconnecting"))
                    _connectionState.value = ConnectionStatus.Connecting
                    forEachListener { it.onDisconnected() }
                    attemptCount = 0  // fresh retry budget for the reconnect
                    startScan()
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
                crashlytics.recordException(BleConnectionException("Service discovery failed (status=$status)"))
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
            crashlytics.log("BLE: connected (${g.services.size} services, mtu=$mtu)")
            attemptCount = 0
            connectedAtMs = android.os.SystemClock.elapsedRealtime()
            _connectionState.value = ConnectionStatus.Connected
            forEachListener { it.onServicesReady(g) }
            // Immediate first ping: the firmware only arms its timeout once
            // one arrives.
            handler.removeCallbacks(pingRunnable)
            handler.post(pingRunnable)
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
        handler.postDelayed({
            g.requestMtu(512)
        }, 600)
    }

    fun connect() {
        if (_connectionState.value == ConnectionStatus.Connected ||
            _connectionState.value == ConnectionStatus.Connecting) return
        _connectionState.value = ConnectionStatus.Connecting
        attemptCount = 0
        quickDropCount = 0
        bondResetDone = false
        userInitiatedDisconnect = false
        registerBondReceiver()
        startScan()
    }

    // BluetoothDevice.removeBond is a hidden API but stable since API 19 and
    // widely relied on (e.g. Nordic's BLE library) — there is no public way to
    // drop a bond programmatically.
    private fun removeBond(device: BluetoothDevice): Boolean =
        try {
            device.javaClass.getMethod("removeBond").invoke(device) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "removeBond failed: ${e.message}")
            crashlytics.log("BLE: removeBond failed: ${e.message}")
            false
        }

    fun disconnect() {
        userInitiatedDisconnect = true
        // Drops pending timeouts, scheduled retries and the discovery delay.
        handler.removeCallbacksAndMessages(null)
        stopScan()
        unregisterBondReceiver()
        pendingGatt?.let { try { it.close() } catch (_: Exception) {} }
        pendingGatt = null
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
            crashlytics.recordException(BleConnectionException("Bluetooth not available"))
            _connectionState.value = ConnectionStatus.Error("Bluetooth not available")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            crashlytics.recordException(BleConnectionException("BLE scanner not available (Bluetooth off?)"))
            _connectionState.value = ConnectionStatus.Error("BLE scanner not available")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // Filter on the device name: filtered scans are exempt from most of
        // Android's unfiltered-scan restrictions (screen-off suspension and
        // harsher duty-cycle throttling).
        val filters = listOf(
            ScanFilter.Builder().setDeviceName(DEVICE_NAME).build()
        )
        try {
            scanner.startScan(filters, settings, scanCallback)
            scanning = true
            attemptCount++
            handler.removeCallbacks(scanTimeoutRunnable)
            handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
            Log.d(TAG, "BLE scan started (attempt $attemptCount/$MAX_ATTEMPTS)")
            crashlytics.log("BLE: scan started (attempt $attemptCount/$MAX_ATTEMPTS)")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed — missing permission: ${e.message}")
            crashlytics.recordException(BleConnectionException("BLE scan failed — missing permission: ${e.message}"))
            _connectionState.value = ConnectionStatus.Error("Missing BLE permission")
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
            crashlytics.recordException(BleConnectionException("BLE scan failed: ${e.message}"))
            _connectionState.value = ConnectionStatus.Error("Scan failed: ${e.message}")
        }
    }

    private fun stopScan() {
        handler.removeCallbacks(scanTimeoutRunnable)
        if (!scanning) return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanning = false
    }

    /** Subscribes to a characteristic's notifications, retrying if the CCCD
     * write races another GATT operation. Shared by the CAN and Tesla status
     * sources. */
    fun subscribeWithRetry(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        logTag: String,
        label: String
    ) {
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            Log.e(logTag, "$label CCCD (0x2902) not discovered")
            return
        }
        var attempt = 0
        fun writeCccd() {
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            if (!queued && attempt < MAX_SUBSCRIBE_ATTEMPTS) {
                attempt++
                Log.d(logTag, "$label CCCD write not queued (attempt=$attempt); retrying")
                handler.postDelayed({ writeCccd() }, SUBSCRIBE_RETRY_DELAY_MS)
            }
        }
        writeCccd()
    }

    /** Write-with-response to a characteristic on the current connection.
     * Returns true if the write was dispatched (not acknowledged). Shared by
     * the CAN control and Tesla app-channel commands. */
    fun writeCommand(
        serviceUuid: UUID,
        charUuid: UUID,
        payload: ByteArray,
        logTag: String
    ): Boolean {
        val gatt = gatt ?: run {
            Log.w(logTag, "No GATT connection; cannot write command")
            return false
        }
        val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(charUuid) ?: run {
            Log.w(logTag, "Characteristic $charUuid not found; firmware may be older")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }
}
