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
    const val CMD_PROVISION: Int = 0x04

    /** VIN length per ISO 3779 (also enforced by the firmware). */
    const val VIN_LEN: Int = 17

    /** Ask the firmware to begin enrollment for the staged car. */
    fun sendStart(manager: DashKitBleManager): Boolean = sendTesla(manager, CMD_START)

    /** Factory-reset the Tesla key (erase -> the next sighting re-stages). */
    fun sendReset(manager: DashKitBleManager): Boolean = sendTesla(manager, CMD_RESET)

    /** Cancel an open pairing window. */
    fun sendCancel(manager: DashKitBleManager): Boolean = sendTesla(manager, CMD_CANCEL)

    /**
     * Stage the car the phone discovered (firmware TESLA_CMD_PROVISION).
     *
     * Payload: [opcode][17-byte VIN][address type][6 MAC bytes]. [mac] is the
     * advertised address as a human-readable string ("AA:BB:CC:DD:EE:FF"); it
     * is sent in NimBLE's raw ble_addr_t.val byte order — val[0] is the LAST
     * pair of the string, val[5] the first — which is what the firmware stores
     * and connects with. The address type is public (0x00), matching how
     * Tesla vehicles advertise.
     */
    fun sendProvision(manager: DashKitBleManager, vin: String, mac: String): Boolean {
        val v = vin.trim().uppercase()
        if (v.length != VIN_LEN) {
            Log.w(TAG, "provision: bad VIN length ${v.length}")
            return false
        }
        val octets = mac.split(":").mapNotNull { it.trim().toIntOrNull(16)?.toByte() }
        if (octets.size != 6) {
            Log.w(TAG, "provision: bad MAC \"$mac\"")
            return false
        }
        val payload = ByteArray(1 + VIN_LEN + 7)
        payload[0] = CMD_PROVISION.toByte()
        v.toByteArray(Charsets.US_ASCII).copyInto(payload, destinationOffset = 1)
        payload[18] = 0x00                                  // BLE_ADDR_PUBLIC
        for (i in 0..5) payload[19 + i] = octets[5 - i]     // reversed -> NimBLE order
        Log.d(TAG, "Sending provision for VIN $v")
        return writePayload(manager, payload)
    }

    /** Write an app-channel command to the DashKit (same [opcode][value_lo][value_hi]
     * framing and TIRAMISU branch as [VehicleControl.send], but on the new UUIDs).
     * Returns true if dispatched (not acknowledged).
     */
    fun sendTesla(manager: DashKitBleManager, opcode: Int): Boolean {
        val payload = byteArrayOf(
            (opcode and 0xFF).toByte(),
            0,
            0
        )
        Log.d(TAG, "Sending app-channel 0x%02X".format(opcode))
        return writePayload(manager, payload)
    }

    private fun writePayload(manager: DashKitBleManager, payload: ByteArray): Boolean {
        val gatt = manager.gatt ?: run {
            Log.w(TAG, "No GATT connection; cannot send app-channel write")
            return false
        }
        val service = gatt.getService(SERVICE_UUID) ?: run {
            Log.w(TAG, "App-channel service not found; firmware may be older")
            return false
        }
        val commandChar = service.getCharacteristic(COMMAND_CHAR_UUID) ?: run {
            Log.w(TAG, "App-channel command characteristic not found")
            return false
        }
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
