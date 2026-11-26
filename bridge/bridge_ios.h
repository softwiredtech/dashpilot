#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Typed struct passed to Swift — no index guessing like the Android double[] approach.
typedef struct {
    // Party bus (ADAS)
    double egoSteeringAngle;
    double egoSpeed;
    double leftBlinker;
    double rightBlinker;
    double gear;
    double adasOn;
    double leftBlindSpot;
    double rightBlindSpot;
    double fusedSpeedLimit;
    double stopLineDist;
    double trafficLightColor;
    double anyDoorOpen;
    double buckleStatus;
    double accSetSpeed;

    // Vehicle bus (BMS/Power)
    double fullPackEnergy;
    double nominalEnergyRemaining;
    double energyBuffer;
    double maxRegenPower;
    double maxDischargePower;
    double packVoltage;
    double packCurrent;
    double packTMin;
    double packTMax;
    double odometer;
    double acTemp;
    double acTempRight;
    double hvacFanLevel;
    double hvacPowerState;
    double hvacAcMode;
    double hvacRecirc;
    double hvacKeepClimateOn;
    double powerLevel;

    // Assist state. No current data source sets these.
    double madsActive;
    double selfdriveActive;
    double experimentalMode;
    double changingLane;

    // TPMS: pressure (bar, -1 = none), temperature (degC, -100 = none) and sensor battery (V, -1 = none) per wheel.
    double tpmsPressureFL;
    double tpmsPressureFR;
    double tpmsPressureRL;
    double tpmsPressureRR;
    double tpmsTempFL;
    double tpmsTempFR;
    double tpmsTempRL;
    double tpmsTempRR;
    double tpmsBattFL;
    double tpmsBattFR;
    double tpmsBattRL;
    double tpmsBattRR;

    // Closures: doors/trunk 0/1 (1 = latch not closed), trunk angle in degrees.
    double doorOpenFL;
    double doorOpenFR;
    double doorOpenRL;
    double doorOpenRR;
    double trunkOpen;
    double trunkPosition;
    double frunkOpen;

    double ambientLightingAvailable;
    uint32_t ambientLightingColor;
    double ambientLightingBrightness;
} BridgeCarState;

// Vehicle decoder
void* bridge_create_vehicle_decoder(const char** dbcContents, const int* busIndices, int count, const char* vehicleType);
void  bridge_destroy_vehicle_decoder(void* decoder);

// Per-frame decode (DashKit BLE path): feed one raw CAN frame into the decoder
// and read back the updated car state.
void bridge_decode_can_frame(void* decoder, int bus, uint32_t address, const uint8_t* data, int len, BridgeCarState* out);

// DashKit CAN subscription: writes the CAN filter payload for this decoder's
// vehicle (the ids its mapper reads) into `out` and returns its length. Returns
// 0 if there is nothing to subscribe to or it does not fit in `capacity`.
int bridge_can_filter_payload(void* decoder, uint8_t* out, int capacity);

#ifdef __cplusplus
}
#endif
