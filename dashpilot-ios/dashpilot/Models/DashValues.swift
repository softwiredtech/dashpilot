import Foundation

enum DashValue {
    case number(Float)
    case bool(Bool)
    case string(String)
}

extension DashState {
    func dashValues() -> [(String, DashValue)] {
        let c = carState
        let d = displaySettings
        return [
            ("egoSpeed", .number(c.egoSpeed)),
            ("gear", .number(c.gear)),
            ("egoSteeringAngle", .number(c.egoSteeringAngle)),
            ("leftBlinker", .number(c.leftBlinker)),
            ("rightBlinker", .number(c.rightBlinker)),
            ("anyDoorOpen", .number(c.anyDoorOpen)),
            ("buckleStatus", .number(c.buckleStatus)),
            ("odometer", .number(c.odometer)),
            ("adasOn", .bool(c.adasOn)),
            ("accSetSpeed", .number(c.accSetSpeed)),
            ("fusedSpeedLimit", .number(c.fusedSpeedLimit)),
            ("leftBlindSpot", .number(c.leftBlindSpot)),
            ("rightBlindSpot", .number(c.rightBlindSpot)),
            ("trafficLightColor", .number(c.trafficLightColor)),
            ("stopLineDist", .number(c.stopLineDist)),
            ("changingLane", .bool(c.changingLane)),
            ("madsActive", .bool(c.madsActive)),
            ("experimentalMode", .bool(c.experimentalMode)),
            ("selfdriveActive", .bool(c.selfdriveActive)),
            ("nominalEnergyRemaining", .number(c.nominalEnergyRemaining)),
            ("fullPackEnergy", .number(c.fullPackEnergy)),
            ("energyBuffer", .number(c.energyBuffer)),
            ("powerLevel", .number(c.powerLevel)),
            ("packVoltage", .number(c.packVoltage)),
            ("packCurrent", .number(c.packCurrent)),
            ("maxRegenPower", .number(c.maxRegenPower)),
            ("maxDischargePower", .number(c.maxDischargePower)),
            ("packTMin", .number(c.packTMin)),
            ("packTMax", .number(c.packTMax)),
            // Rive numbers are 32-bit floats, too coarse for epoch milliseconds.
            ("currentTime", .number(Self.secondsSinceLocalMidnight(currentTime))),
            ("phoneBattery", .number(Float(phoneBattery))),
            ("isImperial", .bool(d.useImperial)),
            ("darkMode", .bool(d.darkMode)),
            ("darkModeBackgroundGray", .number(Float(d.darkModeBackgroundGray))),
            ("showPhoneBattery", .bool(d.showPhoneBattery)),
            ("showCarBattery", .bool(d.showCarBattery)),
            ("showOdometer", .bool(d.showOdometer)),
            ("alwaysOnBlindSpotMonitor", .bool(d.alwaysOnBlindSpotMonitor)),
            ("renderQuality", .number(Float(d.renderQuality))),
            ("dataSourceType", .string(dataSourceType?.rawValue ?? "")),
        ]
    }

    private static func secondsSinceLocalMidnight(_ epochMillis: Int64) -> Float {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000)
        return Float(date.timeIntervalSince(Calendar.current.startOfDay(for: date)))
    }
}
