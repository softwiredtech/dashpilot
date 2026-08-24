import Foundation
import Observation
import UIKit

@Observable
final class ConnectionViewModel {

    /// UserDefaults key for the "onboarding was finished or skipped" flag
    /// (iOS stand-in for the Android bond-state check — iOS cannot ask
    /// whether the DashKit is already paired).
    static let onboardingCompletedKey = "dashkitOnboardingCompleted"

    /// Set when the launch scan finds a DashKit but onboarding has not been
    /// completed yet (or when the user replays onboarding from Settings).
    /// The app root observes it to present the onboarding cover; dismissing
    /// the cover clears it.
    var onboardingRequested = false

    private(set) var connectionStatus: ConnectionStatus = .disconnected
    private(set) var discoveredAddress: String?
    private(set) var discoveryError: String?

    /// Most recent state of the session, replayed to new subscribers so a view
    /// opened mid-session renders immediately.
    private(set) var latestDashState: DashState?

    /// Live per-view subscriptions. AsyncStream is single-consumer and dies
    /// with its consuming task, so every view gets its own stream and the pump
    /// fans out to all of them (the Android equivalent is a shared Flow).
    @ObservationIgnored
    private var dashSubscribers: [UUID: AsyncStream<DashState>.Continuation] = [:]

    /// Live BLE manager while a DashKit session is active. Views use it to
    /// send control commands and the settings screen for firmware/OTA access.
    private(set) var bleManager: DashKitBleManager?

    /// Source of the current (or last) session; stays set after a DashKit
    /// error so foregrounding retries the same source, like Android.
    private(set) var activeSourceType: DataSourceType?

    private var dataSource: (any IDataSource)?
    private var connectTask: Task<Void, Never>?
    private var startupDiscoveryStarted = false

