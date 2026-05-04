#include "bridge_ios.h"
#include "msgq/ipc.h"
#include <vector>
#include <thread>
#include <chrono>
#include <cstdio>

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

    while (receiveLoopRunning) {
        bool gotAny = false;

        for (auto* sub : group->sockets) {
            Message* msg = sub->receive(true);
            if (!msg) continue;
            gotAny = true;

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
