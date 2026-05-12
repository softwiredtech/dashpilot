import Foundation
import Observation

@Observable
final class ConnectionViewModel {

    private(set) var connectionStatus: ConnectionStatus = .disconnected
    private(set) var dataSource: (any IDataSource)?
    private(set) var discoveredAddress: String?
    private(set) var discoveryError: String?

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
        discoveredAddress = nil
        discoveryError = nil
        connectionStatus = .disconnected
    }

    // MARK: - Private

    private func startDataSource(address: String) {
        let ds = CommaDataSource()
        dataSource = ds
        ds.connect(address: address)

        connectTask = Task { @MainActor [weak self] in
            guard let self else { return }
            for await _ in ds.incomingMessages {
                self.connectionStatus = .connected
                break
            }
        }
    }
}
