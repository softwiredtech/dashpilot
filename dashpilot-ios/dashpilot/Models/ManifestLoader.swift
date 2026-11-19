import Foundation

struct ManifestLoader {

    // Loads manifest.json for each dashboard ID from the app bundle.
    // Expects files at {folderPrefix}-{id}/manifest.json in Copy Bundle Resources.
    // Missing or unparseable manifests are logged and skipped; the rest load normally.
    static func loadFromBundle(dashboardIds: [String], folderPrefix: String, optional: Bool = false) -> [String: DashboardManifest] {
        var result: [String: DashboardManifest] = [:]
        for id in dashboardIds {
            let subdirectory = "\(folderPrefix)-\(id)"
            guard let url = Bundle.main.url(
                forResource: "manifest",
                withExtension: "json",
                subdirectory: subdirectory
            ) else {
                if optional { continue }
                print("[DashPilot] Missing manifest for '\(id)' — add \(subdirectory)/manifest.json to the Xcode target")
                continue
            }
            do {
                let json = try String(contentsOf: url, encoding: .utf8)
                let manifest = try DashboardManifest.parse(json)
                guard manifest.id == id else {
                    print("[DashPilot] Manifest at \(subdirectory)/manifest.json declares id '\(manifest.id)' but expected '\(id)'")
                    continue
                }
                result[id] = manifest
            } catch {
                print("[DashPilot] Failed to parse manifest for '\(id)': \(error)")
            }
        }
        return result
    }
}
