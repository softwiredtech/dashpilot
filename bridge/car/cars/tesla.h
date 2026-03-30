#pragma once

#include "car/car_state_mapper.h"

class TeslaCarState : public CarStateMapper {
public:
    void update(const CANParsers& cp, CarState& cs) override {
        cs.egoSteeringAngle = cp.get(2, "SCCM_steeringAngleSensor", "SCCM_steeringAngle");
        cs.gear = cp.get(2, "DI_systemStatus", "DI_gear");
        cs.egoSpeed = cp.get(2, "DI_speed", "DI_uiSpeed");
        cs.adasOn = cp.get(2, "DI_state", "DI_cruiseState") == 2.0 ? 1.0 : 0.0;
        cs.leftBlindSpot = cp.get(2, "DAS_status", "DAS_blindSpotRearLeft");
        cs.rightBlindSpot = cp.get(2, "DAS_status", "DAS_blindSpotRearRight");
        cs.fusedSpeedLimit = cp.get(2, "DAS_status", "DAS_fusedSpeedLimit");
        cs.trafficLightColor = cp.get(2, "DAS_road", "DAS_trafficLightColor");
        cs.stopLineDist = cp.get(2, "DAS_road", "DAS_stopLineDist");
        cs.leftBlinker = cp.get(2, "UI_warning", "leftBlinkerBlinking");
        cs.rightBlinker = cp.get(2, "UI_warning", "rightBlinkerBlinking");
        cs.laneDepartureWarning = cp.get(2, "DAS_status", "DAS_laneDepartureWarning");
        cs.sideCollisionWarning = cp.get(2, "DAS_status", "DAS_sideCollisionWarning");
        cs.anyDoorOpen = cp.get(2, "UI_warning", "anyDoorOpen");
        cs.buckleStatus = cp.get(2, "UI_warning", "buckleStatus");
        cs.accSetSpeed = cp.get(2, "DI_state", "DI_digitalSpeed");
    }
};
