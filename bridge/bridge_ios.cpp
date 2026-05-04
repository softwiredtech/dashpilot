#include "bridge_ios.h"
#include "msgq/ipc.h"
#include "car/can_parsers.h"
#include "car/car_state_mapper.h"
#include "car/cars/tesla.h"
#include <vector>
#include <thread>
#include <chrono>
#include <cstdio>
#include <memory>
#include <string>
#include <capnp/message.h>
#include <capnp/serialize.h>
#include "capnp/gen/log.capnp.h"

struct SubSocketGroup {
    std::vector<SubSocket*> sockets;
};

class VehicleDecoder {
public:
    VehicleDecoder(const std::vector<std::string>& dbcContents,
                   const std::vector<int>& busIndices,
                   const std::string& vehicleType) {
        for (size_t i = 0; i < dbcContents.size(); i++) {
            printf("[VehicleDecoder] addBus(%d) dbc size=%zu\n", busIndices[i], dbcContents[i].size());
            parsers_.addBus(busIndices[i], dbcContents[i]);
        }
        parsers_.buildCache();
        printf("[VehicleDecoder] cache built, vehicleType='%s'\n", vehicleType.c_str());

        if (vehicleType == "tesla_party") mapper_ = std::make_unique<TeslaCommaPartyMapper>();
        else if (vehicleType == "tesla_extra") mapper_ = std::make_unique<TeslaCommaExtraMapper>();

        if (!mapper_) {
            printf("[VehicleDecoder] ERROR: unknown vehicle type '%s'\n", vehicleType.c_str());
        }
    }

    void updateFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        parsers_.updateFrame(bus, address, data, len);
    }

    void updateMapper() {
        if (mapper_) {
            mapper_->update(parsers_, state_);
        }
    }

    CarState& state() { return state_; }

private:
    CANParsers parsers_;
    std::unique_ptr<CarStateMapper> mapper_;
    CarState state_;
};

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

    while (receiveLoopRunning) {
        bool gotAny = false;

        for (auto* sub : group->sockets) {
            Message* msg = sub->receive(true);
            if (!msg) continue;
            gotAny = true;

            kj::ArrayPtr<capnp::word> words(
                reinterpret_cast<capnp::word*>(msg->getData()),
                msg->getSize() / sizeof(capnp::word));
            capnp::FlatArrayMessageReader reader(words);
            auto event = reader.getRoot<cereal::Event>();

            if (event.which() == cereal::Event::CAN) {
                auto canList = event.getCan();
                static int logCount = 0;
                for (const auto& c : canList) {
                    auto dat = c.getDat();
                    if (logCount < 20) {
                        printf("[bridge_ios] CAN frame: bus=%d addr=0x%X len=%u\n",
                               c.getSrc(), c.getAddress(), (unsigned)dat.size());
                    }
                    decoder->updateFrame(
                        c.getSrc(), c.getAddress(),
                        reinterpret_cast<const uint8_t*>(dat.begin()),
                        dat.size());
                }
                decoder->updateMapper();

                auto& state = decoder->state();
                if (logCount < 20) {
                    printf("[bridge_ios] after mapper: speed=%.1f steering=%.1f gear=%.0f\n",
                           state.egoSpeed, state.egoSteeringAngle, state.gear);
                    logCount++;
                }
                carStateToBridge(state, &bridgeState);
                callback(callbackContext, &bridgeState);

            } else if (event.which() == cereal::Event::SELFDRIVE_STATE) {
                auto sd = event.getSelfdriveState();
                decoder->state().experimentalMode = sd.getExperimentalMode() ? 1.0 : 0.0;
                decoder->state().selfdriveActive = sd.getActive() ? 1.0 : 0.0;

            } else if (event.which() == cereal::Event::SELFDRIVE_STATE_S_P) {
                decoder->state().madsActive =
                    event.getSelfdriveStateSP().getMads().getActive() ? 1.0 : 0.0;
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
