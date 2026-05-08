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

struct WebDashView: UIViewRepresentable {

    let url: String
    let incomingMessages: AsyncStream<CarState>

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "log")

        if url == "vanilla" {
            let bundleDir = Bundle.main.bundleURL.appendingPathComponent("web-vanilla")
            config.setURLSchemeHandler(AppSchemeHandler(bundleDir: bundleDir), forURLScheme: "app")
        }

        // Inject NativeCarState bridge — mirrors Android's addJavascriptInterface.
        // Telemetry getters read from window._iosCarState (updated each frame).
        // Settings getters are baked in at load time from UserDefaults.
        let ud = UserDefaults.standard
        let jsImperial   = ud.bool(forKey: DisplaySettings.keyUseImperial)   ? "true" : "false"
        let jsDarkMode   = ud.bool(forKey: DisplaySettings.keyDarkMode)      ? "true" : "false"
        let jsBlindSpot  = ud.bool(forKey: DisplaySettings.keyAlwaysOnBlindSpotMonitor) ? "true" : "false"
        let jsQuality    = ud.object(forKey: DisplaySettings.keyRenderQuality) != nil
                           ? "\(ud.integer(forKey: DisplaySettings.keyRenderQuality))" : "3"
        let jsBgGray     = "\(ud.integer(forKey: DisplaySettings.keyDarkModeBackgroundGray))"
        let jsShowPhone  = ud.object(forKey: DisplaySettings.keyShowPhoneBattery) != nil
                           ? (ud.bool(forKey: DisplaySettings.keyShowPhoneBattery) ? "true" : "false") : "true"
        let jsShowBatt   = ud.object(forKey: DisplaySettings.keyShowCarBattery) != nil
                           ? (ud.bool(forKey: DisplaySettings.keyShowCarBattery) ? "true" : "false") : "true"
        let jsShowOdo    = ud.object(forKey: DisplaySettings.keyShowOdometer) != nil
                           ? (ud.bool(forKey: DisplaySettings.keyShowOdometer) ? "true" : "false") : "true"

        let nativeBridge = """
        window.NativeCarState = (function() {
            var s = function() { return window._iosCarState || {}; };
            return {
                getEgoSteeringAngle:        function() { return s().egoSteeringAngle      ?? 0; },
                getEgoSpeed:                function() { return s().egoSpeed              ?? 0; },
                getLeftBlinker:             function() { return s().leftBlinker           ?? 0; },
                getRightBlinker:            function() { return s().rightBlinker          ?? 0; },
                getGear:                    function() { return s().gear                  ?? 0; },
                isAdasOn:                   function() { return s().adasOn                ?? false; },
                getLeftBlindSpot:           function() { return s().leftBlindSpot         ?? 0; },
                getRightBlindSpot:          function() { return s().rightBlindSpot        ?? 0; },
                getFusedSpeedLimit:         function() { return s().fusedSpeedLimit       ?? 0; },
                getStopLineDist:            function() { return s().stopLineDist          ?? 0; },
                getTrafficLightColor:       function() { return s().trafficLightColor     ?? 0; },
                getLaneDepartureWarning:    function() { return s().laneDepartureWarning  ?? 0; },
                getSideCollisionWarning:    function() { return s().sideCollisionWarning  ?? 0; },
                getBuckleStatus:            function() { return s().buckleStatus          ?? undefined; },
                getAnyDoorOpen:             function() { return s().anyDoorOpen           ?? undefined; },
                getAccSetSpeed:             function() { return s().accSetSpeed           ?? undefined; },
                getOdometer:                function() { return s().odometer              ?? undefined; },
                getPhoneBattery:            function() { return undefined; },
                getFullPackEnergy:          function() { return s().fullPackEnergy        ?? undefined; },
                getNominalEnergyRemaining:  function() { return s().nominalEnergyRemaining ?? undefined; },
                getEnergyBuffer:            function() { return s().energyBuffer          ?? undefined; },
                getPackTMin:                function() { return s().packTMin              ?? undefined; },
                getPackTMax:                function() { return s().packTMax              ?? undefined; },
                getMaxRegenPower:           function() { return s().maxRegenPower         ?? undefined; },
                getMaxDischargePower:       function() { return s().maxDischargePower     ?? undefined; },
                getPackVoltage:             function() { return s().packVoltage           ?? undefined; },
                getPackCurrent:             function() { return s().packCurrent           ?? undefined; },
                getShowPhoneBattery:        function() { return \(jsShowPhone); },
                getShowCarBattery:          function() { return \(jsShowBatt); },
                getShowOdometer:            function() { return \(jsShowOdo); },
                isImperial:                 function() { return \(jsImperial); },
                isDarkMode:                 function() { return \(jsDarkMode); },
                isAlwaysOnBlindSpotMonitor: function() { return \(jsBlindSpot); },
                getRenderQuality:           function() { return \(jsQuality); },
                getDarkModeBackgroundGray:  function() { return \(jsBgGray); },
            };
        })();
        """
        let script = WKUserScript(source: nativeBridge, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        config.userContentController.addUserScript(script)

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
            webView.load(URLRequest(url: URL(string: "app://localhost/index.html")!))
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
                        "window._iosCarState = \(json); window.onCarStateUpdate && window.onCarStateUpdate()",
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
