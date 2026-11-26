#include "bridge_ios.h"
#include "common/vehicle_decoder.h"
#include <vector>
#include <string>
#include <cstring>

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
    out->acTemp = cs.acTemp;
    out->acTempRight = cs.acTempRight;
    out->hvacFanLevel = cs.hvacFanLevel;
    out->hvacPowerState = cs.hvacPowerState;
    out->hvacAcMode = cs.hvacAcMode;
    out->hvacRecirc = cs.hvacRecirc;
    out->hvacKeepClimateOn = cs.hvacKeepClimateOn;
    out->powerLevel = cs.powerLevel;
    out->madsActive = cs.madsActive;
    out->selfdriveActive = cs.selfdriveActive;
    out->experimentalMode = cs.experimentalMode;
    out->changingLane = cs.changingLane;
    out->tpmsPressureFL = cs.tpmsPressureFL;
    out->tpmsPressureFR = cs.tpmsPressureFR;
    out->tpmsPressureRL = cs.tpmsPressureRL;
    out->tpmsPressureRR = cs.tpmsPressureRR;
    out->tpmsTempFL = cs.tpmsTempFL;
    out->tpmsTempFR = cs.tpmsTempFR;
    out->tpmsTempRL = cs.tpmsTempRL;
    out->tpmsTempRR = cs.tpmsTempRR;
    out->tpmsBattFL = cs.tpmsBattFL;
    out->tpmsBattFR = cs.tpmsBattFR;
    out->tpmsBattRL = cs.tpmsBattRL;
    out->tpmsBattRR = cs.tpmsBattRR;
    out->doorOpenFL = cs.doorOpenFL;
    out->doorOpenFR = cs.doorOpenFR;
    out->doorOpenRL = cs.doorOpenRL;
    out->doorOpenRR = cs.doorOpenRR;
    out->trunkOpen = cs.trunkOpen;
    out->trunkPosition = cs.trunkPosition;
    out->frunkOpen = cs.frunkOpen;
    out->ambientLightingAvailable = cs.ambientLightingAvailable;
    out->ambientLightingColor = static_cast<uint32_t>(cs.ambientLightingColor);
    out->ambientLightingBrightness = cs.ambientLightingBrightness;
}

extern "C" {

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
    delete static_cast<VehicleDecoder*>(decoder);
}

void bridge_decode_can_frame(void* decoderPtr, int bus, uint32_t address, const uint8_t* data, int len, BridgeCarState* out) {
    auto* decoder = static_cast<VehicleDecoder*>(decoderPtr);
    decoder->updateFrame(bus, address, data, static_cast<size_t>(len));
    decoder->updateMapper();
    carStateToBridge(decoder->state(), out);
}

int bridge_can_filter_payload(void* decoderPtr, uint8_t* out, int capacity) {
    if (!decoderPtr || !out || capacity <= 0) return 0;
    std::vector<uint8_t> payload = static_cast<VehicleDecoder*>(decoderPtr)->canFilterPayload();
    if (payload.empty() || payload.size() > static_cast<size_t>(capacity)) return 0;
    std::memcpy(out, payload.data(), payload.size());
    return static_cast<int>(payload.size());
}

}
