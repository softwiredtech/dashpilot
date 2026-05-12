import Foundation

struct DashState {
    var carState: CarState = CarState()
    var displaySettings: DisplaySettingsState = DisplaySettingsState()
    var phoneBattery: Int = -1
    var currentTime: Int64 = 0
}
