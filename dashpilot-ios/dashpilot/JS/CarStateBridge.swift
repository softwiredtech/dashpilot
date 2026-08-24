import Foundation

extension DashState {
    func toJSONString() -> String {
        let cs = carState
        let ds = displaySettings
        let source = dataSourceType.map { "\"\($0.rawValue)\"" } ?? "null"
        return "{\"egoSteeringAngle\":\(cs.egoSteeringAngle),\"egoSpeed\":\(cs.egoSpeed),\"leftBlinker\":\(cs.leftBlinker),\"rightBlinker\":\(cs.rightBlinker),\"gear\":\(cs.gear),\"adasOn\":\(cs.adasOn),\"leftBlindSpot\":\(cs.leftBlindSpot),\"rightBlindSpot\":\(cs.rightBlindSpot),\"fusedSpeedLimit\":\(cs.fusedSpeedLimit),\"stopLineDist\":\(cs.stopLineDist),\"trafficLightColor\":\(cs.trafficLightColor),\"laneDepartureWarning\":\(cs.laneDepartureWarning),\"sideCollisionWarning\":\(cs.sideCollisionWarning),\"anyDoorOpen\":\(cs.anyDoorOpen),\"buckleStatus\":\(cs.buckleStatus),\"accSetSpeed\":\(cs.accSetSpeed),\"fullPackEnergy\":\(cs.fullPackEnergy),\"nominalEnergyRemaining\":\(cs.nominalEnergyRemaining),\"energyBuffer\":\(cs.energyBuffer),\"maxRegenPower\":\(cs.maxRegenPower),\"maxDischargePower\":\(cs.maxDischargePower),\"packVoltage\":\(cs.packVoltage),\"packCurrent\":\(cs.packCurrent),\"packTMin\":\(cs.packTMin),\"packTMax\":\(cs.packTMax),\"odometer\":\(cs.odometer),\"madsActive\":\(cs.madsActive),\"selfdriveActive\":\(cs.selfdriveActive),\"experimentalMode\":\(cs.experimentalMode),\"changingLane\":\(cs.changingLane),\"phoneBattery\":\(phoneBattery),\"currentTime\":\(currentTime),\"dataSourceType\":\(source),\"showPhoneBattery\":\(ds.showPhoneBattery),\"showCarBattery\":\(ds.showCarBattery),\"showOdometer\":\(ds.showOdometer),\"isImperial\":\(ds.useImperial),\"darkMode\":\(ds.darkMode),\"alwaysOnBlindSpotMonitor\":\(ds.alwaysOnBlindSpotMonitor),\"renderQuality\":\(ds.renderQuality),\"darkModeBackgroundGray\":\(ds.darkModeBackgroundGray)}"
    }
}
