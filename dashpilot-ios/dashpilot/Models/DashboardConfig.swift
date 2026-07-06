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
        name: "Vanilla Dashboard",
        url: "vanilla",
        type: .web,
        screenshotName: "preview_vanilla"
    ),
    DashboardConfig(
        id: "ambient",
        name: "Ambient Dashboard",
        url: "ambient",
        type: .web,
        screenshotName: "preview_ambient"
    ),
    DashboardConfig(
        id: "analog",
        name: "Analog Dashboard",
        url: "analog",
        type: .web,
        screenshotName: "preview_analog"
    ),
    DashboardConfig(
        id: "retro",
        name: "Retro Dashboard",
        url: "retro",
        type: .web,
        screenshotName: "preview_retro"
    ),
    DashboardConfig(
        id: "dev_rive",
        name: "Load Rive File",
        url: "",
        type: .devRive,
        screenshotName: nil
    ),
]

// Written once at app startup by dashpilotApp.init() via setLoadedManifests().
// Reads happen via dashboardById() — always use that to get a hydrated config.
private var loadedManifests: [String: DashboardManifest] = [:]

func setLoadedManifests(_ map: [String: DashboardManifest]) {
    loadedManifests = map
}

private let defaultDashboardId = "vanilla"

func dashboardById(_ id: String?) -> DashboardConfig? {
    guard let id = id?.trimmingCharacters(in: .whitespaces), !id.isEmpty else { return nil }
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
