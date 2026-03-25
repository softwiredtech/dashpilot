import Foundation

enum DashboardType: String {
    case web
    case rive
    case devRive
}

struct DashboardConfig: Identifiable {
    let id = UUID()
    let name: String
    let url: String
    let type: DashboardType
    let screenshotName: String?
}

let availableDashboards: [DashboardConfig] = [
    DashboardConfig(
        name: "Expo Dashboard",
        url: "https://dashpilot-expo.web.app",
        type: .web,
        screenshotName: "preview_expo"
    ),
    DashboardConfig(
        name: "Vanilla Dashboard",
        url: "vanilla",
        type: .web,
        screenshotName: "preview_vanilla"
    ),
    DashboardConfig(
        name: "Rive Dashboard",
        url: "dashboard_test",
        type: .rive,
        screenshotName: "preview_rive"
    ),
    DashboardConfig(
        name: "Load Rive File",
        url: "",
        type: .devRive,
        screenshotName: nil
    ),
]
