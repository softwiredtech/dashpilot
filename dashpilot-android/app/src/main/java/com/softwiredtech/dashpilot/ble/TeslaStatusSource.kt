package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.datasource.GattListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Subscribes to the DashKit Tesla app-channel status indication (CADA0202) and
 * exposes the last-parsed [TeslaStatus] on a [StateFlow]. Mirrors
 * [DashKitOtaUpdate]'s listener pattern.
 *
 * The connection is shared with the CAN data source via [DashKitBleManager]; this
 * source only adds a second characteristic subscription and is removed when the
 * manager is torn down.
 */
@SuppressLint("MissingPermission")
class TeslaStatusSource(private val manager: DashKitBleManager) : GattListener {

    companion object {
        private const val TAG = "TeslaStatusSource"
    }

    private val _status = MutableStateFlow(TeslaStatus.Idle)
    val status: StateFlow<TeslaStatus> = _status.asStateFlow()

    private var statusChar: BluetoothGattCharacteristic? = null

    /** Register with the manager so we receive onServicesReady + notifications. */
    fun start() {
        manager.addGattListener(this)
        val g = manager.gatt
        if (g != null) setupStatusSubscription(g)
    }

    /** Unregister from the manager. */
    fun stop() {
        manager.removeGattListener(this)
    }

    override fun onServicesReady(gatt: BluetoothGatt) {
        setupStatusSubscription(gatt)
    }

    private fun setupStatusSubscription(gatt: BluetoothGatt) {
        val service = gatt.getService(TeslaClient.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(TeslaClient.STATUS_CHAR_UUID)
        if (characteristic == null) {
            Log.e(TAG, "App-channel/status characteristic not found; firmware may be older")
            return
        }
        statusChar = characteristic
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(TeslaClient.CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
        Log.d(TAG, "Subscribed to Tesla status notifications")
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        // After subscribing, read the current value so we don't wait for the
        // next change notification (e.g. during a long reconnect backoff).
        if (descriptor.characteristic?.uuid == TeslaClient.STATUS_CHAR_UUID &&
            status == BluetoothGatt.GATT_SUCCESS
        ) {
            val c = statusChar ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.readCharacteristic(c)
            } else {
                @Suppress("DEPRECATION")
                gatt.readCharacteristic(c)
            }
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS ||
            characteristic.uuid != TeslaClient.STATUS_CHAR_UUID
        ) return
        consume(value)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (characteristic.uuid != TeslaClient.STATUS_CHAR_UUID) return
        consume(value)
    }

    private fun consume(value: ByteArray) {
        val parsed = TeslaStatus.parse(value) ?: return
        if (parsed != _status.value) {
            Log.d(TAG, "Tesla status: $parsed")
            _status.value = parsed
        }
    }

    override fun onDisconnected() {
        // Keep the last value so the UI can still render "enrolled, not
        // connected" while the manager auto-reconnects; a re-subscribe happens
        // via onServicesReady after the next connect.
    }
}
