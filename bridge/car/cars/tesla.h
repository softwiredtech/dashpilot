#pragma once

#include "car/car_state_mapper.h"

static inline void updatePartyBus(const CANParsers& cp, CarState& cs) {
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

class TeslaCommaPartyMapper : public CarStateMapper {
public:
    void update(const CANParsers& cp, CarState& cs) override {
        updatePartyBus(cp, cs);
    }
};

class TeslaCommaExtraMapper : public CarStateMapper {
public:
    void update(const CANParsers& cp, CarState& cs) override {
        updatePartyBus(cp, cs);

        // Vehicle bus specific signals
        // BMS_energyStatus is multiplexed: mux 0 has pack/remaining energy, mux 1 has energyBuffer
        int muxIdx = static_cast<int>(cp.get(1, "BMS_energyStatus", "BMS_energyStatusIndex"));
        if (muxIdx == 0) {
            cs.fullPackEnergy = cp.get(1, "BMS_energyStatus", "BMS_nominalFullPackEnergy");
            cs.nominalEnergyRemaining = cp.get(1, "BMS_energyStatus", "BMS_nominalEnergyRemaining");
        } else if (muxIdx == 1) {
            cs.energyBuffer = cp.get(1, "BMS_energyStatus", "BMS_energyBuffer");
        }
        cs.maxRegenPower = cp.get(1, "BMS_powerAvailable", "BMS_maxRegenPower");
        cs.maxDischargePower = cp.get(1, "BMS_powerAvailable", "BMS_maxDischargePower");
        cs.packVoltage = cp.get(1, "BMS_hvBusStatus", "BMS_packVoltage");
        cs.packCurrent = cp.get(1, "BMS_hvBusStatus", "BMS_packCurrent");
        cs.packTMin = cp.get(1, "BMS_bmbMinMax", "BMS_thermistorTMin");
        cs.packTMax = cp.get(1, "BMS_bmbMinMax", "BMS_thermistorTMax");
        cs.odometer = cp.get(1, "DI_odometerStatus", "DI_odometer");
    }
};
