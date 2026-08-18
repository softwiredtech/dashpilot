enum AppRoute: Hashable {
    case dashboardSelection
    case dashboard(type: String, url: String)
    case settings
    case themePicker
    case battery
    case automations
    case controls
    case setup
}
