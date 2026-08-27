package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import java.util.UUID

@SuppressLint("MissingPermission")
class DashKitDataSource(
    private val manager: DashKitBleManager,
    private val decoder: CanFrameDecoder,
) : IDataSource, GattListener {

    companion object {
        private const val TAG = "DashKitDataSource"
        private val SERVICE_UUID = UUID.fromString("CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
        private val CHAR_UUID = UUID.fromString("CADA0001-CA00-B1E0-B0D6-C000AA0100A1")
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
        if (payload.isEmpty()) return
        for (frame in parseCanPacket(payload)) {
            currentState = decoder.decodeFrame(frame.bus, frame.address, frame.data)
        }
        _incoming.tryEmit(currentState)
    }
}
