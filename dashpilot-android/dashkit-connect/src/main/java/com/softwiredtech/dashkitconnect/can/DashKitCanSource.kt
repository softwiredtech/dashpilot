package com.softwiredtech.dashkitconnect.can

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.softwiredtech.dashkitconnect.DashKitBleManager
import com.softwiredtech.dashkitconnect.GattListener
import java.util.UUID

/**
 * Subscribes to the DashKit CAN stream characteristic (CADA0001) and delivers
 * each notification as a batch of raw [RawCanFrame]s to [onFrames]. Decoding
 * the frames (DBC lookup, signal extraction) is left to the caller.
 *
 * [onFrames] is invoked on the Bluetooth binder thread; keep it cheap or hand
 * the batch off to your own thread.
 */
@SuppressLint("MissingPermission")
class DashKitCanSource(
    private val manager: DashKitBleManager,
    private val onFrames: (List<RawCanFrame>) -> Unit,
) : GattListener {

    companion object {
        private const val TAG = "DashKitCanSource"
        val SERVICE_UUID: UUID = UUID.fromString("CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
        val STREAM_CHAR_UUID: UUID = UUID.fromString("CADA0001-CA00-B1E0-B0D6-C000AA0100A1")
    }

    /** Register with the manager; the subscription is (re)established on every
     * services-ready event, so this survives auto-reconnects. */
    fun start() {
        manager.addGattListener(this)
    }

    fun stop() {
        manager.removeGattListener(this)
    }

    override fun onServicesReady(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "CAN BLE service not found")
            return
        }
        val characteristic = service.getCharacteristic(STREAM_CHAR_UUID)
        if (characteristic == null) {
            Log.e(TAG, "CAN BLE characteristic not found")
            return
        }
        gatt.setCharacteristicNotification(characteristic, true)
        manager.subscribeWithRetry(gatt, characteristic, TAG, "CAN")
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (characteristic.uuid != STREAM_CHAR_UUID) return
        if (value.isEmpty()) return
        val frames = parseCanPacket(value)
        if (frames.isNotEmpty()) onFrames(frames)
    }
}
