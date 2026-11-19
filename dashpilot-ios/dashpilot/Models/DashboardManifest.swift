import Foundation

enum ManifestDisplaySetting: String, Codable, Hashable {
    case showPhoneBattery
    case showCarBattery
    case showOdometer
    case alwaysOnBlindSpotMonitor
    case useImperial
    case darkMode
    case renderQuality
    case darkModeBackgroundGray
}

enum ManifestRequirement: String, Codable, Hashable {
    case gpuCanvas
}

enum ManifestParseError: Error {
    case invalidData
    case unsupportedVersion(Int)
}

struct DashboardManifest: Codable {
    let manifestVersion: Int
    let id: String
    let settings: Set<ManifestDisplaySetting>
    let requires: Set<ManifestRequirement>?

    static func parse(_ json: String) throws -> DashboardManifest {
        guard let data = json.data(using: .utf8) else {
            throw ManifestParseError.invalidData
        }
        let manifest = try JSONDecoder().decode(DashboardManifest.self, from: data)
        guard manifest.manifestVersion == 1 else {
            throw ManifestParseError.unsupportedVersion(manifest.manifestVersion)
        }
        return manifest
    }
}
