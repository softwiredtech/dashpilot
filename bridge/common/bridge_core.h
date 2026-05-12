#pragma once

#include "common/vehicle_decoder.h"
#include <capnp/message.h>
#include <capnp/serialize.h>
// log.capnp.h uses ANDROID as an enum value, but the Android NDK defines it
// as a macro. Temporarily undefine it so the header compiles, then restore it.
#ifdef ANDROID
#define _SAVED_ANDROID ANDROID
#undef ANDROID
#endif
#include "capnp/gen/log.capnp.h"
#ifdef _SAVED_ANDROID
#define ANDROID _SAVED_ANDROID
#undef _SAVED_ANDROID
#endif
#include <atomic>
#include <chrono>
#include <cstdio>
#include <functional>
#include <map>
#include <string>
#include <thread>
#include <vector>

// Cross-platform logging: routes to logcat on Android, printf elsewhere.
#ifdef __ANDROID__
#include <android/log.h>
#define BRIDGE_LOG(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "Bridge", fmt, ##__VA_ARGS__)
#else
#define BRIDGE_LOG(fmt, ...) printf(fmt "\n", ##__VA_ARGS__)
#endif

namespace bridge {

inline bool processMessage(Message* msg, VehicleDecoder* decoder,
                           double& madsActive, double& experimentalMode,
                           double& selfdriveActive, double& changingLane) {
    kj::ArrayPtr<capnp::word> words(
        reinterpret_cast<capnp::word*>(msg->getData()),
        msg->getSize() / sizeof(capnp::word));
    capnp::FlatArrayMessageReader reader(words);
    auto event = reader.getRoot<cereal::Event>();

    if (event.which() == cereal::Event::CAN) {
        auto canList = event.getCan();
        for (const auto& c : canList) {
            decoder->updateFrame(c.getSrc(), c.getAddress(),
                reinterpret_cast<const uint8_t*>(c.getDat().begin()),
                c.getDat().size());
        }
        decoder->updateMapper();
        decoder->state().madsActive = madsActive;
        decoder->state().experimentalMode = experimentalMode;
        decoder->state().selfdriveActive = selfdriveActive;
        decoder->state().changingLane = changingLane;
        return true;
    } else if (event.which() == cereal::Event::SELFDRIVE_STATE) {
        auto sd = event.getSelfdriveState();
        experimentalMode = sd.getExperimentalMode() ? 1.0 : 0.0;
        selfdriveActive = sd.getActive() ? 1.0 : 0.0;
        std::string alertType = sd.getAlertType().cStr();
        changingLane = (alertType == "laneChange/warning") ? 1.0 : 0.0;
    } else if (event.which() == cereal::Event::SELFDRIVE_STATE_S_P) {
        madsActive = event.getSelfdriveStateSP().getMads().getActive() ? 1.0 : 0.0;
    }
    return false;
}

inline Context* createContext() {
    return Context::create();
}

inline void deleteContext(Context* ctx) {
    if (ctx) delete ctx;
}

inline SubSocketGroup* createSubSockets(
        Context* ctx,
        const std::vector<std::string>& endpoints,
        const std::string& address) {
    auto* group = new SubSocketGroup();
    for (const auto& ep : endpoints) {
        SubSocket* sub = SubSocket::create(ctx, ep, address, false, true, 0);
        if (sub) {
            group->sockets.push_back(sub);
        }
    }
    return group;
}

inline void deleteSubSockets(SubSocketGroup* group) {
    if (!group) return;
    for (auto* sub : group->sockets) delete sub;
    delete group;
}

inline VehicleDecoder* createVehicleDecoder(
        const std::vector<std::string>& dbcContents,
        const std::vector<int>& busIndices,
        const std::string& vehicleType) {
    return new VehicleDecoder(dbcContents, busIndices, vehicleType);
}

inline void destroyVehicleDecoder(VehicleDecoder* decoder) {
    if (decoder) delete decoder;
}

using StateCallback = std::function<void(const CarState&)>;

inline void runReceiveLoop(
        SubSocketGroup* group,
        VehicleDecoder* decoder,
        std::atomic<bool>& running,
        StateCallback onState) {
    double madsActive = 0.0;
    double experimentalMode = 0.0;
    double selfdriveActive = 0.0;
    double changingLane = 0.0;

    long long msgTimeAccum = 0;
    int msgCount = 0;
    int callbackCount = 0;
    auto fpsStart = std::chrono::steady_clock::now();

    while (running.load()) {
        bool gotAny = false;

        for (auto* sub : group->sockets) {
            Message* msg = sub->receive(true);
            if (!msg) continue;
            gotAny = true;

            auto t0 = std::chrono::high_resolution_clock::now();

            if (processMessage(msg, decoder, madsActive, experimentalMode, selfdriveActive, changingLane)) {
                onState(decoder->state());
                callbackCount++;
            }

            delete msg;

            auto t1 = std::chrono::high_resolution_clock::now();
            msgTimeAccum += std::chrono::duration_cast<std::chrono::microseconds>(t1 - t0).count();
            msgCount++;
            if (msgCount >= 100) {
                auto now = std::chrono::steady_clock::now();
                double elapsed = std::chrono::duration<double>(now - fpsStart).count();
                double fps = callbackCount / elapsed;
                BRIDGE_LOG("[bridge] avg: %.3f ms/msg (%d msgs) | %.1f callbacks/s",
                       msgTimeAccum / 1000.0 / msgCount, msgCount, fps);
                msgTimeAccum = 0;
                msgCount = 0;
                callbackCount = 0;
                fpsStart = now;
            }
        }

        if (!gotAny) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
}


inline std::string discoverPublisher(
        Context* ctx,
        const std::string& endpoint,
        const std::string& initialIp,
        int timeoutMs) {
    auto lastDot = initialIp.rfind('.');
    if (lastDot == std::string::npos) return "";
    std::string prefix = initialIp.substr(0, lastDot);
    int selfOctet = std::stoi(initialIp.substr(lastDot + 1));

    std::vector<int> octets;
    for (int i = 1; i <= 254; i++) {
        if (i != selfOctet) octets.push_back(i);
    }

    std::vector<SubSocket*> allSockets;
    std::map<SubSocket*, std::string> socketToIp;
    allSockets.reserve(octets.size());

    for (int octet : octets) {
        std::string candidate = prefix + "." + std::to_string(octet);
        SubSocket* sub = SubSocket::create(ctx, endpoint, candidate, false, true, 0);
        if (sub) {
            allSockets.push_back(sub);
            socketToIp[sub] = candidate;
        }
    }

    std::string foundIp;
    auto start = std::chrono::steady_clock::now();
    const size_t batchSize = 127;
    std::vector<Poller*> pollers;

    for (size_t i = 0; i < allSockets.size(); i += batchSize) {
        Poller* poller = Poller::create();
        size_t end = std::min(i + batchSize, allSockets.size());
        for (size_t j = i; j < end; j++) {
            poller->registerSocket(allSockets[j]);
        }
        pollers.push_back(poller);
    }

    while (foundIp.empty()) {
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - start).count();
        if (elapsed >= timeoutMs) break;

        for (auto* poller : pollers) {
            auto ready = poller->poll(100);
            for (auto* sub : ready) {
                Message* msg = sub->receive(true);
                if (msg) {
                    delete msg;
                    foundIp = socketToIp[sub];
                    break;
                }
            }
            if (!foundIp.empty()) break;
        }
    }

    for (auto* poller : pollers) delete poller;
    for (auto* sub : allSockets) delete sub;

    return foundIp;
}

} // namespace bridge
