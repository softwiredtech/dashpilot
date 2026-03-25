import Foundation

final class WebsocketDataSource: NSObject, IDataSource {

    private var session: URLSession?
    private var webSocketTask: URLSessionWebSocketTask?
    private var continuation: AsyncStream<CarState>.Continuation?

    let incomingMessages: AsyncStream<CarState>

    override init() {
        var capturedContinuation: AsyncStream<CarState>.Continuation?
        incomingMessages = AsyncStream(bufferingPolicy: .bufferingNewest(16)) { continuation in
            capturedContinuation = continuation
        }
        super.init()
        self.continuation = capturedContinuation
    }

    func connect(address: String) {
        guard let url = URL(string: "ws://\(address):8080") else {
            print("[WS] Invalid URL for address: \(address)")
            return
        }
        print("[WS] Connecting to \(url)")
        let s = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        session = s
        webSocketTask = s.webSocketTask(with: url)
        webSocketTask?.resume()
        scheduleReceive()
    }

    func disconnect() {
        print("[WS] Disconnecting")
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
        session?.invalidateAndCancel()
        session = nil
        continuation?.finish()
    }

    private func scheduleReceive() {
        webSocketTask?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    if let data = text.data(using: .utf8) {
                        do {
                            let state = try JSONDecoder().decode(CarState.self, from: data)
                            self.continuation?.yield(state)
                        } catch {
                            print("[WS] JSON decode error: \(error)\nraw: \(text.prefix(200))")
                        }
                    }
                case .data(let data):
                    do {
                        let state = try JSONDecoder().decode(CarState.self, from: data)
                        self.continuation?.yield(state)
                    } catch {
                        print("[WS] Binary JSON decode error: \(error)")
                    }
                @unknown default:
                    break
                }
                self.scheduleReceive()
            case .failure(let error):
                print("[WS] Receive error: \(error)")
                self.continuation?.finish()
            }
        }
    }
}

extension WebsocketDataSource: URLSessionWebSocketDelegate {
    func urlSession(_ session: URLSession,
                    webSocketTask: URLSessionWebSocketTask,
                    didOpenWithProtocol protocol: String?) {
        print("[WS] Handshake OK, protocol: \(`protocol` ?? "none")")
    }

    func urlSession(_ session: URLSession,
                    webSocketTask: URLSessionWebSocketTask,
                    didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
                    reason: Data?) {
        let msg = reason.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        print("[WS] Closed: code=\(closeCode.rawValue) reason=\(msg)")
        continuation?.finish()
    }
}

extension WebsocketDataSource: URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    didCompleteWithError error: Error?) {
        if let error {
            print("[WS] Task completed with error: \(error)")
        }
    }
}
