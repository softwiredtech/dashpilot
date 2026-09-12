package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.datasource.GattListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Reads the read-only firmware version characteristic (CADA0005) the DashKit
 * exposes on its main CAN service. The value is the ASCII string embedded by
 * ESP-IDF in the app descriptor (e.g. "0.0.1").
 */
@SuppressLint("MissingPermission")
class DashKitFirmwareVersion(
    private val manager: DashKitBleManager
) : GattListener {

    companion object {
        private const val TAG = "DashKitFwVersion"
        private val SERVICE_UUID = UUID.fromString("CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
        private val VERSION_UUID = UUID.fromString("CADA0005-CA00-B1E0-B0D6-C000AA0100A1")

        // Android allows one GATT operation in flight; right after services
        // are ready the CAN and Tesla subscriptions are writing their CCCDs,
        // so the read is refused and must be retried.
        private const val MAX_READ_ATTEMPTS = 8
        private const val READ_RETRY_DELAY_MS = 250L
    }

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    /** Register for callbacks and, if connected, trigger the read now. */
    fun read() {
        if (!registered) {
            manager.addGattListener(this)
            registered = true
        }
        val g = manager.gatt
        if (g != null && manager.connectionState.value == ConnectionStatus.Connected) {
            requestRead(g)
        }
    }

    override fun onServicesReady(gatt: BluetoothGatt) {
        requestRead(gatt)
    }

    private fun requestRead(g: BluetoothGatt, attempt: Int = 0) {
        handler.removeCallbacksAndMessages(null)
        if (manager.gatt !== g) return
        val ch = g.getService(SERVICE_UUID)?.getCharacteristic(VERSION_UUID)
        if (ch == null) {
            Log.w(TAG, "Version characteristic not found")
            return
        }
        @Suppress("DEPRECATION")
        val ok = g.readCharacteristic(ch)
        if (ok) return
        if (attempt < MAX_READ_ATTEMPTS) {
            Log.d(TAG, "Version read not queued (attempt=${attempt + 1}); retrying")
            handler.postDelayed({ requestRead(g, attempt + 1) }, READ_RETRY_DELAY_MS)
        } else {
            Log.w(TAG, "Version read gave up after $MAX_READ_ATTEMPTS attempts")
        }
    }

    // The link is gone (typically the reboot after an OTA), so the last
    // value no longer describes what is running; the next connect re-reads it.
    override fun onDisconnected() {
        handler.removeCallbacksAndMessages(null)
        _version.value = null
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (characteristic.uuid != VERSION_UUID) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "Version read failed: status $status")
            return
        }
        val v = value.toString(Charsets.UTF_8).trim().trimEnd('\u0000')
        Log.d(TAG, "Firmware version: $v")
        _version.value = v
    }

    /** Suspend until a version is available or [timeoutMs] elapses. */
    suspend fun await(timeoutMs: Long = 5000): String? =
        withTimeoutOrNull(timeoutMs) {
            version.first { it != null }
        }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        manager.removeGattListener(this)
        registered = false
    }
}
