import Foundation
import CoreBluetooth
import Observation
import UIKit

/// Fan-out abstraction for GATT events, mirroring the Android `GattListener`.
/// The data source, firmware-version reader and OTA updater each register one.
protocol DashKitGattListener: AnyObject {
    func onServicesReady(_ peripheral: CBPeripheral)
    func onCharacteristicChanged(_ characteristic: CBCharacteristic, value: Data)
    func onCharacteristicRead(_ characteristic: CBCharacteristic, value: Data?, error: Error?)
    func onCharacteristicWrite(_ characteristic: CBCharacteristic, error: Error?)
    /// Notify subscription armed/disarmed (Android's onDescriptorWrite).
    func onNotificationStateUpdated(_ characteristic: CBCharacteristic, error: Error?)
    func onDisconnected()
}

extension DashKitGattListener {
    func onServicesReady(_ peripheral: CBPeripheral) {}
    func onCharacteristicChanged(_ characteristic: CBCharacteristic, value: Data) {}
    func onCharacteristicRead(_ characteristic: CBCharacteristic, value: Data?, error: Error?) {}
    func onCharacteristicWrite(_ characteristic: CBCharacteristic, error: Error?) {}
    func onNotificationStateUpdated(_ characteristic: CBCharacteristic, error: Error?) {}
    func onDisconnected() {}
}

/// DashKit GATT identifiers, shared by the data source, control writer and OTA
/// updater. Values match the firmware and the Android app.
enum DashKitGatt {
    static let deviceName = "DashKit"
    static let canService = CBUUID(string: "CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
    /// Batched raw CAN frames (notify).
    static let canCharacteristic = CBUUID(string: "CADA0001-CA00-B1E0-B0D6-C000AA0100A1")
    /// Vehicle control commands (write): [opcode][value_lo][value_hi].
    static let controlCharacteristic = CBUUID(string: "CADA0004-CA00-B1E0-B0D6-C000AA0100A1")
    /// Installed firmware version (read, ASCII).
    static let versionCharacteristic = CBUUID(string: "CADA0005-CA00-B1E0-B0D6-C000AA0100A1")

    static let otaService = CBUUID(string: "CADA0100-CA00-B1E0-B0D6-C000AA0100A1")
    static let otaControlCharacteristic = CBUUID(string: "CADA0101-CA00-B1E0-B0D6-C000AA0100A1")
    static let otaDataCharacteristic = CBUUID(string: "CADA0102-CA00-B1E0-B0D6-C000AA0100A1")
    static let otaStatusCharacteristic = CBUUID(string: "CADA0103-CA00-B1E0-B0D6-C000AA0100A1")
}

/// CoreBluetooth port of the Android `DashKitBleManager`: scans for the
/// advertised "DashKit" name, connects, discovers services and fans GATT events
/// out to registered listeners. Handles retry with timeouts while connecting
/// and auto-reconnects after an unexpected drop (typically a DashKit reboot).
///
/// Pairing differs from Android: DashKit's characteristics require an encrypted
/// link, and iOS bonds transparently (system pairing dialog) on the first
/// access to a protected characteristic — there is no explicit bond step.
@Observable
final class DashKitBleManager: NSObject {

    // A connection attempt is one scan + connect cycle. Retry a few times with
    // a pause in between (DashKit may be mid-reboot, or the previous link may
    // still be tearing down) before surfacing an error.
    private static let maxAttempts = 3
    private static let scanTimeout: TimeInterval = 15
    private static let connectTimeout: TimeInterval = 10
    private static let retryDelay: TimeInterval = 2

    // A drop this soon after reaching .connected is a setup failure, not a
    // mid-session loss: encryption is only initiated when the data source
    // subscribes (service discovery runs unencrypted), so a refused pairing or
    // a stale bond shows up as repeated drops just after .connected.
    private static let quickDropWindow: TimeInterval = 15
    private static let maxQuickDrops = 3

    /// Matches PAIRING_WINDOW_S in the firmware.
    private static let pairingWindow: TimeInterval = 120

    /// Keepalive interval; the firmware drops the link after 60 s without a
    /// ping (KEEPALIVE_TIMEOUT_S), so this allows a few missed writes.
    private static let pingInterval: TimeInterval = 15

    /// UI-facing state, mirrored from `state` on the main queue.
    private(set) var connectionState: ConnectionStatus = .disconnected

    /// Invoked on the main queue after every state change (the
    /// ConnectionViewModel uses this to surface BLE errors).
    @ObservationIgnored var onStateChange: ((ConnectionStatus) -> Void)?

    /// Authoritative state; only touched on `queue`.
    private var state: ConnectionStatus = .disconnected

    private var central: CBCentralManager?
    private(set) var peripheral: CBPeripheral?

