import Foundation
import Observation

@Observable
final class ConnectionViewModel {

    private(set) var connectionStatus: ConnectionStatus = .disconnected
    private(set) var dataSource: (any IDataSource)?

    private var firstMessageTask: Task<Void, Never>?

    func connect(serverAddress: String) {
        guard connectionStatus == .disconnected else { return }
        
        
        let context = zmq_ctx_new()
        let socket = zmq_socket(context, ZMQ_SUB)
        zmq_connect(socket, "tcp://192.168.1.1:5555")
        // ...
        zmq_close(socket)
        zmq_ctx_destroy(context)

        /*let ds = WebsocketDataSource()
        dataSource = ds
        connectionStatus = .connecting

        ds.connect(address: serverAddress)

        firstMessageTask = Task { @MainActor [weak self] in
            guard let self else { return }
            for await _ in ds.incomingMessages {
                self.connectionStatus = .connected
                break
            }
        }*/
    }

    func disconnect() {
        firstMessageTask?.cancel()
        firstMessageTask = nil
        dataSource?.disconnect()
        dataSource = nil
        connectionStatus = .disconnected
    }
}
