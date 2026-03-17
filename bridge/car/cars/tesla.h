#pragma once

#include "car/car_state_mapper.h"

class TeslaCarState : public CarStateMapper {
public:
    void update(const CANParsers& cp, CarState& cs) override {
        cs.egoSteeringAngle = cp.get("SCCM_steeringAngleSensor", "SCCM_steeringAngle");
        cs.gear = cp.get("DI_systemStatus", "DI_gear");
        cs.egoSpeed = cp.get("DI_speed", "DI_uiSpeed");
        cs.adasOn = cp.get("DAS_control", "DAS_accState") > 0 ? 1.0 : 0.0;
        cs.leftBlindSpot = cp.get("DAS_status", "DAS_blindSpotRearLeft");
        cs.rightBlindSpot = cp.get("DAS_status", "DAS_blindSpotRearRight");
        cs.fusedSpeedLimit = cp.get("DAS_status", "DAS_fusedSpeedLimit");
        cs.trafficLightColor = cp.get("DAS_road", "DAS_trafficLightColor");
        cs.stopLineDist = cp.get("DAS_road", "DAS_stopLineDist");
        cs.leftBlinker = cp.get("UI_warning", "leftBlinkerBlinking");
        cs.rightBlinker = cp.get("UI_warning", "rightBlinkerBlinking");
        cs.laneDepartureWarning = cp.get("DAS_status", "DAS_laneDepartureWarning");
        cs.sideCollisionWarning = cp.get("DAS_status", "DAS_sideCollisionWarning");
        cs.anyDoorOpen = cp.get("UI_warning", "anyDoorOpen");
        cs.sideCollisionWarning = cp.get("UI_warning", "buckleStatus");
    }
};
