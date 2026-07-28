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
    double laneDepartureWarning;
    double sideCollisionWarning;
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

    // Openpilot state
    double madsActive;
    double selfdriveActive;
    double experimentalMode;
    double changingLane;
} BridgeCarState;

// Context
void* bridge_create_context(void);
void  bridge_delete_context(void* ctx);

// Subscribe socket group
void* bridge_create_sub_sockets(void* ctx, const char** endpoints, int count, const char* address);
void  bridge_delete_sub_sockets(void* group);

// Vehicle decoder
void* bridge_create_vehicle_decoder(const char** dbcContents, const int* busIndices, int count, const char* vehicleType);
void  bridge_destroy_vehicle_decoder(void* decoder);

// Per-frame decode (DashKit BLE path): feed one raw CAN frame into the decoder
// and read back the updated car state. Not thread-safe against the receive
// loop — a decoder is used by either this or bridge_start_receive_loop.
void bridge_decode_can_frame(void* decoder, int bus, uint32_t address, const uint8_t* data, int len, BridgeCarState* out);

// Discovery
// Returns a malloc'd C string with the discovered IP, or NULL if not found.
// Caller must free() the result.
char* bridge_discover_publisher(void* ctx, const char* endpoint, const char* initialIp, int timeoutMs);

// Receive loop
typedef void (*bridge_car_state_callback_t)(void* context, const BridgeCarState* state);
void bridge_start_receive_loop(void* group, void* decoder, void* callbackContext, bridge_car_state_callback_t callback);
void bridge_stop_receive_loop(void);

#ifdef __cplusplus
}
#endif
