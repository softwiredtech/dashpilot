import Foundation
import CoreBluetooth
import Observation

enum OtaState: Equatable {
    case idle
    case connecting
    case uploading(Float)
    case rebooting
    case error(String)
}

/// BLE firmware upload to the DashKit's OTA service (port of the Android
/// `DashKitOtaUpdate`). Protocol:
///   1. enable notifications on the status characteristic (CADA0103),
///   2. write Begin [0x01][size LE32] to the control characteristic (CADA0101),
///   3. stream the binary in write-with-response chunks to CADA0102, paced by
///      the write acks,
///   4. status notifications report acks (0x01), completion/reboot (0x02) or a
///      firmware error (0xFF).
@Observable
final class DashKitOtaUpdate: DashKitGattListener {

    private(set) var state: OtaState = .idle

    @ObservationIgnored
    var onCompleted: (() -> Void)?

    private let manager: DashKitBleManager

    // Mutated only on the manager's BLE queue (listener callbacks) once the
    // upload is running.
    private var firmware: Data?
    private var firmwareOffset = 0
    private var peripheral: CBPeripheral?
    private var ctrlChar: CBCharacteristic?
    private var dataChar: CBCharacteristic?
    private var statusChar: CBCharacteristic?

    // The CAN stream is paused for the upload — its high-rate notifications
    // compete with the OTA writes for connection-event bandwidth. Resumed on
    // error/cancel; after a successful upload the DashKit reboots and the
    // data source re-subscribes on reconnect.
    private var canChar: CBCharacteristic?
    private var canWasNotifying = false

    private static let pingInterval: TimeInterval = 15
    private var controlChar: CBCharacteristic?
    private var lastPingAt = Date.distantPast
    private var pingInFlight = false

    // Set synchronously on the BLE queue when the device reports completion;
    // `state` is published on main so it can lag the disconnect that follows.
    private var rebooting = false
    private var uploadFinished = false

    init(manager: DashKitBleManager) {
        self.manager = manager
    }

    func start(firmware fw: Data) {
        guard !fw.isEmpty else {
            setState(.error("Firmware file is empty"))
            return
        }
        firmware = fw
        firmwareOffset = 0
        rebooting = false
        uploadFinished = false
        manager.suppressPings = true
        setState(.connecting)
        // If already connected the manager replays onServicesReady right away;
        // otherwise kick off a connection.
        manager.addGattListener(self)
        manager.connect()
    }

    func cancel() {
        manager.removeGattListener(self)
        manager.suppressPings = false
        rebooting = false
        uploadFinished = false
        resumeCanNotifications()
        firmware = nil
        peripheral = nil
        ctrlChar = nil
        dataChar = nil
        statusChar = nil
        canChar = nil
        controlChar = nil
        pingInFlight = false
        setState(.idle)
    }

    private func resumeCanNotifications() {
        if canWasNotifying, let peripheral, let canChar {
            peripheral.setNotifyValue(true, for: canChar)
        }
        canWasNotifying = false
    }

    private func setState(_ newState: OtaState) {
        DispatchQueue.main.async { self.state = newState }
    }

    // MARK: - DashKitGattListener (called on the manager's BLE queue)

    func onServicesReady(_ peripheral: CBPeripheral) {
        if rebooting {
            // The DashKit came back on the new firmware: the update is done.
            rebooting = false
            manager.removeGattListener(self)
            setState(.idle)
            onCompleted?()
            return
        }
        guard let service = peripheral.services?.first(where: { $0.uuid == DashKitGatt.otaService }) else {
            setState(.error("OTA service not found on device"))
            return
        }
        self.peripheral = peripheral
        ctrlChar = service.characteristics?.first { $0.uuid == DashKitGatt.otaControlCharacteristic }
        dataChar = service.characteristics?.first { $0.uuid == DashKitGatt.otaDataCharacteristic }
        statusChar = service.characteristics?.first { $0.uuid == DashKitGatt.otaStatusCharacteristic }
        guard let statusChar, ctrlChar != nil, dataChar != nil else {
            setState(.error("OTA characteristics not found"))
            return
        }
        let canService = peripheral.services?.first { $0.uuid == DashKitGatt.canService }
        canChar = canService?.characteristics?.first { $0.uuid == DashKitGatt.canCharacteristic }
        controlChar = canService?.characteristics?.first { $0.uuid == DashKitGatt.controlCharacteristic }
        if let canChar, canChar.isNotifying {
            canWasNotifying = true
            peripheral.setNotifyValue(false, for: canChar)
            print("[DashKitOta] paused CAN notifications for the upload")
        }
        peripheral.setNotifyValue(true, for: statusChar)
        print("[DashKitOta] OTA service ready, subscribing to status")
    }

