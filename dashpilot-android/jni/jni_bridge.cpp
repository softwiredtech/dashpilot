#include <jni.h>
#include <string>
#include <iostream>
#include "msgq/ipc.h"
#include "dbc/dbcfile.h"
#include <android/log.h>
#define LOG_TAG "MsgQNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#ifdef ANDROID
#undef ANDROID
#endif
#include <capnp/message.h>
#include <capnp/serialize.h>
#include "log.capnp.h"
#include <thread>
#include <chrono>


extern "C" {

DBCFile* dbcFile;
bool receiveLoopRunning = false;

std::vector<std::string> targetSignalNames = {
        "SCCM_steeringAngle",
        "DAS_accState",
        "DAS_trafficLightColor",
        "DAS_stopLineDist",
        "DAS_blindSpotRearLeft",
        "DAS_blindSpotRearRight",
        "DAS_fusedSpeedLimit",
        "DI_uiSpeed",
        "DI_gear",
        "leftBlinkerBlinking",
        "rightBlinkerBlinking"
};

struct OrderedSignal {
    uint32_t messageId;
    const cabana::Signal* signal;
};

std::vector<OrderedSignal> orderedSignals;

static JavaVM* g_vm = nullptr;
static jobject g_callback = nullptr;
static jdoubleArray g_buffer = nullptr;
static jmethodID g_onCanDataMethod = nullptr;

jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// === DBC ===
JNIEXPORT jboolean JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_loadDbcFile(JNIEnv *env, jobject thiz, jstring dbcPath) {
    const char* dbcPathString = env->GetStringUTFChars(dbcPath, nullptr);
    dbcFile = new DBCFile(dbcPathString);
    env->ReleaseStringUTFChars(dbcPath, dbcPathString);

    std::unordered_map<std::string, OrderedSignal> lookup;

    for (const auto& msgPair : dbcFile->getMessages()) {
        const auto& msg = msgPair.second;

        for (const auto& sig : msg.getSignals()) {
            lookup[sig->name] = { msg.address,sig };
        }
    }

    orderedSignals.clear();

    for (const auto& name : targetSignalNames) {
        if (lookup.count(name)) {
            orderedSignals.push_back(lookup[name]);
        } else {
            LOGD("Missing signal: %s", name.c_str());
            orderedSignals.push_back({0, nullptr});
        }
    }

    return true;
}

// === Context ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeCreateContext(JNIEnv *env, jobject thiz) {
    Context *ctx = Context::create();
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeDeleteContext(JNIEnv *env, jobject thiz,
                                                           jlong ctxPtr) {
    Context *ctx = reinterpret_cast<Context *>(ctxPtr);
    delete ctx;
}

// === SubSocket ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeCreateSubSocket(
        JNIEnv* env, jobject thiz,
        jlong ctxPtr,
        jstring endpoint,
        jstring address) {

    Context* ctx = reinterpret_cast<Context*>(ctxPtr);

    std::string  endpointStr = env->GetStringUTFChars(endpoint, nullptr);
    std::string  addressStr = env->GetStringUTFChars(address, nullptr);

    SubSocket* sub = SubSocket::create(
            ctx,
            endpointStr,
            addressStr,
            false,
            true,
            0
    );

    return reinterpret_cast<jlong>(sub);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeDeleteSubSocket(JNIEnv* env, jobject thiz, jlong subPtr) {
    SubSocket* sub = reinterpret_cast<SubSocket*>(subPtr);
    delete sub;
}

// === Message ===
JNIEXPORT void JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeStartReceiveLoop(
        JNIEnv* env,
        jobject thiz,
        jlong subPtr,
        jdoubleArray buffer,
        jobject callback) {

    // Setup callbacks:
    g_callback = env->NewGlobalRef(callback);
    g_buffer = (jdoubleArray) env->NewGlobalRef(buffer);

    jclass callbackClass = env->GetObjectClass(callback);
    g_onCanDataMethod = env->GetMethodID(
            callbackClass,
            "onCanData",
            "([D)V");
    auto sub = reinterpret_cast<SubSocket*>(subPtr);
    receiveLoopRunning = true;
    std::vector<double> nativeBuffer(orderedSignals.size(), 0.0);

    while (receiveLoopRunning) {
        Message *msg = sub->receive(true);
        if (msg) {
            kj::ArrayPtr<capnp::word> canArray = kj::ArrayPtr<capnp::word>((capnp::word*)msg->getData(), msg->getSize() / sizeof(capnp::word));
            capnp::FlatArrayMessageReader reader(canArray);
            auto event = reader.getRoot<cereal::Event>();

            if (event.which() == cereal::Event::Which::CAN) {
                auto canList = event.getCan();

                // TODO: refactor me, this is experimental
                for (const auto &c : canList) {
                    auto src = c.getSrc();
                    auto addr = c.getAddress();
                    auto dat = c.getDat();
                    auto canMsg = dbcFile->msg(addr);
                    if (canMsg) {
                        uint32_t currentMsgId = canMsg->address;
                        for (size_t i = 0; i < orderedSignals.size(); i++) {
                            const auto& os = orderedSignals[i];

                            if (!os.signal)
                                continue;

                            if (os.messageId != currentMsgId)
                                continue;

                            double value = 0.0;

                            os.signal->getValue(
                                    (const uint8_t*)(dat.begin()),
                                    dat.size(),
                                    &value);

                            nativeBuffer[i] = value;
                        }
                    }

                    env->SetDoubleArrayRegion(
                            g_buffer,
                            0,
                            nativeBuffer.size(),
                            nativeBuffer.data());

                    env->CallVoidMethod(
                            g_callback,
                            g_onCanDataMethod,
                            g_buffer);
                }
            }
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
    }
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeStopReceiveLoop(JNIEnv* env, jobject thiz) {
    receiveLoopRunning = false;
}

JNIEXPORT jbyteArray JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeGetData(JNIEnv* env, jobject thiz, jlong msgPtr) {
    Message* msg = reinterpret_cast<Message*>(msgPtr);
    size_t size = msg->getSize();
    char* data = msg->getData();

    jbyteArray array = env->NewByteArray(size);
    env->SetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte*>(data));
    return array;
}

JNIEXPORT jint JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeGetSize(JNIEnv* env, jobject thiz, jlong msgPtr) {
    Message* msg = reinterpret_cast<Message*>(msgPtr);
    return static_cast<jint>(msg->getSize());
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_pilotboard_jni_CommaBridge_nativeDeleteMessage(JNIEnv* env, jobject thiz, jlong msgPtr) {
    Message* msg = reinterpret_cast<Message*>(msgPtr);
    delete msg;
}

}