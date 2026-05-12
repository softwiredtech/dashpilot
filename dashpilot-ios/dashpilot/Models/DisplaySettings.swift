import Foundation

struct DisplaySettings {
    static let keyShowPhoneBattery = "show_phone_battery"
    static let keyShowCarBattery = "show_car_battery"
    static let keyShowOdometer = "show_odometer"
    static let keyUseImperial = "use_imperial"
    static let keyDarkMode = "dark_mode"
    static let keyAlwaysOnBlindSpotMonitor = "always_on_blind_spot_monitor"
    static let keyRenderQuality = "render_quality"
    static let keyDarkModeBackgroundGray = "dark_mode_background_gray"
    static let keyExtraVehicleBus = "extra_vehicle_bus"
    static let keySelectedDashboardId = "selected_dashboard_id"
}

struct DisplaySettingsState {
    var showPhoneBattery: Bool = true
    var showCarBattery: Bool = true
    var showOdometer: Bool = true
    var useImperial: Bool = false
    var darkMode: Bool = false
    var alwaysOnBlindSpotMonitor: Bool = true
    var renderQuality: Int = 3
    var darkModeBackgroundGray: Int = 0

    static func fromUserDefaults() -> DisplaySettingsState {
        let ud = UserDefaults.standard
        return DisplaySettingsState(
            showPhoneBattery: ud.object(forKey: DisplaySettings.keyShowPhoneBattery) == nil || ud.bool(forKey: DisplaySettings.keyShowPhoneBattery),
            showCarBattery: ud.object(forKey: DisplaySettings.keyShowCarBattery) == nil || ud.bool(forKey: DisplaySettings.keyShowCarBattery),
            showOdometer: ud.object(forKey: DisplaySettings.keyShowOdometer) == nil || ud.bool(forKey: DisplaySettings.keyShowOdometer),
            useImperial: ud.bool(forKey: DisplaySettings.keyUseImperial),
            darkMode: ud.bool(forKey: DisplaySettings.keyDarkMode),
            alwaysOnBlindSpotMonitor: ud.object(forKey: DisplaySettings.keyAlwaysOnBlindSpotMonitor) == nil || ud.bool(forKey: DisplaySettings.keyAlwaysOnBlindSpotMonitor),
            renderQuality: ud.object(forKey: DisplaySettings.keyRenderQuality) != nil ? ud.integer(forKey: DisplaySettings.keyRenderQuality) : 3,
            darkModeBackgroundGray: ud.integer(forKey: DisplaySettings.keyDarkModeBackgroundGray)
        )
    }
}
