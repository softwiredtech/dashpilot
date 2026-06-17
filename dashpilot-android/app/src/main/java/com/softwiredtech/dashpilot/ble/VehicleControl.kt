package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import android.util.Log
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import java.util.UUID

/**
 * Sends high-level Tesla vehicle-control commands to the DashKit over BLE.
 *
 * The firmware exposes a control characteristic (CADA0004) that accepts a small
 * [opcode][value_lo][value_hi] packet and bit-packs the matching Tesla DBC
 * signal into a CAN frame. Opcodes mirror `vehicle_control.h` in the firmware.
 */
@SuppressLint("MissingPermission")
object VehicleControl {

    private const val TAG = "VehicleControl"

    private val SERVICE_UUID = UUID.fromString("CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
    private val CONTROL_CHAR_UUID = UUID.fromString("CADA0004-CA00-B1E0-B0D6-C000AA0100A1")

    // --- UI_vehicleControl (0x273) ---
    const val CMD_CLOSURE: Int = 0x02  // UI_remoteClosureRequest: 1=REAR_TRUNK 2=FRONT_TRUNK
    const val CLOSURE_REAR_TRUNK: Int = 1
    const val CLOSURE_FRONT_TRUNK: Int = 2

    // --- UI_vehicleControl2 (0x3B3) ---
    const val CMD_GLOVEBOX: Int = 0x0F              // 1=open (UI_gloveboxRequest)

    // --- Battery preheat (firmware fakes UI_tripPlanning 0x082) ---
    const val CMD_BATTERY_PREHEAT: Int = 0x35       // 1=start injecting preheat, 0=stop

    // --- UI_chargeRequest (0x333) ---
    const val CMD_CHARGE_PORT_OPEN: Int = 0x0D   // 1=open
    const val CMD_CHARGE_PORT_CLOSE: Int = 0x0E  // 1=close

    /**
     * Write a control command to the DashKit. Returns true if the write was
     * dispatched (not necessarily acknowledged). No-op returning false if the
     * link is down or the control characteristic is unavailable.
     */
    fun send(manager: DashKitBleManager, opcode: Int, value: Int): Boolean {
        val gatt = manager.gatt
        if (gatt == null) {
            Log.w(TAG, "No GATT connection; cannot send command 0x%02X".format(opcode))
            return false
        }
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.w(TAG, "Control service not found")
            return false
        }
        val controlChar = service.getCharacteristic(CONTROL_CHAR_UUID)
        if (controlChar == null) {
            Log.w(TAG, "Control characteristic not found; firmware may be older")
            return false
        }

        val v = value and 0xFFFF
        val payload = byteArrayOf(
            (opcode and 0xFF).toByte(),
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte()
        )

        Log.d(TAG, "Sending control 0x%02X value=%d".format(opcode, v))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                controlChar,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            controlChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            controlChar.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(controlChar)
        }
    }
}
