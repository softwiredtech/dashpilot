import SwiftUI
import WebKit

struct WebDashView: UIViewRepresentable {

    let url: String
    let incomingMessages: AsyncStream<CarState>

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "log")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator
        webView.backgroundColor = .black
        webView.isOpaque = false
        context.coordinator.webView = webView
        context.coordinator.incomingMessages = incomingMessages

        if url.hasPrefix("http") || url.hasPrefix("https") {
            webView.load(URLRequest(url: URL(string: url)!))
        } else if url == "vanilla" {
            if let fileURL = Bundle.main.url(forResource: "index", withExtension: "html",
                                              subdirectory: "web-vanilla") {
                webView.loadFileURL(fileURL, allowingReadAccessTo: fileURL.deletingLastPathComponent())
            }
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {

        weak var webView: WKWebView?
        var incomingMessages: AsyncStream<CarState>?
        private var receiveTask: Task<Void, Never>?

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard let stream = incomingMessages else { return }
            startReceiving(stream)
        }

        private func startReceiving(_ stream: AsyncStream<CarState>) {
            receiveTask?.cancel()
            receiveTask = Task { @MainActor in
                for await state in stream {
                    let json = state.toJSONString()
                    webView?.evaluateJavaScript(
                        "window.onCarStateUpdate && window.onCarStateUpdate(\(json))",
                        completionHandler: nil
                    )
                }
            }
        }

        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            if message.name == "log" {
                print("WebView JS: \(message.body)")
            }
        }

        deinit {
            receiveTask?.cancel()
        }
    }
}
