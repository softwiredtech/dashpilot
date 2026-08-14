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

    const val CMD_MIRROR_FOLD: Int = 0x04  // UI_mirrorFoldRequest: 1=RETRACT(fold) 2=PRESENT(unfold)
    const val MIRROR_FOLD: Int = 1
    const val MIRROR_UNFOLD: Int = 2

    // --- UI_vehicleControl2 (0x3B3) ---
    const val CMD_GLOVEBOX: Int = 0x0F              // 1=open (UI_gloveboxRequest)

    // --- Battery preheat (firmware fakes UI_tripPlanning 0x082) ---
    const val CMD_BATTERY_PREHEAT: Int = 0x35       // 1=start injecting preheat, 0=stop

    // --- UI_chargeRequest (0x333) ---
    const val CMD_CHARGE_PORT_OPEN: Int = 0x0D   // 1=open
    const val CMD_CHARGE_PORT_CLOSE: Int = 0x0E  // 1=close

    // --- Infotainment multi-finger tap binding ---
    // Tells the firmware which action to run when it sees an N-finger tap on
    // UI_status2.UI_activeTouchPoints. The value packs the finger count (3..5) in
    // the high byte and the action code in the low byte. Action codes are the
    // gestureValue each control carries in VehicleControls.kt (the source of
    // truth); 0 means "unbound".
    const val CMD_MULTI_FINGER_ACTION: Int = 0x40
    const val GESTURE_ACTION_NONE: Int = 0

    // --- Auto wiper-off automation toggle ---
    // Arms/disarms the firmware's auto wiper-off automation. 1=enable, 0=disable.
    // The firmware persists it in NVS (wiper_off module).
    const val CMD_WIPER_OFF_ENABLE: Int = 0x41

    // --- Enter BLE pairing mode ---
    // Opens a window on the firmware during which one new (not-yet-bonded) device
    // may pair. Only this already-paired phone can send it (the control char
    // requires an encrypted link). Value is ignored by the firmware.
    const val CMD_ENTER_PAIRING: Int = 0x42

    // --- Rear AC fan toggle (UI_hvacRequest 0x2F3) ---
    // The firmware reads the live UI_hvacReqSecondRowState and flips it OFF <->
    // HIGH, so this is a single momentary command with no value (value ignored).
    const val CMD_REAR_FAN_TOGGLE: Int = 0x43

    // --- Reboot the DashKit ---
    // Restarts the ESP32 (esp_restart). Value is ignored by the firmware.
    const val CMD_REBOOT: Int = 0x44

    // --- Keepalive ping ---
    // Written every ~15 s while connected; once the firmware has seen a first
    // ping it drops the link after 60 s of silence, so a wedged app can't hold
    // DashKit's only connection slot. Value is ignored by the firmware.
    const val CMD_PING: Int = 0x45

    /** Bind (or clear, with actionValue 0) an N-finger tap to a control action. */
    fun sendFingerAction(manager: DashKitBleManager, fingers: Int, actionValue: Int): Boolean =
        send(manager, CMD_MULTI_FINGER_ACTION, (fingers shl 8) or (actionValue and 0xFF))

    /** Enable or disable the firmware's auto wiper-off automation. */
    fun sendWiperOff(manager: DashKitBleManager, enabled: Boolean): Boolean =
        send(manager, CMD_WIPER_OFF_ENABLE, if (enabled) 1 else 0)

    /** Ask the DashKit to open a pairing window for one new device. */
    fun sendEnterPairing(manager: DashKitBleManager): Boolean {
        val ok = send(manager, CMD_ENTER_PAIRING, 1)
        // DashKit drops this phone to free its single connection slot for the
        // new device; tell the manager so it doesn't auto-reconnect and steal
        // the slot back during the window.
        if (ok) manager.expectPairingWindowDrop()
        return ok
    }

    /** Toggle the rear AC fan (firmware flips OFF <-> HIGH; value ignored). */
    fun sendRearFanToggle(manager: DashKitBleManager): Boolean =
        send(manager, CMD_REAR_FAN_TOGGLE, 1)

    /** Ask the DashKit to reboot (firmware calls esp_restart; value ignored). */
    fun sendReboot(manager: DashKitBleManager): Boolean =
        send(manager, CMD_REBOOT, 1)

    /** Keepalive ping so the firmware knows this app is still alive. */
    fun sendPing(manager: DashKitBleManager): Boolean =
        send(manager, CMD_PING, 1)

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
