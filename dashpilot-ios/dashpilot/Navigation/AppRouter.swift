enum AppRoute: Hashable {
    case dashboardSelection
    case dashboard(type: String, url: String)
    case settings
    case themePicker
    case battery
    case climate
    case automations
    case controls
    case setup
}

/// Opens once per departure from Park; unknown gear values never trigger a drive.
struct AutoStartDriveGate {
    private var started = false

    mutating func shouldStart(enabled: Bool, connected: Bool, gear: Float?, canNavigate: Bool, dashboardVisible: Bool) -> Bool {
        if !enabled || !connected || gear == 1 {
            started = false
            return false
        }
        guard gear == 2 || gear == 3 || gear == 4 else { return false }
        guard canNavigate, !started else { return false }
        started = true
        return !dashboardVisible
    }
}
