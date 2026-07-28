import Foundation
import CoreBluetooth

/// Streams `CarState` decoded from the raw CAN frames DashKit notifies over
/// BLE. Port of the Android `DashKitDataSource`: subscribes to the CAN
/// characteristic once services are ready and feeds each frame through the
/// native Tesla DBC decoder.
final class DashKitDataSource: IDataSource {

    /// Emit at most one state per interval — the firmware notifies far faster
    /// than the UI needs (Android samples the same stream at 40 ms).
    private static let emitInterval: TimeInterval = 0.04

    private let manager: DashKitBleManager

    private var continuation: AsyncStream<CarState>.Continuation?
    let incomingMessages: AsyncStream<CarState>

    /// Native VehicleDecoder handle; created on connect, freed on disconnect.
    /// Guarded by `decoderLock`: decode runs on the manager's BLE queue while
    /// disconnect() may destroy the decoder from the main thread.
    private var decoder: UnsafeMutableRawPointer?
    private let decoderLock = NSLock()
    private var lastEmit: TimeInterval = 0

    init(manager: DashKitBleManager) {
        self.manager = manager
        var capturedContinuation: AsyncStream<CarState>.Continuation?
        incomingMessages = AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            capturedContinuation = continuation
        }
        self.continuation = capturedContinuation
    }

    /// `address` is unused — DashKit is found by BLE scan, not by IP.
    func connect(address: String) {
        // Both DashKit buses carry the vehicle bus DBC (config_dashkit.json on
        // Android); the "dashkit" mapper reads buses 0 and 1.
        var dbcContents: [String] = []
        var busIndices: [CInt] = []
        for bus in [0, 1] {
            guard let url = Bundle.main.url(forResource: "bus_1_tesla_vehicle", withExtension: "dbc"),
                  let content = try? String(contentsOf: url) else {
                print("[DashKitDataSource] failed to load bus_1_tesla_vehicle.dbc")
                continue
            }
            dbcContents.append(content)
            busIndices.append(CInt(bus))
        }
        decoder = dbcContents.withCStringArray { dbcPtrs in
            busIndices.withUnsafeBufferPointer { busBuf in
                bridge_create_vehicle_decoder(dbcPtrs, busBuf.baseAddress, Int32(dbcContents.count), "dashkit")
            }
        }

        manager.addGattListener(self)
        manager.connect()
    }

    func disconnect() {
        manager.removeGattListener(self)
        continuation?.finish()
        decoderLock.lock()
        if let decoder {
            bridge_destroy_vehicle_decoder(decoder)
            self.decoder = nil
        }
        decoderLock.unlock()
    }

    // Wire format from firmware (build_ble_packet):
    //   [count : 1]
    //   per frame:
    //     [timestamp_us : LE32]
    //     [bus          : 1]
    //     [addr         : LE32]
    //     [len          : 1]
    //     [data         : len bytes]
    private func parseAndEmit(_ payload: Data) {
        decoderLock.lock()
        defer { decoderLock.unlock() }
        guard let decoder, !payload.isEmpty else { return }
        let bytes = [UInt8](payload)
        let count = Int(bytes[0])
        var offset = 1
        var state = BridgeCarState()
        var decodedAny = false
        for _ in 0..<count {
            guard offset + 4 <= bytes.count else { break }
            // Timestamp is currently unused by the decoder; skip it.
            offset += 4
            guard offset < bytes.count else { break }
            let bus = Int(bytes[offset])
            offset += 1
            guard offset + 4 <= bytes.count else { break }
            let addr = UInt32(bytes[offset])
                | (UInt32(bytes[offset + 1]) << 8)
                | (UInt32(bytes[offset + 2]) << 16)
                | (UInt32(bytes[offset + 3]) << 24)
            offset += 4
            guard offset < bytes.count else { break }
            let len = Int(bytes[offset])
            offset += 1
            guard offset + len <= bytes.count else { break }
            bytes[offset..<(offset + len)].withUnsafeBufferPointer { buf in
                bridge_decode_can_frame(decoder, Int32(bus), addr, buf.baseAddress, Int32(len), &state)
            }
            decodedAny = true
            offset += len
        }
        guard decodedAny else { return }

        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastEmit >= Self.emitInterval else { return }
        lastEmit = now
        continuation?.yield(CarState(from: state))
    }
}

// MARK: - DashKitGattListener

extension DashKitDataSource: DashKitGattListener {

    func onServicesReady(_ peripheral: CBPeripheral) {
        guard let characteristic = peripheral.services?
            .first(where: { $0.uuid == DashKitGatt.canService })?
            .characteristics?
            .first(where: { $0.uuid == DashKitGatt.canCharacteristic }) else {
            print("[DashKitDataSource] CAN BLE characteristic not found")
            return
        }
        // Triggers the system pairing dialog on first connect — the CAN
        // characteristic requires an encrypted link.
        peripheral.setNotifyValue(true, for: characteristic)
        print("[DashKitDataSource] subscribed to CAN notifications")
    }

    func onCharacteristicChanged(_ characteristic: CBCharacteristic, value: Data) {
        guard characteristic.uuid == DashKitGatt.canCharacteristic else { return }
        parseAndEmit(value)
    }
}

// MARK: - C string array helper (same shape as CommaDataSource's private one)

private extension Array where Element == String {
    func withCStringArray<R>(_ body: (UnsafeMutablePointer<UnsafePointer<CChar>?>) -> R) -> R {
        var cStrings = self.map { strdup($0) }
        defer { cStrings.forEach { free($0) } }
        return cStrings.withUnsafeMutableBufferPointer { buffer in
            let bound = buffer.baseAddress!.withMemoryRebound(to: UnsafePointer<CChar>?.self, capacity: buffer.count) { $0 }
            return body(bound)
        }
    }
}
