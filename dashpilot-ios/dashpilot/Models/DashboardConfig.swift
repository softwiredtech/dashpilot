import Foundation

enum DashboardType: String {
    case web
    case rive
    case devRive
}

struct DashboardConfig: Identifiable {
    let id: String
    let name: String
    let url: String
    let type: DashboardType
    let screenshotName: String?
    var manifest: DashboardManifest? = nil
}

let availableDashboards: [DashboardConfig] = [
    DashboardConfig(
        id: "vanilla",
        name: String(localized: "Vanilla Dashboard"),
        url: "vanilla",
        type: .web,
        screenshotName: "preview_vanilla"
    ),
    DashboardConfig(
        id: "ambient",
        name: String(localized: "Ambient Dashboard"),
        url: "ambient",
        type: .web,
        screenshotName: "preview_ambient"
    ),
    DashboardConfig(
        id: "analog",
        name: String(localized: "Analog Dashboard"),
        url: "analog",
        type: .web,
        screenshotName: "preview_analog"
    ),
    DashboardConfig(
        id: "compose",
        name: String(localized: "Custom Dashboard"),
        url: "compose",
        type: .web,
        screenshotName: "preview_compose"
    ),
    DashboardConfig(
        id: "retro",
        name: String(localized: "Retro Dashboard"),
        url: "retro",
        type: .web,
        screenshotName: "preview_retro"
    ),
    DashboardConfig(
        id: "rive",
        name: String(localized: "Rive"),
        url: "dash_demo",
        type: .rive,
        screenshotName: "preview_rive"
    ),
]

let devRiveDashboardId = "dev_rive"
let devUrlDashboardId = "dev_url"
let devDashboardUrlKey = "devDashboardUrl"

private func developerDashboard(_ id: String) -> DashboardConfig? {
    switch id {
    case devRiveDashboardId:
        guard FileManager.default.fileExists(atPath: DevRiveFile.url.path) else { return nil }
        return DashboardConfig(id: id, name: String(localized: "Rive File"), url: "", type: .devRive, screenshotName: nil)
    case devUrlDashboardId:
        guard let url = UserDefaults.standard.string(forKey: devDashboardUrlKey), !url.isEmpty else { return nil }
        return DashboardConfig(id: id, name: String(localized: "Dash App URL"), url: url, type: .web, screenshotName: nil)
    default:
        return nil
    }
}

// Written once at app startup by dashpilotApp.init() via setLoadedManifests().
// Reads happen via dashboardById() — always use that to get a hydrated config.
private var loadedManifests: [String: DashboardManifest] = [:]

func setLoadedManifests(_ map: [String: DashboardManifest]) {
    loadedManifests = map
}

private let defaultDashboardId = "vanilla"

func dashboardById(_ id: String?) -> DashboardConfig? {
    guard let id = id?.trimmingCharacters(in: .whitespaces), !id.isEmpty else { return nil }
    if let dev = developerDashboard(id) { return dev }
    guard var config = availableDashboards.first(where: { $0.id == id }) else { return nil }
    if config.type == .web {
        config.manifest = loadedManifests[id]
    }
    return config
}

func selectedDashboard() -> DashboardConfig {
    let savedId = UserDefaults.standard.string(forKey: DisplaySettings.keySelectedDashboardId)
    return dashboardById(savedId) ?? dashboardById(defaultDashboardId) ?? availableDashboards[0]
}
