package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample

@SuppressLint("MissingPermission")
class DashKitDataSource(
    private val manager: DashKitBleManager,
    private val decoder: CanFrameDecoder
) : IDataSource, GattListener {

    companion object {
        private const val TAG = "DashKitDataSource"
        private val SERVICE_UUID = UUID.fromString("CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
        private val CHAR_UUID = UUID.fromString("CADA0001-CA00-B1E0-B0D6-C000AA0100A1")
        // The firmware now applies CAN acceptance filtering in hardware (per-bus
        // MCP251xFD filters), so the app no longer pushes a BLE filter list.
    }

    private val _incoming = MutableSharedFlow<CarState>(replay = 1)
    @OptIn(FlowPreview::class)
    override val incomingMessages: Flow<CarState> = _incoming.sample(40)

    private var currentState = CarState()

    override fun connect(address: String) {
        manager.addGattListener(this)
        manager.connect()
    }

    override fun disconnect() {
        manager.removeGattListener(this)
    }

    override fun onServicesReady(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "CAN BLE service not found")
            return
        }
        val characteristic = service.getCharacteristic(CHAR_UUID)
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
        if (characteristic.uuid != CHAR_UUID) return
        parseAndEmit(value)
    }

    private fun parseAndEmit(payload: ByteArray) {
        // Wire format from firmware (build_ble_packet):
        //   [count : 1]
        //   per frame:
        //     [timestamp_us : LE32]
        //     [bus          : 1]
        //     [addr         : LE32]
        //     [len          : 1]
        //     [data         : len bytes]
        if (payload.isEmpty()) return
        val count = payload[0].toInt() and 0xFF
        var offset = 1
        for (i in 0 until count) {
            if (offset + 4 > payload.size) break
            // Timestamp is currently unused by the decoder; skip it.
            offset += 4
            if (offset >= payload.size) break
            val bus = payload[offset].toInt() and 0xFF
            offset += 1
            if (offset + 4 > payload.size) break
            val addr = ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 4
            if (offset >= payload.size) break
            val len = payload[offset].toInt() and 0xFF
            offset += 1
            if (offset + len > payload.size) break
            val data = payload.copyOfRange(offset, offset + len)
            offset += len

            currentState = decoder.decodeFrame(bus, addr, data)
        }
        _incoming.tryEmit(currentState)
    }
}
