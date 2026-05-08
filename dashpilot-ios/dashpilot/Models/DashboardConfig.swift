import Foundation

enum DashboardType: String {
    case web
    case rive
    case devRive
}

struct DashboardCapabilities {
    var supports3dVisualizerSettings: Bool = false
    var supportsVehicleBusWidgets: Bool = false
    var supportsPhoneBatteryToggle: Bool = false
}

struct DashboardConfig: Identifiable {
    let id: String
    let name: String
    let url: String
    let type: DashboardType
    let screenshotName: String?
    let capabilities: DashboardCapabilities

    init(id: String, name: String, url: String, type: DashboardType, screenshotName: String? = nil, capabilities: DashboardCapabilities = DashboardCapabilities()) {
        self.id = id
        self.name = name
        self.url = url
        self.type = type
        self.screenshotName = screenshotName
        self.capabilities = capabilities
    }
}

let availableDashboards: [DashboardConfig] = [
    DashboardConfig(
        id: "expo",
        name: "Expo Dashboard",
        url: "https://dashpilot-expo.web.app",
        type: .web,
        screenshotName: "preview_expo"
    ),
    DashboardConfig(
        id: "vanilla",
        name: "Vanilla Dashboard",
        url: "vanilla",
        type: .web,
        screenshotName: "preview_vanilla",
        capabilities: DashboardCapabilities(
            supports3dVisualizerSettings: true,
            supportsVehicleBusWidgets: true,
            supportsPhoneBatteryToggle: true
        )
    ),
    DashboardConfig(
        id: "rive",
        name: "Rive Dashboard",
        url: "dashboard_test",
        type: .rive,
        screenshotName: "preview_rive"
    ),
    DashboardConfig(
        id: "dev_rive",
        name: "Load Rive File",
        url: "",
        type: .devRive,
        screenshotName: nil
    ),
]

func dashboardById(_ id: String?) -> DashboardConfig? {
    guard let id = id?.trimmingCharacters(in: .whitespaces), !id.isEmpty else { return nil }
    return availableDashboards.first { $0.id == id }
}

func selectedDashboard() -> DashboardConfig {
    let savedId = UserDefaults.standard.string(forKey: DisplaySettings.keySelectedDashboardId)
    return dashboardById(savedId) ?? availableDashboards[0]
}
