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
#include <thread>
#include <chrono>

// Processes one message from a SubSocket, updating decoder state.
// Returns true if a CAN event was processed (caller should emit state to platform).
inline bool processMessage(Messae* msg, VehicleDecoder* decoder,
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