    /// Returns a stream of `DashState` updates for one view. Each caller gets
    /// an independent stream, so one view leaving (its task being cancelled)
    /// tears down only its own subscription — not the pipeline feeding the
    /// other views. Subscriptions survive reconnects; the stream ends when the
    /// consuming task is cancelled.
    func dashStateStream() -> AsyncStream<DashState> {
        AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let id = UUID()
            dashSubscribers[id] = continuation
            if let latest = latestDashState {
                continuation.yield(latest)
            }
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in
                    self?.dashSubscribers.removeValue(forKey: id)
                }
            }
        }
    }

    /// True when a new connection may start (mirrors Android: connect is
    /// allowed from Disconnected and Error).
    private var canStartConnection: Bool {
        switch connectionStatus {
        case .disconnected, .error: return true
        case .connecting, .connected: return false
        }
    }

    // MARK: - Startup discovery / warm launch

    /// Briefly scan for an advertising DashKit at launch (port of the Android
    /// startup discovery). If onboarding was already completed the DashKit is
    /// silently auto-connected; otherwise the onboarding flow is requested and
    /// its "Pair device" CTA drives the first connection.
    func startupAutoConnect() {
        guard !startupDiscoveryStarted else { return }
        startupDiscoveryStarted = true
        guard connectionStatus == .disconnected else { return }

        Task { @MainActor [weak self] in
            let found = await DashKitStartupScanner().scan(timeout: 3)
            guard let self, found, self.canStartConnection, self.dataSource == nil else { return }
            if UserDefaults.standard.bool(forKey: Self.onboardingCompletedKey) {
                self.connectDashKit()
            } else {
                self.onboardingRequested = true
            }
        }
    }

    /// Called when the app returns to the foreground. Keeps a healthy DashKit
    /// link (a teardown + rescan would drop it), revives a dead DashKit
    /// session, and otherwise re-runs startup discovery in case the app was
    /// opened away from the car before.
    func onAppForegrounded() {
        switch activeSourceType {
        case .dashkit:
            if let manager = bleManager,
               manager.connectionState == .connected || manager.connectionState == .connecting {
                return
            }
            disconnect()
            connectDashKit()
        case .comma, .websocket:
            // WiFi sources are user-initiated on iOS; leave them alone.
            return
        case nil:
            guard connectionStatus == .disconnected else { return }
            startupDiscoveryStarted = false
            startupAutoConnect()
        }
    }

    // MARK: - DashKit

    func connectDashKit() {
        guard canStartConnection else { return }
        if case .error = connectionStatus { disconnect() }  // drop failed-session remnants
        connectionStatus = .connecting
        discoveryError = nil
        discoveredAddress = nil
        activeSourceType = .dashkit

        let manager = DashKitBleManager()
        bleManager = manager
        manager.onStateChange = { [weak self] state in
            guard let self else { return }
            // Surface BLE failures while we are still waiting for the first
            // CAN frame so the UI leaves "Connecting…" (mirrors Android).
            if case .error = state, self.connectionStatus == .connecting {
                self.connectionStatus = state
            }
        }

        let ds = DashKitDataSource(manager: manager)
        dataSource = ds
        ds.connect(address: "")
        startStreaming(ds, resyncDashKit: true)
    }

    /// Pushes the persisted multi-finger bindings and the wiper-off flag to
    /// the firmware. The firmware persists them in NVS too, but re-syncing on
    /// each connect keeps its state matching the app exactly (e.g. after a
    /// DashKit reboot or an edit made while disconnected).
    func resyncDashKitAutomations() {
        guard let manager = bleManager else { return }
        let raw = UserDefaults.standard.string(forKey: "finger_actions") ?? ""
        let actions = AutomationsView.parseFingerActions(raw)
        let byCount = Dictionary(uniqueKeysWithValues: actions.map { ($0.fingerCount, $0.controlId) })
        for fingers in 3...5 {
            let actionValue = byCount[fingers].flatMap { controlById($0)?.gestureValue }
                ?? VehicleControl.gestureActionNone
            VehicleControl.sendFingerAction(manager, fingers: fingers, actionValue: actionValue)
        }
        let wiperOff = UserDefaults.standard.bool(forKey: "wiper_off_automation")
        VehicleControl.sendWiperOff(manager, enabled: wiperOff)
        let climateKeep = UserDefaults.standard.bool(forKey: "climate_keep_automation")
        VehicleControl.sendClimateKeep(manager, enabled: climateKeep)
        let climateKeepMinutes = UserDefaults.standard.integer(forKey: "climate_keep_minutes")
        VehicleControl.sendClimateKeepDuration(manager, minutes: climateKeepMinutes > 0 ? climateKeepMinutes : 5)
    }

    // MARK: - Comma (WiFi)

    /// Auto-discover a publisher on the local subnet, then connect.
    func autoConnect() {
        guard canStartConnection else { return }
        if case .error = connectionStatus { disconnect() }  // drop failed-session remnants
        connectionStatus = .connecting
        discoveryError = nil
        discoveredAddress = nil
        activeSourceType = .comma

        connectTask = Task { @MainActor [weak self] in
            guard let self else { return }

            // Retry discovery in a loop until found or cancelled
            var foundIp: String?
            while foundIp == nil && !Task.isCancelled {
                foundIp = await NetworkUtil.findPublisher(endpoint: "can", timeoutMs: 5000)
                if foundIp == nil && !Task.isCancelled {
                    // Brief pause before retrying
                    try? await Task.sleep(for: .milliseconds(700))
                }
            }

            guard !Task.isCancelled, let ip = foundIp else {
                if !Task.isCancelled {
                    self.discoveryError = "Could not find device on the network"
                    self.connectionStatus = .disconnected
                }
                return
            }

            self.discoveredAddress = ip
            self.startCommaDataSource(address: ip)
        }
    }

    /// Connect directly to a known address (manual entry).
    func connect(serverAddress: String) {
        guard canStartConnection else { return }
        if case .error = connectionStatus { disconnect() }  // drop failed-session remnants
        connectionStatus = .connecting
        discoveryError = nil
        discoveredAddress = serverAddress
        activeSourceType = .comma
        startCommaDataSource(address: serverAddress)
    }

    func disconnect() {
        connectTask?.cancel()
        connectTask = nil
        dataSource?.disconnect()
        dataSource = nil
        bleManager?.onStateChange = nil
        bleManager?.disconnect()
        bleManager = nil
        activeSourceType = nil
        latestDashState = nil
        discoveredAddress = nil
        discoveryError = nil
        connectionStatus = .disconnected
    }

    // MARK: - Private

    private func startCommaDataSource(address: String) {
        let extraBus = UserDefaults.standard.bool(forKey: DisplaySettings.keyExtraVehicleBus)
        let ds: CommaDataSource
        if extraBus {
            ds = CommaDataSource(
                vehicleType: "tesla_extra",
                dbcFiles: [
                    (1, "bus_1_tesla_vehicle"),
                    (2, "bus_2_tesla_party")
                ]
            )
        } else {
            ds = CommaDataSource()
        }
        dataSource = ds
        ds.connect(address: address)
        startStreaming(ds, resyncDashKit: false)
    }

    /// Consumes the data source's `CarState` stream, republishes it as
    /// `DashState` to every subscribed view, and flips to `.connected` on the
    /// first message. Subscribers are not finished when a session ends — a
    /// reconnect's pump simply resumes feeding them.
    private func startStreaming(_ ds: any IDataSource, resyncDashKit: Bool) {
        connectTask = Task { @MainActor [weak self] in
            guard let self else { return }
            UIDevice.current.isBatteryMonitoringEnabled = true
            var first = true
            for await carState in ds.incomingMessages {
                if first {
                    self.connectionStatus = .connected
                    first = false
                    // The GATT services are discovered by the time the first
                    // CAN frame arrives.
                    if resyncDashKit {
                        self.resyncDashKitAutomations()
                    }
                }
                let settings = DisplaySettingsState.fromUserDefaults()
                let converted = settings.useImperial ? carState.toImperial() : carState
                let batteryLevel = UIDevice.current.batteryLevel
                let state = DashState(
                    carState: converted,
                    displaySettings: settings,
                    phoneBattery: batteryLevel >= 0 ? Int(batteryLevel * 100) : -1,
                    currentTime: Int64(Date().timeIntervalSince1970 * 1000),
                    dataSourceType: self.activeSourceType
                )
                self.latestDashState = state
                for continuation in self.dashSubscribers.values {
                    continuation.yield(state)
                }
            }
        }
    }
}
