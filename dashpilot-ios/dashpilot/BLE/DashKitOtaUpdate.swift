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

    private let manager: DashKitBleManager

    // Mutated only on the manager's BLE queue (listener callbacks) once the
    // upload is running.
    private var firmware: Data?
    private var firmwareOffset = 0
    private var peripheral: CBPeripheral?
    private var ctrlChar: CBCharacteristic?
    private var dataChar: CBCharacteristic?
    private var statusChar: CBCharacteristic?

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
        setState(.connecting)
        // If already connected the manager replays onServicesReady right away;
        // otherwise kick off a connection.
        manager.addGattListener(self)
        manager.connect()
    }

    func cancel() {
        manager.removeGattListener(self)
        firmware = nil
        peripheral = nil
        ctrlChar = nil
        dataChar = nil
        statusChar = nil
        setState(.idle)
    }

    private func setState(_ newState: OtaState) {
        DispatchQueue.main.async { self.state = newState }
    }

    // MARK: - DashKitGattListener (called on the manager's BLE queue)

    func onServicesReady(_ peripheral: CBPeripheral) {
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
        peripheral.setNotifyValue(true, for: statusChar)
        print("[DashKitOta] OTA service ready, subscribing to status")
    }

    func onNotificationStateUpdated(_ characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == DashKitGatt.otaStatusCharacteristic else { return }
        if error != nil {
            setState(.error("Failed to enable OTA notifications"))
            return
        }
        sendBeginCommand()
    }

    func onCharacteristicChanged(_ characteristic: CBCharacteristic, value: Data) {
        guard characteristic.uuid == DashKitGatt.otaStatusCharacteristic else { return }
        handleStatusNotification(value)
    }

    func onCharacteristicWrite(_ characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == DashKitGatt.otaControlCharacteristic ||
              characteristic.uuid == DashKitGatt.otaDataCharacteristic else { return }
        if let error {
            setState(.error("Write failed (\(error.localizedDescription))"))
            return
        }
        sendNextChunk()
    }

    func onDisconnected() {
        switch state {
        case .rebooting, .idle:
            break
        default:
            setState(.error("Disconnected unexpectedly"))
        }
        firmware = nil
        peripheral = nil
        ctrlChar = nil
        dataChar = nil
        statusChar = nil
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
        peripheral.writeValue(cmd, for: ctrl, type: .withResponse)
    }

    private func sendNextChunk() {
        guard let fw = firmware, let peripheral, let data = dataChar else { return }
        guard firmwareOffset < fw.count else { return }

        let maxLen = peripheral.maximumWriteValueLength(for: .withResponse)
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
            setState(.rebooting)
            manager.removeGattListener(self)
            firmware = nil
        case 0xFF:
            let errCode = value.count > 1 ? value[1] : 0
            print("[DashKitOta] OTA error from device: 0x\(String(errCode, radix: 16))")
            setState(.error("Device reported error (0x\(String(errCode, radix: 16)))"))
            manager.removeGattListener(self)
            firmware = nil
        default:
            break
        }
    }
}