    /// Serial queue all CoreBluetooth callbacks and state mutations run on.
    private let queue = DispatchQueue(label: "dashkit.ble")

    private var listeners: [DashKitGattListener] = []
    private let listenersLock = NSLock()

    // Set only when the app itself tears down the link (disconnect()). Any
    // other drop after a healthy connection is treated as transient and
    // triggers an automatic reconnect.
    private var userInitiatedDisconnect = false

    // True while a connect() intent is pending central power-on: CoreBluetooth
    // reports .poweredOn asynchronously and scans started earlier are dropped.
    private var scanPendingPowerOn = false

    // Scan+connect cycles used so far for the current connection intent.
    private var attemptCount = 0

    // Consecutive drops shortly after reaching .connected (see quickDropWindow).
    // iOS cannot delete a bond programmatically, so after maxQuickDrops the
    // manager stops reconnecting and surfaces instructions instead of flapping.
    private var quickDropCount = 0
    private var connectedAt: TimeInterval = 0

    // Set when this phone asks DashKit to open its pairing window. The firmware
    // drops us to free the single connection slot for the new device; until the
    // window ends we must not auto-reconnect and steal the slot back.
    private var pairingWindowUntil: TimeInterval = 0

    /// Called after sending the enter-pairing command: the upcoming disconnect
    /// is expected, and auto-reconnect stays paused for the window duration.
    func expectPairingWindowDrop() {
        queue.async { [self] in
            pairingWindowUntil = ProcessInfo.processInfo.systemUptime + Self.pairingWindow
        }
    }

    private var scanTimeoutTask: DispatchWorkItem?
    private var connectTimeoutTask: DispatchWorkItem?
    private var retryTask: DispatchWorkItem?
    private var pingTask: DispatchWorkItem?