    func onNotificationStateUpdated(_ characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == DashKitGatt.otaStatusCharacteristic else { return }
        if error != nil {
            setState(.error("Failed to enable OTA notifications"))
            resumeCanNotifications()
            return
        }
        sendBeginCommand()
    }

    func onCharacteristicChanged(_ characteristic: CBCharacteristic, value: Data) {
        guard characteristic.uuid == DashKitGatt.otaStatusCharacteristic else { return }
        handleStatusNotification(value)
    }

    func onCharacteristicWrite(_ characteristic: CBCharacteristic, error: Error?) {
        if characteristic.uuid == DashKitGatt.controlCharacteristic {
            guard pingInFlight else { return }
            pingInFlight = false
        } else if characteristic.uuid != DashKitGatt.otaControlCharacteristic,
                  characteristic.uuid != DashKitGatt.otaDataCharacteristic {
            return
        }
        if let error {
            setState(.error("Write failed (\(error.localizedDescription))"))
            resumeCanNotifications()
            return
        }
        if characteristic.uuid == DashKitGatt.otaDataCharacteristic,
           let fw = firmware, firmwareOffset >= fw.count {
            uploadFinished = true
        }
        sendNextChunk()
    }

    func onDisconnected() {
        // Rebooting expects this drop; the listener stays registered so the
        // reconnect's onServicesReady can clear the completed state.
        if uploadFinished, !rebooting {
            print("[DashKitOta] link dropped after the last chunk; treating as reboot")
            rebooting = true
            setState(.rebooting)
        } else if !rebooting, state != .idle {
            setState(.error("Disconnected unexpectedly"))
        }
        manager.suppressPings = false
        firmware = nil
        peripheral = nil
        ctrlChar = nil
        dataChar = nil
        statusChar = nil
        canChar = nil
        controlChar = nil
        canWasNotifying = false
        pingInFlight = false
    }

    // MARK: - Upload

    private func sendBeginCommand() {
        guard let fw = firmware, let peripheral, let ctrl = ctrlChar else { return }
        let size = fw.count
        let cmd = Data([
            0x01,
            UInt8(size & 0xFF),
            UInt8((size >> 8) & 0xFF),
            UInt8((size >> 16) & 0xFF),
            UInt8((size >> 24) & 0xFF)
        ])
        print("[DashKitOta] sending OTA Begin: \(size) bytes")
        setState(.uploading(0))
        firmwareOffset = 0
        lastPingAt = .distantPast
        pingInFlight = false
        peripheral.writeValue(cmd, for: ctrl, type: .withResponse)
    }

    private func sendNextChunk() {
        guard let fw = firmware, let peripheral, let data = dataChar else { return }
        guard firmwareOffset < fw.count else { return }

        if let controlChar, Date().timeIntervalSince(lastPingAt) >= Self.pingInterval {
            lastPingAt = Date()
            pingInFlight = true
            let ping = VehicleControl.payload(opcode: VehicleControl.cmdPing, value: 1)
            peripheral.writeValue(ping, for: controlChar, type: .withResponse)
            return
        }

        // .withoutResponse reports the true MTU-3 payload; .withResponse
        // reports 512, which turns each chunk into a slow ATT long write.
        let maxLen = peripheral.maximumWriteValueLength(for: .withoutResponse)
        let chunkSize = min(maxLen, fw.count - firmwareOffset)
        let chunk = fw.subdata(in: firmwareOffset..<(firmwareOffset + chunkSize))
        firmwareOffset += chunkSize

        setState(.uploading(Float(firmwareOffset) / Float(fw.count)))
        peripheral.writeValue(chunk, for: data, type: .withResponse)
    }

    private func handleStatusNotification(_ value: Data) {
        guard let first = value.first else { return }
        switch first {
        case 0x01:
            if value.count >= 5 {
                let received = UInt32(value[1])
                    | (UInt32(value[2]) << 8)
                    | (UInt32(value[3]) << 16)
                    | (UInt32(value[4]) << 24)
                print("[DashKitOta] device ack: \(received) bytes received")
            }
        case 0x02:
            print("[DashKitOta] OTA complete, device rebooting")
            rebooting = true
            setState(.rebooting)
            manager.suppressPings = false
            firmware = nil
        case 0xFF:
            let errCode = value.count > 1 ? value[1] : 0
            print("[DashKitOta] OTA error from device: 0x\(String(errCode, radix: 16))")
            setState(.error("Device reported error (0x\(String(errCode, radix: 16)))"))
            manager.removeGattListener(self)
            manager.suppressPings = false
            uploadFinished = false
            resumeCanNotifications()
            firmware = nil
        default:
            break
        }
    }
}
