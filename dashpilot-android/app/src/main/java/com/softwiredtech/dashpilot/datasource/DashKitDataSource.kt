package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
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
        private val FILTER_CHAR_UUID = UUID.fromString("CADA0002-CA00-B1E0-B0D6-C000AA0100A1")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Whitelist of CAN frames the firmware should forward over BLE.
        private val MAPPER_MESSAGE_IDS: List<Int> = listOf(
            0x129, // SCCM_steeringAngleSensor   (297)
            0x118, // DI_systemStatus            (280)
            0x257, // DI_speed                   (599)
            0x399, // DAS_status                 (921)
            0x3E2, // VCLEFT_lightStatus         (994)
            0x3E3, // VCRIGHT_lightStatus        (995)
            0x352, // BMS_energyStatus           (850)
            0x252, // BMS_powerAvailable         (594)
            0x132, // BMS_hvBusStatus            (306)
            0x332, // BMS_bmbMinMax              (818)
            0x3B6, // DI_odometerStatus          (950)
            0x2F3, // UI_hvacRequest             (755)
        )

        private val CAN_FILTER: Map<Int, List<Int>> = mapOf(
            0 to MAPPER_MESSAGE_IDS, // Chassis bus
            1 to MAPPER_MESSAGE_IDS, // Vehicle bus
        )

        // Encode the filter map into the DashKit wire format:
        //   [bus][num_addrs][addr_LE32]*  repeated per bus.
        // Buses with an empty address list are skipped.
        internal fun encodeFilter(filter: Map<Int, List<Int>>): ByteArray {
            val nonEmpty = filter.filterValues { it.isNotEmpty() }
            val size = nonEmpty.entries.sumOf { 2 + it.value.size * 4 }
            val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            for ((bus, addrs) in nonEmpty) {
                buf.put(bus.toByte())
                buf.put(addrs.size.toByte())
                for (a in addrs) buf.putInt(a)
            }
            return buf.array()
        }
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
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
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
        Log.d(TAG, "Subscribed to CAN notifications")
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        // After the CAN notification CCCD write completes, push the filter
        // to the firmware. Done here (rather than back-to-back in
        // onServicesReady) because Android only allows one GATT op at a time.
        if (descriptor.characteristic?.uuid == CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
            writeFilter(gatt)
        }
    }

    private fun writeFilter(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID) ?: return
        val filterChar = service.getCharacteristic(FILTER_CHAR_UUID)
        if (filterChar == null) {
            Log.w(TAG, "Filter characteristic not found; firmware may be older — skipping")
            return
        }
        val payload = encodeFilter(CAN_FILTER)
        Log.d(TAG, "Writing CAN filter (${payload.size} bytes)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                filterChar,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            filterChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            filterChar.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(filterChar)
        }
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
