import Foundation
import CoreBluetooth

/// Sends high-level Tesla vehicle-control commands to the DashKit over BLE.
///
/// The firmware exposes a control characteristic (CADA0004) that accepts a
/// small [opcode][value_lo][value_hi] packet and bit-packs the matching Tesla
/// DBC signal into a CAN frame. Opcodes mirror `vehicle_control.h` in the
/// firmware and `VehicleControl.kt` in the Android app.
enum VehicleControl {

    // --- UI_vehicleControl (0x273) ---
    static let cmdClosure = 0x02  // UI_remoteClosureRequest: 1=REAR_TRUNK 2=FRONT_TRUNK
    static let closureRearTrunk = 1
    static let closureFrontTrunk = 2

    static let cmdMirrorFold = 0x04  // UI_mirrorFoldRequest: 1=RETRACT(fold) 2=PRESENT(unfold)
    static let mirrorFold = 1
    static let mirrorUnfold = 2

    // --- UI_vehicleControl2 (0x3B3) ---
    static let cmdGlovebox = 0x0F              // 1=open (UI_gloveboxRequest)

    // --- Battery preheat (firmware fakes UI_tripPlanning 0x082) ---
    static let cmdBatteryPreheat = 0x35        // 1=start injecting preheat, 0=stop

    // --- UI_chargeRequest (0x333) ---
    static let cmdChargePortOpen = 0x0D   // 1=open
    static let cmdChargePortClose = 0x0E  // 1=close

    // --- Infotainment multi-finger tap binding ---
    // Tells the firmware which action to run when it sees an N-finger tap on
    // UI_status2.UI_activeTouchPoints. The value packs the finger count (3..5)
    // in the high byte and the action code in the low byte. Action codes are
    // the gestureValue each control carries in VehicleControls.swift (the
    // source of truth); 0 means "unbound".
    static let cmdMultiFingerAction = 0x40
    static let gestureActionNone = 0

    // --- Auto wiper-off automation toggle ---
    // Arms/disarms the firmware's auto wiper-off automation. 1=enable,
    // 0=disable. The firmware persists it in NVS (wiper_off module).
    static let cmdWiperOffEnable = 0x41

    // --- Enter BLE pairing mode ---
    // Opens a window on the firmware during which one new (not-yet-paired)
    // device may pair. Only an already-paired phone can send it (the control
    // char requires an encrypted link). Value is ignored by the firmware.
    static let cmdEnterPairing = 0x42

    // --- Rear AC fan toggle (UI_hvacRequest 0x2F3) ---
    // The firmware reads the live UI_hvacReqSecondRowState and flips it
    // OFF <-> HIGH, so this is a single momentary command (value ignored).
    static let cmdRearFanToggle = 0x43

    // --- Reboot the DashKit ---
    // Restarts the ESP32 (esp_restart). Value is ignored by the firmware.
    static let cmdReboot = 0x44

    // --- Keepalive ping ---
    // Sent every ~15 s while connected and foregrounded; after the first ping
    // the firmware drops the link after 60 s of silence. Value is ignored.
    static let cmdPing = 0x45

    /// Bind (or clear, with actionValue 0) an N-finger tap to a control action.
    @discardableResult
    static func sendFingerAction(_ manager: DashKitBleManager, fingers: Int, actionValue: Int) -> Bool {
        send(manager, opcode: cmdMultiFingerAction, value: (fingers << 8) | (actionValue & 0xFF))
    }

    /// Enable or disable the firmware's auto wiper-off automation.
    @discardableResult
    static func sendWiperOff(_ manager: DashKitBleManager, enabled: Bool) -> Bool {
        send(manager, opcode: cmdWiperOffEnable, value: enabled ? 1 : 0)
    }

    /// Ask the DashKit to open a pairing window for one new device.
    @discardableResult
    static func sendEnterPairing(_ manager: DashKitBleManager) -> Bool {
        let ok = send(manager, opcode: cmdEnterPairing, value: 1)
        // DashKit drops this phone to free its single connection slot for the
        // new device; tell the manager so it doesn't auto-reconnect and steal
        // the slot back during the window.
        if ok { manager.expectPairingWindowDrop() }
        return ok
    }

    /// Toggle the rear AC fan (firmware flips OFF <-> HIGH; value ignored).
    @discardableResult
    static func sendRearFanToggle(_ manager: DashKitBleManager) -> Bool {
        send(manager, opcode: cmdRearFanToggle, value: 1)
    }

    /// Ask the DashKit to reboot (firmware calls esp_restart; value ignored).
    @discardableResult
    static func sendReboot(_ manager: DashKitBleManager) -> Bool {
        send(manager, opcode: cmdReboot, value: 1)
    }

    /// Keepalive ping so the firmware knows this app is still alive.
    @discardableResult
    static func sendPing(_ manager: DashKitBleManager) -> Bool {
        send(manager, opcode: cmdPing, value: 1)
    }

    /// Write a control command to the DashKit. Returns true if the write was
    /// dispatched (not necessarily acknowledged). No-op returning false when
    /// the link is down or the control characteristic is unavailable.
    @discardableResult
    static func send(_ manager: DashKitBleManager, opcode: Int, value: Int) -> Bool {
        guard let characteristic = manager.characteristic(
            service: DashKitGatt.canService,
            characteristic: DashKitGatt.controlCharacteristic
        ), let peripheral = manager.peripheral else {
            print("[VehicleControl] no connection; cannot send command 0x\(String(opcode, radix: 16))")
            return false
        }
        let v = value & 0xFFFF
        let payload = Data([
            UInt8(opcode & 0xFF),
            UInt8(v & 0xFF),
            UInt8((v >> 8) & 0xFF)
        ])
        print("[VehicleControl] sending control 0x\(String(opcode, radix: 16)) value=\(v)")
        peripheral.writeValue(payload, for: characteristic, type: .withResponse)
        return true
    }
}
