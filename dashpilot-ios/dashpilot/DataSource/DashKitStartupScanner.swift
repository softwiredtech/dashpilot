import Foundation
import CoreBluetooth

/// One-shot launch-time scan that answers "is a DashKit advertising nearby?"
/// (port of the Android `scanForDashKit` startup discovery). Unlike Android,
/// iOS has no bond-state API, so `ConnectionViewModel` decides what a "found"
/// result means: silent auto-connect once onboarding was completed, otherwise
/// it presents the onboarding flow first.
final class DashKitStartupScanner: NSObject, CBCentralManagerDelegate {

    private var central: CBCentralManager?
    private let queue = DispatchQueue(label: "dashkit.startup-scan")
    private var continuation: CheckedContinuation<Bool, Never>?
    private var timeoutTask: DispatchWorkItem?

    private var scanDuration: TimeInterval = 3

    /// Scans for up to `timeout` seconds and resolves as soon as a peripheral
    /// advertising the DashKit name is seen. The window only starts once
    /// Bluetooth is powered on, so the first-launch permission dialog does not
    /// eat into it; a generous safety cap covers a never-answered dialog.
    func scan(timeout: TimeInterval) async -> Bool {
        await withCheckedContinuation { continuation in
            queue.async { [self] in
                self.continuation = continuation
                scanDuration = timeout
                central = CBCentralManager(delegate: self, queue: queue)
                let cap = DispatchWorkItem { [weak self] in self?.finish(false) }
                timeoutTask = cap
                queue.asyncAfter(deadline: .now() + 60, execute: cap)
            }
        }
    }

    private func finish(_ found: Bool) {
        guard let continuation else { return }
        self.continuation = nil
        timeoutTask?.cancel()
        timeoutTask = nil
        if central?.isScanning == true { central?.stopScan() }
        central = nil
        continuation.resume(returning: found)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            central.scanForPeripherals(withServices: nil, options: nil)
            timeoutTask?.cancel()
            let task = DispatchWorkItem { [weak self] in self?.finish(false) }
            timeoutTask = task
            queue.asyncAfter(deadline: .now() + scanDuration, execute: task)
        case .unauthorized, .unsupported, .poweredOff:
            finish(false)
        default:
            break
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name
        guard name == DashKitGatt.deviceName else { return }
        finish(true)
    }
}
