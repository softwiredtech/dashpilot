package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import android.util.Log
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import java.util.UUID

/**
 * Sends DashKit *app-channel* commands over the Tesla service (CADA0200) — the
 * phone-side half of the Phase 4 pairing/reset UX.
 *
 * This is intentionally separate from [VehicleControl] (which is hardcoded to
 * the CAN control characteristic CADA0004): the app-channel writes go to
 * CADA0201 with the same 3-byte [opcode][value_lo][value_hi] framing, and carry
 * only pairing lifecycle opcodes:
 *   CMD_START (0x01) - begin enrollment (the ONLY trigger, app-triggered-only)
 *   CMD_RESET (0x02) - factory-reset the Tesla key (erase -> re-stage)
 *   CMD_CANCEL(0x03) - cancel an open pairing window
 *
 * Enrollment is app-triggered only: the firmware never starts pairing on its
 * own, because the DashKit sits in the car trim and its LEDs aren't visible.
 */
@SuppressLint("MissingPermission")
object TeslaClient {

    private const val TAG = "TeslaClient"

    // App-channel service + characteristics (CADA02xx)
    val SERVICE_UUID = UUID.fromString("CADA0200-CA00-B1E0-B0D6-C000AA0100A1")
    val COMMAND_CHAR_UUID = UUID.fromString("CADA0201-CA00-B1E0-B0D6-C000AA0100A1")
    val STATUS_CHAR_UUID = UUID.fromString("CADA0202-CA00-B1E0-B0D6-C000AA0100A1")
    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Command opcodes (match TESLA_CMD_* in main/ble/ble_appchan.h)
    const val CMD_START: Int = 0x01
    const val CMD_RESET: Int = 0x02
    const val CMD_CANCEL: Int = 0x03

    /** Ask the firmware to begin enrollment for the staged car. */
    fun sendStart(manager: DashKitBleManager): Boolean = sendTesla(manager, CMD_START, 0)

    /** Factory-reset the Tesla key (erase -> the next sighting re-stages). */
    fun sendReset(manager: DashKitBleManager): Boolean = sendTesla(manager, CMD_RESET, 0)

    /** Cancel an open pairing window. */
    fun sendCancel(manager: DashKitBleManager): Boolean = sendTesla(manager, CMD_CANCEL, 0)

    /**
     * Write an app-channel command to the DashKit (same [opcode][value_lo][value_hi]
     * framing and TIRAMISU branch as [VehicleControl.send], but on the new UUIDs).
     * Returns true if dispatched (not acknowledged).
     */
    fun sendTesla(manager: DashKitBleManager, opcode: Int, value: Int): Boolean {
        val gatt = manager.gatt
        if (gatt == null) {
            Log.w(TAG, "No GATT connection; cannot send Tesla command 0x%02X".format(opcode))
            return false
        }
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.w(TAG, "App-channel service not found; firmware may be older")
            return false
        }
        val commandChar = service.getCharacteristic(COMMAND_CHAR_UUID)
        if (commandChar == null) {
            Log.w(TAG, "App-channel command characteristic not found")
            return false
        }

        val v = value and 0xFFFF
        val payload = byteArrayOf(
            (opcode and 0xFF).toByte(),
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte()
        )

        Log.d(TAG, "Sending app-channel 0x%02X value=%d".format(opcode, v))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                commandChar,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            commandChar.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(commandChar)
        }
    }
}
