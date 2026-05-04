#include "bridge_ios.h"
#include "msgq/ipc.h"
#include <vector>
#include <thread>
#include <chrono>
#include <cstdio>
#include <capnp/message.h>
#include <capnp/serialize.h>
#include "capnp/gen/log.capnp.h"

struct SubSocketGroup {
    std::vector<SubSocket*> sockets;
};

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

void bridge_start_receive_loop(void* groupPtr, bridge_callback_t callback) {
    auto* group = static_cast<SubSocketGroup*>(groupPtr);
    receiveLoopRunning = true;

    double egoSpeed = 0;
    double egoSteeringAngle = 0;
    double selfdriveActive = 0;
    double experimentalMode = 0;
    double madsActive = 0;

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
                for (const auto& c : canList) {
                    (void)c.getSrc();
                    (void)c.getAddress();
                    (void)c.getDat();
                }
                // TODO: feed CAN frames into vehicle decoder
                printf("[bridge_ios] CAN: %d frames\n", canList.size());

            } else if (event.which() == cereal::Event::SELFDRIVE_STATE) {
                auto state = event.getSelfdriveState();
                experimentalMode = state.getExperimentalMode() ? 1.0 : 0.0;
                selfdriveActive = state.getActive() ? 1.0 : 0.0;
                printf("[bridge_ios] selfdriveState: active=%d experimental=%d\n",
                       (int)selfdriveActive, (int)experimentalMode);

            } else if (event.which() == cereal::Event::SELFDRIVE_STATE_S_P) {
                madsActive = event.getSelfdriveStateSP().getMads().getActive() ? 1.0 : 0.0;
                printf("[bridge_ios] selfdriveStateSP: madsActive=%d\n", (int)madsActive);
            }

            // Pass raw bytes to Swift callback for now
            callback(reinterpret_cast<const uint8_t*>(msg->getData()), static_cast<int>(msg->getSize()));
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
