#include "bridge_ios.h"
#include "common/vehicle_decoder.h"
#include "common/receive_loop.h"
#include <vector>
#include <thread>
#include <chrono>
#include <cstdio>
#include <string>

static void carStateToBridge(const CarState& cs, BridgeCarState* out) {
    out->egoSteeringAngle = cs.egoSteeringAngle;
    out->egoSpeed = cs.egoSpeed;
    out->leftBlinker = cs.leftBlinker;
    out->rightBlinker = cs.rightBlinker;
    out->gear = cs.gear;
    out->adasOn = cs.adasOn;
    out->leftBlindSpot = cs.leftBlindSpot;
    out->rightBlindSpot = cs.rightBlindSpot;
    out->fusedSpeedLimit = cs.fusedSpeedLimit;
    out->stopLineDist = cs.stopLineDist;
    out->trafficLightColor = cs.trafficLightColor;
    out->laneDepartureWarning = cs.laneDepartureWarning;
    out->sideCollisionWarning = cs.sideCollisionWarning;
    out->anyDoorOpen = cs.anyDoorOpen;
    out->buckleStatus = cs.buckleStatus;
    out->accSetSpeed = cs.accSetSpeed;
    out->fullPackEnergy = cs.fullPackEnergy;
    out->nominalEnergyRemaining = cs.nominalEnergyRemaining;
    out->energyBuffer = cs.energyBuffer;
    out->maxRegenPower = cs.maxRegenPower;
    out->maxDischargePower = cs.maxDischargePower;
    out->packVoltage = cs.packVoltage;
    out->packCurrent = cs.packCurrent;
    out->packTMin = cs.packTMin;
    out->packTMax = cs.packTMax;
    out->odometer = cs.odometer;
    out->madsActive = cs.madsActive;
    out->selfdriveActive = cs.selfdriveActive;
    out->experimentalMode = cs.experimentalMode;
    out->changingLane = cs.changingLane;
}

static bool receiveLoopRunning = false;

extern "C" {

void* bridge_create_context(void) {
    return Context::create();
}

void bridge_delete_context(void* ctx) {
    if (ctx) delete static_cast<Context*>(ctx);
}

void* bridge_create_sub_sockets(void* ctx, const char** endpoints, int count, const char* address) {
    auto* context = static_cast<Context*>(ctx);
    auto* group = new SubSocketGroup();

    for (int i = 0; i < count; i++) {
        SubSocket* sub = SubSocket::create(context, endpoints[i], address, false, true, 0);
        if (sub) {
            group->sockets.push_back(sub);
            printf("[bridge_ios] subscribed to '%s' at %s\n", endpoints[i], address);
        }
    }

    return group;
}

void bridge_delete_sub_sockets(void* groupPtr) {
    if (!groupPtr) return;
    auto* group = static_cast<SubSocketGroup*>(groupPtr);
    for (auto* sub : group->sockets) delete sub;
    delete group;
}

void* bridge_create_vehicle_decoder(const char** dbcContents, const int* busIndices, int count, const char* vehicleType) {
    std::vector<std::string> dbc;
    std::vector<int> buses;
    for (int i = 0; i < count; i++) {
        dbc.emplace_back(dbcContents[i]);
        buses.push_back(busIndices[i]);
    }
    return new VehicleDecoder(dbc, buses, vehicleType);
}

void bridge_destroy_vehicle_decoder(void* decoder) {
    if (decoder) delete static_cast<VehicleDecoder*>(decoder);
}

void bridge_start_receive_loop(void* groupPtr, void* decoderPtr, void* callbackContext, bridge_car_state_callback_t callback) {
    auto* group = static_cast<SubSocketGroup*>(groupPtr);
    auto* decoder = static_cast<VehicleDecoder*>(decoderPtr);
    receiveLoopRunning = true;

    BridgeCarState bridgeState = {};
    double madsActive = 0.0;
    double experimentalMode = 0.0;
    double selfdriveActive = 0.0;
    double changingLane = 0.0;

    while (receiveLoopRunning) {
        bool gotAny = false;

        for (auto* sub : group->sockets) {
            Message* msg = sub->receive(true);
            if (!msg) continue;
            gotAny = true;

            if (processMessage(msg, decoder, madsActive, experimentalMode, selfdriveActive, changingLane)) {
                carStateToBridge(decoder->state(), &bridgeState);
                callback(callbackContext, &bridgeState);
            }

            delete msg;
        }

        if (!gotAny) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
}

void bridge_stop_receive_loop(void) {
    receiveLoopRunning = false;
}

}
