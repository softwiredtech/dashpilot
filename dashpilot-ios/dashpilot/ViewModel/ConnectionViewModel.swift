import Foundation
import Observation
import UIKit

@Observable
final class ConnectionViewModel {

    private(set) var connectionStatus: ConnectionStatus = .disconnected
    private(set) var dashMessages: AsyncStream<DashState>?
    private(set) var discoveredAddress: String?
    private(set) var discoveryError: String?

    private var dataSource: (any IDataSource)?
    private var connectTask: Task<Void, Never>?

    /// Auto-discover a publisher on the local subnet, then connect.
    func autoConnect() {
        guard connectionStatus == .disconnected else { return }
        connectionStatus = .connecting
        discoveryError = nil
        discoveredAddress = nil

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
            self.startDataSource(address: ip)
        }
    }

    /// Connect directly to a known address (manual entry).
    func connect(serverAddress: String) {
        guard connectionStatus == .disconnected else { return }
        connectionStatus = .connecting
        discoveryError = nil
        discoveredAddress = serverAddress
        startDataSource(address: serverAddress)
    }

    func disconnect() {
        connectTask?.cancel()
        connectTask = nil
        dataSource?.disconnect()
        dataSource = nil
        dashMessages = nil
        discoveredAddress = nil
        discoveryError = nil
        connectionStatus = .disconnected
    }

    // MARK: - Private

    private func startDataSource(address: String) {
        let ds = CommaDataSource()
        dataSource = ds
        ds.connect(address: address)

        var capturedContinuation: AsyncStream<DashState>.Continuation?
        dashMessages = AsyncStream<DashState>(bufferingPolicy: .bufferingNewest(1)) { continuation in
            capturedContinuation = continuation
        }

        connectTask = Task { @MainActor [weak self] in
            guard let self else { return }
            UIDevice.current.isBatteryMonitoringEnabled = true
            var first = true
            for await carState in ds.incomingMessages {
                if first {
                    self.connectionStatus = .connected
                    first = false
                }
                let settings = DisplaySettingsState.fromUserDefaults()
                let converted = settings.useImperial ? carState.toImperial() : carState
                let batteryLevel = UIDevice.current.batteryLevel
                let state = DashState(
                    carState: converted,
                    displaySettings: settings,
                    phoneBattery: batteryLevel >= 0 ? Int(batteryLevel * 100) : -1,
                    currentTime: Int64(Date().timeIntervalSince1970 * 1000)
                )
                capturedContinuation?.yield(state)
            }
            capturedContinuation?.finish()
        }
    }
}