    /// Foreground gate for the keepalive, mirroring Android. iOS suspension
    /// stops the ping timer anyway, but not *immediately* (the app gets a few
    /// seconds of background runtime) and not at all under the Xcode debugger —
    /// so stop pinging explicitly the moment the app backgrounds, and never
    /// auto-reconnect from the background. Accessed on `queue`.
    private var appInForeground = true

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.queue.async {
                self.appInForeground = false
                self.pingTask?.cancel()
                self.pingTask = nil
            }
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.queue.async {
                self.appInForeground = true
                // Sends a ping right away — the link may be seconds from the
                // firmware's keepalive timeout after a short backgrounding.
                if self.state == .connected { self.schedulePing() }
            }
        }
    }

    func addGattListener(_ listener: DashKitGattListener) {
        listenersLock.lock()
        listeners.append(listener)
        listenersLock.unlock()
        // If already connected with services discovered, notify immediately.
        queue.async { [self] in
            if let p = peripheral, state == .connected {
                listener.onServicesReady(p)
            }
        }
    }

    func removeGattListener(_ listener: DashKitGattListener) {
        listenersLock.lock()
        listeners.removeAll { $0 === listener }
        listenersLock.unlock()
    }

    private func forEachListener(_ action: (DashKitGattListener) -> Void) {
        listenersLock.lock()
        let snapshot = listeners
        listenersLock.unlock()
        snapshot.forEach(action)
    }

    func connect() {
        queue.async { [self] in
            if state == .connected || state == .connecting { return }
            setState(.connecting)
            attemptCount = 0
            quickDropCount = 0
            userInitiatedDisconnect = false
            if central == nil {
                central = CBCentralManager(delegate: self, queue: queue)
                scanPendingPowerOn = true  // scan starts in centralManagerDidUpdateState
            } else {
                startScan()
            }
        }
    }

    func disconnect() {
        queue.async { [self] in
            userInitiatedDisconnect = true
            cancelScheduled()
            stopScan()
            scanPendingPowerOn = false
            if let p = peripheral {
                central?.cancelPeripheralConnection(p)
            }
            peripheral = nil
            setState(.disconnected)
            forEachListener { $0.onDisconnected() }
        }
    }

    // MARK: - Writes/reads used by VehicleControl and the settings screen

    /// Returns the characteristic with the given UUID on a discovered service,
    /// or nil when the link is down or discovery hasn't finished. Callable
    /// from any thread except the manager's own BLE queue.
    func characteristic(service: CBUUID, characteristic: CBUUID) -> CBCharacteristic? {
        queue.sync {
            guard let p = peripheral, state == .connected else { return nil }
            return p.services?
                .first { $0.uuid == service }?
                .characteristics?
                .first { $0.uuid == characteristic }
        }
    }

    // MARK: - Internals (all on `queue`)

    private func setState(_ newState: ConnectionStatus) {
        state = newState
        // @Observable mutation observed by SwiftUI — hop to the main queue.
        DispatchQueue.main.async {
            self.connectionState = newState
            self.onStateChange?(newState)
        }
    }

    private func cancelScheduled() {
        scanTimeoutTask?.cancel()
        connectTimeoutTask?.cancel()
        retryTask?.cancel()
        pingTask?.cancel()
        scanTimeoutTask = nil
        connectTimeoutTask = nil
        retryTask = nil
        pingTask = nil
    }

    // Keepalive: sends one ping now, then reschedules itself while .connected.
    // The write hops off `queue` because VehicleControl.send uses queue.sync.
    // Pinging immediately (not after one interval) matters: the firmware only
    // arms its keepalive drop once the first ping arrives, so an app that
    // suspends before its first ping would never be evicted.
    private func schedulePing() {
        pingTask?.cancel()
        DispatchQueue.global().async { [weak self] in
            guard let self else { return }
            VehicleControl.sendPing(self)
        }
        pingTask = schedule(after: Self.pingInterval) { [weak self] in
            guard let self, self.state == .connected else { return }
            self.schedulePing()
        }
    }

    private func schedule(after delay: TimeInterval, _ block: @escaping () -> Void) -> DispatchWorkItem {
        let task = DispatchWorkItem(block: block)
        queue.asyncAfter(deadline: .now() + delay, execute: task)
        return task
    }

    // Retry after a short pause while attempts remain, otherwise publish an
    // error so the UI leaves "Connecting…" instead of hanging forever.
    private func retryOrFail(_ errorMessage: String) {
        if userInitiatedDisconnect { return }
        if attemptCount < Self.maxAttempts {
            retryTask = schedule(after: Self.retryDelay) { [weak self] in
                guard let self, !self.userInitiatedDisconnect else { return }
                self.startScan()
            }
            return
        }
        print("[DashKitBleManager] \(errorMessage) (after \(attemptCount) attempts)")
        setState(.error(errorMessage))
        forEachListener { $0.onDisconnected() }
    }

    private func startScan() {
        guard let central else { return }
        guard central.state == .poweredOn else {
            scanPendingPowerOn = true
            return
        }
        // DashKit does not advertise its service UUID, so scan unfiltered and
        // match on the advertised local name (like the Android name filter).
        central.scanForPeripherals(withServices: nil, options: nil)
        attemptCount += 1
        print("[DashKitBleManager] scan started (attempt \(attemptCount)/\(Self.maxAttempts))")
        scanTimeoutTask?.cancel()
        scanTimeoutTask = schedule(after: Self.scanTimeout) { [weak self] in
            guard let self, let central = self.central, central.isScanning else { return }
            print("[DashKitBleManager] scan timed out without finding DashKit")
            self.stopScan()
            self.retryOrFail("DashKit not found")
        }
    }

    private func stopScan() {
        scanTimeoutTask?.cancel()
        scanTimeoutTask = nil
        if central?.isScanning == true {
            central?.stopScan()
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension DashKitBleManager: CBCentralManagerDelegate {

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            if scanPendingPowerOn {
                scanPendingPowerOn = false
                startScan()
            }
        case .unauthorized:
            setState(.error("Bluetooth permission denied"))
        case .poweredOff:
            if state == .connecting {
                setState(.error("Bluetooth is off"))
            }
        default:
            break
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover discovered: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? discovered.name
        guard name == DashKitGatt.deviceName else { return }
        // Duplicate advertisements can be delivered after stopScan(); never
        // issue a second connect while one attempt or a live connection exists.
        guard peripheral == nil else { return }
        print("[DashKitBleManager] found DashKit, connecting")
        stopScan()
        peripheral = discovered
        discovered.delegate = self
        central.connect(discovered, options: nil)
        connectTimeoutTask?.cancel()
        connectTimeoutTask = schedule(after: Self.connectTimeout) { [weak self] in
            guard let self, let pending = self.peripheral,
                  self.state == .connecting else { return }
            print("[DashKitBleManager] connect attempt timed out")
            self.central?.cancelPeripheralConnection(pending)
            self.peripheral = nil
            self.retryOrFail("Could not connect to DashKit")
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect connected: CBPeripheral) {
        guard connected === peripheral else { return }
        connectTimeoutTask?.cancel()
        connectTimeoutTask = nil
        print("[DashKitBleManager] connected, discovering services")
        connected.discoverServices([DashKitGatt.canService, DashKitGatt.otaService])
    }

    func centralManager(_ central: CBCentralManager,
                        didFailToConnect failed: CBPeripheral,
                        error: Error?) {
        guard failed === peripheral else { return }
        connectTimeoutTask?.cancel()
        connectTimeoutTask = nil
        peripheral = nil
        print("[DashKitBleManager] connect failed: \(error?.localizedDescription ?? "unknown")")
        retryOrFail("Could not connect to DashKit")
    }

    func centralManager(_ central: CBCentralManager,
                        didDisconnectPeripheral dropped: CBPeripheral,
                        error: Error?) {
        guard dropped === peripheral else { return }
        connectTimeoutTask?.cancel()
        connectTimeoutTask = nil
        let reachedConnected = state == .connected
        peripheral = nil
        if userInitiatedDisconnect {
            setState(.disconnected)
            forEachListener { $0.onDisconnected() }
            return
        }
        if !reachedConnected {
            // Link dropped before setup completed: DashKit refused/tore down a
            // pairing, the previous link was still clearing, or the connect
            // attempt itself failed. Retry a few times before surfacing an
            // error.
            print("[DashKitBleManager] link dropped before setup completed")
            retryOrFail("Could not connect to DashKit")
            return
        }
        if ProcessInfo.processInfo.systemUptime < pairingWindowUntil {
            // We asked DashKit to open its pairing window and it dropped us to
            // free the only connection slot for the new device. Don't fight for
            // it — stay disconnected; foregrounding the app (or a manual retry)
            // reconnects once the window is over.
            print("[DashKitBleManager] dropped for pairing window; auto-reconnect paused")
            setState(.disconnected)
            forEachListener { $0.onDisconnected() }
            return
        }
        if !appInForeground {
            // We stopped pinging when the app backgrounded, so this drop is
            // (usually) the firmware freeing the slot. Reconnecting from the
            // background would squat on it again without pinging — stay down;
            // ConnectionViewModel.onAppForegrounded restores the session.
            print("[DashKitBleManager] dropped while backgrounded; reconnect deferred to foreground")
            setState(.disconnected)
            forEachListener { $0.onDisconnected() }
            return
        }
        let quickDrop = ProcessInfo.processInfo.systemUptime - connectedAt < Self.quickDropWindow
        quickDropCount = quickDrop ? quickDropCount + 1 : 0
        if quickDrop && quickDropCount >= Self.maxQuickDrops {
            // Every reconnect completes service discovery (unencrypted) and
            // then dies when the subscription forces encryption: DashKit is
            // rejecting this phone's pairing. iOS cannot delete a bond
            // programmatically, so stop flapping and tell the user what to do.
            print("[DashKitBleManager] repeated drops right after connect; giving up")
            setState(.error("DashKit rejected this phone's pairing. If DashKit is paired with another phone, open \"Pair a new device\" in its DashPilot settings and try again. If this phone was paired before, forget \"DashKit\" in Settings > Bluetooth first."))
            forEachListener { $0.onDisconnected() }
            return
        }
        // Unexpected drop after a healthy connection (typically a DashKit
        // reboot). The pairing survives the reboot, so restart the scan: it
        // reconnects, re-discovers services and the data source re-subscribes
        // via onServicesReady.
        print("[DashKitBleManager] link dropped after connect; auto-reconnecting")
        setState(.connecting)
        forEachListener { $0.onDisconnected() }
        attemptCount = 0  // fresh retry budget for the reconnect
        startScan()
    }
}

// MARK: - CBPeripheralDelegate

extension DashKitBleManager: CBPeripheralDelegate {

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            print("[DashKitBleManager] service discovery failed: \(error.localizedDescription)")
            setState(.error("Service discovery failed"))
            return
        }
        let services = peripheral.services ?? []
        guard !services.isEmpty else {
            setState(.error("Service discovery failed"))
            return
        }
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        // Wait until every service has reported its characteristics before
        // declaring the connection ready.
        let services = peripheral.services ?? []
        guard services.allSatisfy({ $0.characteristics != nil }) else { return }
        guard state != .connected else { return }
        print("[DashKitBleManager] discovered \(services.count) services; connected")
        attemptCount = 0
        connectedAt = ProcessInfo.processInfo.systemUptime
        setState(.connected)
        forEachListener { $0.onServicesReady(peripheral) }
        schedulePing()
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        if characteristic.properties.contains(.notify), error == nil, let value = characteristic.value {
            forEachListener { $0.onCharacteristicChanged(characteristic, value: value) }
        }
        // Reads land here too (CoreBluetooth shares the callback).
        if !characteristic.properties.contains(.notify) || error != nil {
            forEachListener { $0.onCharacteristicRead(characteristic, value: characteristic.value, error: error) }
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didWriteValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        forEachListener { $0.onCharacteristicWrite(characteristic, error: error) }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateNotificationStateFor characteristic: CBCharacteristic,
                    error: Error?) {
        forEachListener { $0.onNotificationStateUpdated(characteristic, error: error) }
    }
}
