import SwiftUI
import WebKit

// Serves local bundle files under app://localhost/ so that fetch(), ES modules,
// and WebAssembly.instantiateStreaming all work (fetch() blocks file:// URLs in WKWebView).
final class AppSchemeHandler: NSObject, WKURLSchemeHandler {

    private let bundleDir: URL

    init(bundleDir: URL) {
        self.bundleDir = bundleDir
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let requestURL = urlSchemeTask.request.url,
              var path = requestURL.path.removingPercentEncoding else {
            urlSchemeTask.didFailWithError(URLError(.badURL))
            return
        }

        if path.hasPrefix("/") { path = String(path.dropFirst()) }
        let fileURL = bundleDir.appendingPathComponent(path)

        guard !path.hasSuffix(".meta") else {
            let response = HTTPURLResponse(url: requestURL, statusCode: 404,
                                           httpVersion: "HTTP/1.1", headerFields: nil)!
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(Data())
            urlSchemeTask.didFinish()
            return
        }

        do {
            let data = try Data(contentsOf: fileURL)
            let mimeType = Self.mimeType(for: fileURL.pathExtension)
            print("[AppSchemeHandler] \(mimeType) \(path) (\(data.count) bytes)")
            let response = HTTPURLResponse(
                url: requestURL,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: [
                    "Content-Type": mimeType,
                    "Content-Length": "\(data.count)",
                    "Access-Control-Allow-Origin": "*",
                ]
            )!
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(data)
            urlSchemeTask.didFinish()
        } catch {
            print("[AppSchemeHandler] MISSING: \(path) in \(bundleDir.path)")
            let response = HTTPURLResponse(url: requestURL, statusCode: 404,
                                           httpVersion: "HTTP/1.1", headerFields: nil)!
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(Data())
            urlSchemeTask.didFinish()
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}

    private static func mimeType(for ext: String) -> String {
        switch ext.lowercased() {
        case "html":        return "text/html; charset=utf-8"
        case "js", "mjs":  return "application/javascript"
        case "wasm":        return "application/wasm"
        case "css":         return "text/css"
        case "json":        return "application/json"
        case "png":         return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "svg":         return "image/svg+xml"
        case "wgsl":        return "text/plain"
        case "glb":         return "model/gltf-binary"
        default:            return "application/octet-stream"
        }
    }
}

// localStorage is unavailable on the app:// scheme's opaque origin.
enum DashAppDataStore {
    static let messageName = "appData"
    static let editingMessageName = "editing"

    private static func key(_ appId: String) -> String { "dash_app_data.\(appId)" }

    static func save(_ json: String, for appId: String) {
        UserDefaults.standard.set(json, forKey: key(appId))
    }

    static func injectionScript(for appId: String) -> WKUserScript? {
        guard let json = UserDefaults.standard.string(forKey: key(appId)),
              let data = try? JSONSerialization.data(withJSONObject: json, options: [.fragmentsAllowed]),
              let literal = String(data: data, encoding: .utf8) else { return nil }
        return WKUserScript(source: "window.__DASHPILOT_APP_DATA__ = \(literal);",
                            injectionTime: .atDocumentStart,
                            forMainFrameOnly: true)
    }
}

struct WebDashView: UIViewRepresentable {

    let url: String
    let incomingMessages: AsyncStream<DashState>
    var onEditingChange: (Bool) -> Void = { _ in }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private static let localApps: Set<String> = ["vanilla", "retro", "ambient", "analog", "compose"]

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "log")

        if Self.localApps.contains(url) {
            let appId = "web-\(url)"
            context.coordinator.appId = appId
            config.userContentController.add(context.coordinator, name: DashAppDataStore.messageName)
            config.userContentController.add(context.coordinator, name: DashAppDataStore.editingMessageName)
            if let script = DashAppDataStore.injectionScript(for: appId) {
                config.userContentController.addUserScript(script)
            }
            let bundleDir = Bundle.main.bundleURL.appendingPathComponent(appId)
            config.setURLSchemeHandler(AppSchemeHandler(bundleDir: bundleDir), forURLScheme: "app")
        }

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator
        webView.backgroundColor = .black
        webView.isOpaque = false
        context.coordinator.webView = webView
        context.coordinator.incomingMessages = incomingMessages
        context.coordinator.onEditingChange = onEditingChange

        if url.hasPrefix("http") || url.hasPrefix("https") {
            webView.load(URLRequest(url: URL(string: url)!))
        } else if Self.localApps.contains(url) {
            webView.load(URLRequest(url: URL(string: "app://localhost/index.html")!))
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.onEditingChange = onEditingChange
    }

    // The content controller retains its handlers (and so the coordinator) until removed.
    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.stopLoading()
        uiView.configuration.userContentController.removeAllScriptMessageHandlers()
        uiView.configuration.userContentController.removeAllUserScripts()
        coordinator.stop()
    }

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {

        weak var webView: WKWebView?
        var incomingMessages: AsyncStream<DashState>?
        var onEditingChange: (Bool) -> Void = { _ in }
        var appId: String?
        private var receiveTask: Task<Void, Never>?

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            onEditingChange(false)
            guard let stream = incomingMessages else { return }
            startReceiving(stream)
        }

        func stop() {
            receiveTask?.cancel()
            receiveTask = nil
        }

        private func startReceiving(_ stream: AsyncStream<DashState>) {
            receiveTask?.cancel()
            receiveTask = Task { @MainActor in
                for await state in stream {
                    let json = state.toJSONString()
                    webView?.evaluateJavaScript(
                        "window.receiveMessage && window.receiveMessage(\(json))",
                        completionHandler: nil
                    )
                }
            }
        }

        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            if message.name == "log" {
                print("WebView JS: \(message.body)")
            } else if message.name == DashAppDataStore.messageName, let appId, let json = message.body as? String {
                DashAppDataStore.save(json, for: appId)
            } else if message.name == DashAppDataStore.editingMessageName {
                onEditingChange((message.body as? NSNumber)?.boolValue ?? false)
            }
        }

        deinit {
            receiveTask?.cancel()
        }
    }
}
