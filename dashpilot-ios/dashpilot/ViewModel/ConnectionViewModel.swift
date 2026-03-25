import Foundation
import Observation

@Observable
final class ConnectionViewModel {

    private(set) var connectionStatus: ConnectionStatus = .disconnected
    private(set) var dataSource: (any IDataSource)?

    private var firstMessageTask: Task<Void, Never>?

    func connect(serverAddress: String) {
        guard connectionStatus == .disconnected else { return }

        let ds = WebsocketDataSource()
        dataSource = ds
        connectionStatus = .connecting

        ds.connect(address: serverAddress)

        firstMessageTask = Task { @MainActor [weak self] in
            guard let self else { return }
            for await _ in ds.incomingMessages {
                self.connectionStatus = .connected
                break
            }
        }
    }

    func disconnect() {
        firstMessageTask?.cancel()
        firstMessageTask = nil
        dataSource?.disconnect()
        dataSource = nil
        connectionStatus = .disconnected
    }
}
