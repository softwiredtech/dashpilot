#pragma once

#include <cstddef>

struct CarState {
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

    static constexpr size_t FIELD_COUNT = 16;

    void toArray(double* out) const {
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
    }
};
