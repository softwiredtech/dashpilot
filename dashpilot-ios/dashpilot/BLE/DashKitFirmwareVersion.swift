import Foundation
import CoreBluetooth
import Observation

/// Reads the read-only firmware version characteristic (CADA0005) the DashKit
/// exposes on its main CAN service. The value is the ASCII string embedded by
/// ESP-IDF in the app descriptor (e.g. "0.0.1").
@Observable
final class DashKitFirmwareVersion: DashKitGattListener {

    private(set) var version: String?

    private let manager: DashKitBleManager

    init(manager: DashKitBleManager) {
        self.manager = manager
    }

    /// Register for callbacks; if already connected the manager immediately
    /// replays onServicesReady, which triggers the read.
    func read() {
        manager.addGattListener(self)
    }

    func onServicesReady(_ peripheral: CBPeripheral) {
        guard let characteristic = peripheral.services?
            .first(where: { $0.uuid == DashKitGatt.canService })?
            .characteristics?
            .first(where: { $0.uuid == DashKitGatt.versionCharacteristic }) else {
            print("[DashKitFirmwareVersion] version characteristic not found")
            return
        }
        peripheral.readValue(for: characteristic)
    }

    func onCharacteristicRead(_ characteristic: CBCharacteristic, value: Data?, error: Error?) {
        guard characteristic.uuid == DashKitGatt.versionCharacteristic else { return }
        if let error {
            print("[DashKitFirmwareVersion] version read failed: \(error.localizedDescription)")
            return
        }
        guard let value else { return }
        let v = String(decoding: value, as: UTF8.self)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\0"))
        print("[DashKitFirmwareVersion] firmware version: \(v)")
        DispatchQueue.main.async { self.version = v }
    }

    /// Suspend until a version is available or `timeout` elapses.
    func awaitVersion(timeout: TimeInterval = 5) async -> String? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let version { return version }
            try? await Task.sleep(for: .milliseconds(100))
        }
        return version
    }

    func dispose() {
        manager.removeGattListener(self)
    }
}
