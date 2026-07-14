enum AppRoute: Hashable {
    case dashboardSelection
    case dashboard(type: String, url: String)
    case settings
    case themePicker
}
