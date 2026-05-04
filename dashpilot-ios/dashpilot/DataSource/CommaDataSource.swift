import Foundation

final class CommaDataSource: IDataSource {

    private var continuation: AsyncStream<CarState>.Continuation?
    let incomingMessages: AsyncStream<CarState>

    private var context: UnsafeMutableRawPointer?
    private var group: UnsafeMutableRawPointer?

    init() {
        var capturedContinuation: AsyncStream<CarState>.Continuation?
        incomingMessages = AsyncStream(bufferingPolicy: .bufferingNewest(16)) { continuation in
            capturedContinuation = continuation
        }
        self.continuation = capturedContinuation
    }

    func connect(address: String) {
        let ctx = bridge_create_context()
        self.context = ctx

        var endpoints: [String] = ["can", "selfdriveState", "selfdriveStateSP"]
        let group = endpoints.withCStringArray { ptrs in
            bridge_create_sub_sockets(ctx, ptrs, Int32(endpoints.count), address)
        }
        self.group = group

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            bridge_start_receive_loop(group) { data, size in
                guard let data, size > 0 else { return }
                // TODO: decode raw message into CarState
                print("[CommaDataSource] received \(size) bytes")
            }
            // Loop exited
            self?.continuation?.finish()
        }
    }

    func disconnect() {
        bridge_stop_receive_loop()
        if let group { bridge_delete_sub_sockets(group) }
        if let context { bridge_delete_context(context) }
        self.group = nil
        self.context = nil
        continuation?.finish()
    }
}

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
