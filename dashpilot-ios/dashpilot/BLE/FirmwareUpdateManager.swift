import Foundation
import CryptoKit
import Observation

/// A single firmware release as described by the update manifest hosted on the
/// server. Only `version` and `url` are required; the rest are optional.
struct FirmwareManifest: Codable, Equatable {
    let version: String
    let url: String
    var sha256: String?
    var notes: String?
    var minAppVersion: String?
}

/// Fetches the firmware update manifest and downloads firmware binaries
/// (port of the Android `FirmwareUpdateRepository`).
enum FirmwareUpdateRepository {

    static let manifestURL =
        "https://firebasestorage.googleapis.com/v0/b/xreport-f792c.appspot.com/o/dashkit%2Fmanifest.json?alt=media&token=68bcc03a-3951-454d-8f35-f5ab06c0ed0a"

    /// Download and parse the update manifest. Returns nil on any failure.
    static func fetchManifest() async -> FirmwareManifest? {
        guard let url = URL(string: manifestURL) else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                print("[FirmwareUpdateRepo] manifest fetch failed: bad status")
                return nil
            }
            return try JSONDecoder().decode(FirmwareManifest.self, from: data)
        } catch {
            print("[FirmwareUpdateRepo] manifest fetch error: \(error.localizedDescription)")
            return nil
        }
    }

    /// Download the firmware binary. Throws on failure so the caller can
    /// surface an error. (Firmware images are a few MB, so a plain download
    /// without incremental progress is fine — the UI shows an indeterminate
    /// "downloading" state.)
    static func downloadBinary(_ urlString: String) async throws -> Data {
        guard let url = URL(string: urlString) else {
            throw URLError(.badURL)
        }
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }
}

/// Orchestrates the end-to-end firmware update flow (port of the Android
/// `FirmwareUpdateManager`):
///  1. read the installed version over BLE (CADA0005),
///  2. fetch the release manifest from the server,
///  3. SemVer-compare to decide if an update is available,
///  4. download the binary,
///  5. hand the bytes to the `DashKitOtaUpdate` BLE upload path.
///
/// One instance per connected `DashKitBleManager`, disposed when the settings
/// screen goes away.
@Observable
final class FirmwareUpdateManager {

    enum Check: Equatable {
        case idle
        case checking
        case upToDate
        case available(FirmwareManifest)
        case error(String)
    }

    let ota: DashKitOtaUpdate
    let versionReader: DashKitFirmwareVersion

    private(set) var check: Check = .idle
    private(set) var downloading = false

    var otaState: OtaState { ota.state }
    var installedVersion: String? { versionReader.version }

    init(manager: DashKitBleManager) {
        ota = DashKitOtaUpdate(manager: manager)
        versionReader = DashKitFirmwareVersion(manager: manager)
    }

    /// Begin reading the installed firmware version over BLE.
    func start() {
        versionReader.read()
    }

    /// Fetch the manifest and compare with the installed version. Reads the
    /// installed version first (waiting briefly if it hasn't arrived yet).
    @MainActor
    func checkForUpdate() async {
        check = .checking
        guard let manifest = await FirmwareUpdateRepository.fetchManifest() else {
            check = .error("Could not reach update server")
            return
        }
        var current = installedVersion
        if current == nil {
            current = await versionReader.awaitVersion()
        }
        guard let current else {
            print("[FwUpdateManager] installed version unknown; offering update anyway")
            check = .available(manifest)
            return
        }
        check = SemVer.isNewer(manifest.version, than: current) ? .available(manifest) : .upToDate
    }

    /// Download the firmware described by `manifest`, verify its SHA-256 if
    /// the manifest provides one, then start the BLE upload.
    @MainActor
    func install(_ manifest: FirmwareManifest) async {
        downloading = true
        defer { downloading = false }
        do {
            let bytes = try await FirmwareUpdateRepository.downloadBinary(manifest.url)
            if let expected = manifest.sha256?.trimmingCharacters(in: .whitespaces).lowercased(),
               !expected.isEmpty {
                let actual = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
                guard actual == expected else {
                    check = .error("Downloaded firmware failed integrity check")
                    return
                }
            }
            ota.start(firmware: bytes)
        } catch {
            check = .error("Download failed: \(error.localizedDescription)")
        }
    }

    func cancel() {
        ota.cancel()
    }

    func dispose() {
        ota.cancel()
        versionReader.dispose()
    }
}
