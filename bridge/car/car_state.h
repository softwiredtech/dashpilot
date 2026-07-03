#pragma once

#include <cstddef>

struct CarState {
    // Party bus
    double egoSteeringAngle = 0;
    double egoSpeed = 0;
    double leftBlinker = 0;
    double rightBlinker = 0;
    double gear = 0;
    double adasOn = 0;
    double leftBlindSpot = 0;
    double rightBlindSpot = 0;
    double fusedSpeedLimit = 0;
    double stopLineDist = 0;
    double trafficLightColor = 0;
    double laneDepartureWarning = 0;
    double sideCollisionWarning = 0;
    double anyDoorOpen = 0;
    double buckleStatus = 0;
    double accSetSpeed = 0;

    // Vehicle bus
    double fullPackEnergy = 0;
    double nominalEnergyRemaining = 0;
    double energyBuffer = 0;
    double maxRegenPower = 0;
    double maxDischargePower = 0;
    double packVoltage = 0;
    double packCurrent = 0;
    double packTMin = 0;
    double packTMax = 0;
    double odometer = 0;
    double acTemp = 0;

    // Openpilot state
    double madsActive = 0;
    double selfdriveActive = 0;
    double experimentalMode = 0;
    double changingLane = 0;

    // Virtual lane geometry (DAS_lanes) — forwarded to the adasviz renderer.
    double virtualLaneWidth = 0;      // m
    double virtualLaneViewRange = 0;  // m
    double virtualLaneC0 = 0;         // cm  (lateral offset at x=0)
    double virtualLaneC1 = 0;         // deg (heading offset)
    double virtualLaneC2 = 0;         // m-1 (curvature)
    double virtualLaneC3 = 0;         // m-2 (curvature rate)

    static constexpr size_t FIELD_COUNT = 37;

    void toArray(double* out) const {
        // Party bus
        out[0] = egoSteeringAngle;
        out[1] = egoSpeed;
        out[2] = leftBlinker;
        out[3] = rightBlinker;
        out[4] = gear;
        out[5] = adasOn;
        out[6] = leftBlindSpot;
        out[7] = rightBlindSpot;
        out[8] = fusedSpeedLimit;
        out[9] = stopLineDist;
        out[10] = trafficLightColor;
        out[11] = laneDepartureWarning;
        out[12] = sideCollisionWarning;
        out[13] = anyDoorOpen;
        out[14] = buckleStatus;
        out[15] = accSetSpeed;

        // Vehicle bus
        out[16] = fullPackEnergy;
        out[17] = nominalEnergyRemaining;
        out[18] = energyBuffer;
        out[19] = maxRegenPower;
        out[20] = maxDischargePower;
        out[21] = packVoltage;
        out[22] = packCurrent;
        out[23] = packTMin;
        out[24] = packTMax;
        out[25] = odometer;

        // Openpilot
        out[26] = selfdriveActive;
        out[27] = experimentalMode;
        out[28] = madsActive;
        out[29] = changingLane;

        out[30] = acTemp;

        // Virtual lane geometry
        out[31] = virtualLaneWidth;
        out[32] = virtualLaneViewRange;
        out[33] = virtualLaneC0;
        out[34] = virtualLaneC1;
        out[35] = virtualLaneC2;
        out[36] = virtualLaneC3;
    }
};
