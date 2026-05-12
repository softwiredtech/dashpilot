import Foundation

struct CarState: Codable {
    // Party bus (ADAS)
    var egoSteeringAngle: Float = 0
    var egoSpeed: Float = 0
    var leftBlinker: Float = 0
    var rightBlinker: Float = 0
    var gear: Float = 0
    var adasOn: Bool = false
    var leftBlindSpot: Float = 0
    var rightBlindSpot: Float = 0
    var fusedSpeedLimit: Float = 0
    var stopLineDist: Float = 0
    var trafficLightColor: Float = 0
    var laneDepartureWarning: Float = 0
    var sideCollisionWarning: Float = 0
    var anyDoorOpen: Float = 0
    var buckleStatus: Float = 0
    var accSetSpeed: Float = 0

    // Vehicle bus (BMS/Power)
    var fullPackEnergy: Float = 0
    var nominalEnergyRemaining: Float = 0
    var energyBuffer: Float = 0
    var maxRegenPower: Float = 0
    var maxDischargePower: Float = 0
    var packVoltage: Float = 0
    var packCurrent: Float = 0
    var packTMin: Float = 0
    var packTMax: Float = 0
    var odometer: Float = 0

    // Openpilot state
    var madsActive: Bool = false
    var selfdriveActive: Bool = false
    var experimentalMode: Bool = false
    var changingLane: Bool = false

    init() {}

    init(from bridge: BridgeCarState) {
        egoSteeringAngle = Float(bridge.egoSteeringAngle)
        egoSpeed = Float(bridge.egoSpeed)
        leftBlinker = Float(bridge.leftBlinker)
        rightBlinker = Float(bridge.rightBlinker)
        gear = Float(bridge.gear)
        adasOn = bridge.adasOn > 0
        leftBlindSpot = Float(bridge.leftBlindSpot)
        rightBlindSpot = Float(bridge.rightBlindSpot)
        fusedSpeedLimit = Float(bridge.fusedSpeedLimit)
        stopLineDist = Float(bridge.stopLineDist)
        trafficLightColor = Float(bridge.trafficLightColor)
        laneDepartureWarning = Float(bridge.laneDepartureWarning)
        sideCollisionWarning = Float(bridge.sideCollisionWarning)
        anyDoorOpen = Float(bridge.anyDoorOpen)
        buckleStatus = Float(bridge.buckleStatus)
        accSetSpeed = Float(bridge.accSetSpeed)
        fullPackEnergy = Float(bridge.fullPackEnergy)
        nominalEnergyRemaining = Float(bridge.nominalEnergyRemaining)
        energyBuffer = Float(bridge.energyBuffer)
        maxRegenPower = Float(bridge.maxRegenPower)
        maxDischargePower = Float(bridge.maxDischargePower)
        packVoltage = Float(bridge.packVoltage)
        packCurrent = Float(bridge.packCurrent)
        packTMin = Float(bridge.packTMin)
        packTMax = Float(bridge.packTMax)
        odometer = Float(bridge.odometer)
        madsActive = bridge.madsActive > 0
        selfdriveActive = bridge.selfdriveActive > 0
        experimentalMode = bridge.experimentalMode > 0
        changingLane = bridge.changingLane > 0
    }

    private static let kmToMiles: Float = 0.621371

    private static let milesCountries: Set<String> = ["US", "GB", "MM", "LR"]

    private static func speedLimitSignsInKm() -> Bool {
        let country = Locale.current.region?.identifier ?? ""
        return !milesCountries.contains(country)
    }

    func toImperial() -> CarState {
        var c = self
        c.odometer = odometer * Self.kmToMiles
        c.packTMin = packTMin != 0 ? packTMin * 1.8 + 32 : 0
        c.packTMax = packTMax != 0 ? packTMax * 1.8 + 32 : 0
        if Self.speedLimitSignsInKm() {
            c.fusedSpeedLimit = fusedSpeedLimit * Self.kmToMiles
        }
        return c
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        egoSteeringAngle     = try c.decodeIfPresent(Float.self, forKey: .egoSteeringAngle)     ?? 0
        egoSpeed             = try c.decodeIfPresent(Float.self, forKey: .egoSpeed)             ?? 0
        leftBlinker          = try c.decodeIfPresent(Float.self, forKey: .leftBlinker)          ?? 0
        rightBlinker         = try c.decodeIfPresent(Float.self, forKey: .rightBlinker)         ?? 0
        gear                 = try c.decodeIfPresent(Float.self, forKey: .gear)                 ?? 0
        adasOn               = try c.decodeIfPresent(Bool.self,  forKey: .adasOn)               ?? false
        leftBlindSpot        = try c.decodeIfPresent(Float.self, forKey: .leftBlindSpot)        ?? 0
        rightBlindSpot       = try c.decodeIfPresent(Float.self, forKey: .rightBlindSpot)       ?? 0
        fusedSpeedLimit      = try c.decodeIfPresent(Float.self, forKey: .fusedSpeedLimit)      ?? 0
        stopLineDist         = try c.decodeIfPresent(Float.self, forKey: .stopLineDist)         ?? 0
        trafficLightColor    = try c.decodeIfPresent(Float.self, forKey: .trafficLightColor)    ?? 0
        laneDepartureWarning = try c.decodeIfPresent(Float.self, forKey: .laneDepartureWarning) ?? 0
        sideCollisionWarning = try c.decodeIfPresent(Float.self, forKey: .sideCollisionWarning) ?? 0
        anyDoorOpen          = try c.decodeIfPresent(Float.self, forKey: .anyDoorOpen)          ?? 0
        buckleStatus         = try c.decodeIfPresent(Float.self, forKey: .buckleStatus)         ?? 0
        accSetSpeed          = try c.decodeIfPresent(Float.self, forKey: .accSetSpeed)          ?? 0
        fullPackEnergy       = try c.decodeIfPresent(Float.self, forKey: .fullPackEnergy)       ?? 0
        nominalEnergyRemaining = try c.decodeIfPresent(Float.self, forKey: .nominalEnergyRemaining) ?? 0
        energyBuffer         = try c.decodeIfPresent(Float.self, forKey: .energyBuffer)         ?? 0
        maxRegenPower        = try c.decodeIfPresent(Float.self, forKey: .maxRegenPower)        ?? 0
        maxDischargePower    = try c.decodeIfPresent(Float.self, forKey: .maxDischargePower)    ?? 0
        packVoltage          = try c.decodeIfPresent(Float.self, forKey: .packVoltage)          ?? 0
        packCurrent          = try c.decodeIfPresent(Float.self, forKey: .packCurrent)          ?? 0
        packTMin             = try c.decodeIfPresent(Float.self, forKey: .packTMin)             ?? 0
        packTMax             = try c.decodeIfPresent(Float.self, forKey: .packTMax)             ?? 0
        odometer             = try c.decodeIfPresent(Float.self, forKey: .odometer)             ?? 0
        madsActive           = try c.decodeIfPresent(Bool.self,  forKey: .madsActive)           ?? false
        selfdriveActive      = try c.decodeIfPresent(Bool.self,  forKey: .selfdriveActive)      ?? false
        experimentalMode     = try c.decodeIfPresent(Bool.self,  forKey: .experimentalMode)     ?? false
        changingLane         = try c.decodeIfPresent(Bool.self,  forKey: .changingLane)         ?? false
    }
}
