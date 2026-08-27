package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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

    private val _resetPending = MutableStateFlow(false)
    val resetPending: StateFlow<Boolean> = _resetPending.asStateFlow()

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

    fun requestReset(): Boolean {
        _resetPending.value = true
        if (TeslaClient.sendReset(manager)) return true
        _resetPending.value = false
        return false
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
        gatt.setCharacteristicNotification(characteristic, true)
        manager.subscribeWithRetry(gatt, characteristic, TAG, "Status")
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        // After subscribing, read the current value so we don't wait for the
        // next change notification (e.g. during a long reconnect backoff).
        val characteristic = descriptor.characteristic ?: return
        if (characteristic.uuid == TeslaClient.STATUS_CHAR_UUID &&
            status == BluetoothGatt.GATT_SUCCESS
        ) {
            gatt.readCharacteristic(characteristic)
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
        if (parsed.linkState == TeslaLinkState.NeverEnrolled ||
            parsed.linkState == TeslaLinkState.Staged
        ) {
            _resetPending.value = false
        }
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
