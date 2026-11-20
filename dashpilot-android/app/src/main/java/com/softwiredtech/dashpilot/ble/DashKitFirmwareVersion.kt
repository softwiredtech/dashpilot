package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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
    }

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version

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

    // Android allows one GATT operation in flight, and right after services
    // are ready the CAN and Tesla subscriptions are writing their CCCDs. The
    // read goes through the manager's operation queue and waits its turn.
    private fun requestRead(g: BluetoothGatt) {
        val ch = g.getService(SERVICE_UUID)?.getCharacteristic(VERSION_UUID)
        if (ch == null) {
            Log.w(TAG, "Version characteristic not found")
            return
        }
        if (!manager.readCharacteristic(g, ch, TAG)) {
            Log.d(TAG, "Version read skipped: the link was replaced")
        }
    }

    // The link is gone (typically the reboot after an OTA), so the last
    // value no longer describes what is running; the next connect re-reads it.
    override fun onDisconnected() {
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
        manager.removeGattListener(this)
        registered = false
    }
}
