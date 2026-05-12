import Foundation
import Network

enum NetworkUtil {

    /// Returns the device's WiFi IPv4 address, or nil if unavailable.
    static func getWifiIPAddress() -> String? {
        var address: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }

        for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let iface = ptr.pointee
            let family = iface.ifa_addr.pointee.sa_family
            guard family == UInt8(AF_INET) else { continue }

            let name = String(cString: iface.ifa_name)
            // en0 is WiFi on iOS
            guard name == "en0" else { continue }

            var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(iface.ifa_addr, socklen_t(iface.ifa_addr.pointee.sa_len),
                           &hostname, socklen_t(hostname.count),
                           nil, 0, NI_NUMERICHOST) == 0 {
                address = String(cString: hostname)
            }
        }
        return address
    }

    /// Discovers a ZMQ publisher on the local subnet using the bridge's discoverPublisher.
    /// Runs on a background thread. Returns the discovered IP or nil on timeout.
    static func findPublisher(
        endpoint: String = "can",
        timeoutMs: Int32 = 5000
    ) async -> String? {
        guard let localIp = getWifiIPAddress() else {
            print("[NetworkUtil] no WiFi IP available")
            return nil
        }
        print("[NetworkUtil] local IP: \(localIp), scanning subnet...")

        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let ctx = bridge_create_context()
                let result = bridge_discover_publisher(ctx, endpoint, localIp, timeoutMs)
                bridge_delete_context(ctx)

                if let result {
                    let ip = String(cString: result)
                    free(result)
                    print("[NetworkUtil] discovered publisher at \(ip)")
                    continuation.resume(returning: ip)
                } else {
                    print("[NetworkUtil] no publisher found")
                    continuation.resume(returning: nil)
                }
            }
        }
    }
}
