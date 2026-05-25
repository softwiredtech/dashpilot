import Foundation

struct ManifestLoader {

    // Loads manifest.json for each dashboard ID from the app bundle.
    // Expects files at web-{id}/manifest.json in Copy Bundle Resources.
    // Missing or unparseable manifests are logged and skipped; the rest load normally.
    static func loadFromBundle(dashboardIds: [String]) -> [String: DashboardManifest] {
        var result: [String: DashboardManifest] = [:]
        for id in dashboardIds {
            let subdirectory = "web-\(id)"
            guard let url = Bundle.main.url(
                forResource: "manifest",
                withExtension: "json",
                subdirectory: subdirectory
            ) else {
                print("[DashPilot] Missing manifest for '\(id)' — add \(subdirectory)/manifest.json to the Xcode target")
                continue
            }
            do {
                let json = try String(contentsOf: url, encoding: .utf8)
                result[id] = try DashboardManifest.parse(json)
            } catch {
                print("[DashPilot] Failed to parse manifest for '\(id)': \(error)")
            }
        }
        return result
    }
}
