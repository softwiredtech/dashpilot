import Foundation

struct CarState: Codable {
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

    static let fieldCount = 16

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        egoSteeringAngle    = try c.decodeIfPresent(Float.self, forKey: .egoSteeringAngle)    ?? 0
        egoSpeed            = try c.decodeIfPresent(Float.self, forKey: .egoSpeed)            ?? 0
        leftBlinker         = try c.decodeIfPresent(Float.self, forKey: .leftBlinker)         ?? 0
        rightBlinker        = try c.decodeIfPresent(Float.self, forKey: .rightBlinker)        ?? 0
        gear                = try c.decodeIfPresent(Float.self, forKey: .gear)                ?? 0
        adasOn              = try c.decodeIfPresent(Bool.self,  forKey: .adasOn)              ?? false
        leftBlindSpot       = try c.decodeIfPresent(Float.self, forKey: .leftBlindSpot)       ?? 0
        rightBlindSpot      = try c.decodeIfPresent(Float.self, forKey: .rightBlindSpot)      ?? 0
        fusedSpeedLimit     = try c.decodeIfPresent(Float.self, forKey: .fusedSpeedLimit)     ?? 0
        stopLineDist        = try c.decodeIfPresent(Float.self, forKey: .stopLineDist)        ?? 0
        trafficLightColor   = try c.decodeIfPresent(Float.self, forKey: .trafficLightColor)   ?? 0
        laneDepartureWarning = try c.decodeIfPresent(Float.self, forKey: .laneDepartureWarning) ?? 0
        sideCollisionWarning = try c.decodeIfPresent(Float.self, forKey: .sideCollisionWarning) ?? 0
        anyDoorOpen         = try c.decodeIfPresent(Float.self, forKey: .anyDoorOpen)         ?? 0
        buckleStatus        = try c.decodeIfPresent(Float.self, forKey: .buckleStatus)        ?? 0
        accSetSpeed         = try c.decodeIfPresent(Float.self, forKey: .accSetSpeed)         ?? 0
    }
}
