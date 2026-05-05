#include <jni.h>
#include <string>
#include <iostream>
#include <cmath>
#include <memory>
#include <android/log.h>

#include "common/vehicle_decoder.h"
#include "common/receive_loop.h"

#define LOG_TAG "MsgQNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#include <map>

extern "C" {

static bool receiveLoopRunning = false;
static JavaVM* g_vm = nullptr;
static jobject g_callback = nullptr;
static jdoubleArray g_buffer = nullptr;
static jmethodID g_onCanDataMethod = nullptr;

jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// === Context ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCreateContext(JNIEnv *env, jobject thiz) {
    Context *ctx = Context::create();
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteContext(JNIEnv *env, jobject thiz,
                                                           jlong ctxPtr) {
    if (ctxPtr == 0) {
        return;
    }
    Context *ctx = reinterpret_cast<Context *>(ctxPtr);
    delete ctx;
}

// === SubSocket ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCreateSubSocket(
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
            0);

    return reinterpret_cast<jlong>(sub);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteSubSocket(JNIEnv* env, jobject thiz, jlong subPtr) {
    if (subPtr == 0) {
        return;
    }
    SubSocket* sub = reinterpret_cast<SubSocket*>(subPtr);
    delete sub;
}

// === SubSocketGroup (multiple endpoints) ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCreateSubSockets(
        JNIEnv* env, jobject thiz,
        jlong ctxPtr,
        jobjectArray endpoints,
        jstring address) {

    Context* ctx = reinterpret_cast<Context*>(ctxPtr);
    const char* addrChars = env->GetStringUTFChars(address, nullptr);
    std::string addressStr(addrChars);
    env->ReleaseStringUTFChars(address, addrChars);

    int count = env->GetArrayLength(endpoints);
    auto* group = new SubSocketGroup();

    for (int i = 0; i < count; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(endpoints, i);
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        std::string endpointStr(chars);
        env->ReleaseStringUTFChars(jstr, chars);

        SubSocket* sub = SubSocket::create(ctx, endpointStr, addressStr, false, true, 0);
        if (sub) {
            group->sockets.push_back(sub);
            LOGD("SubSocketGroup: subscribed to '%s' at %s", endpointStr.c_str(), addressStr.c_str());
        }
    }

    return reinterpret_cast<jlong>(group);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteSubSockets(
        JNIEnv* env, jobject thiz, jlong groupPtr) {
    if (groupPtr == 0) return;
    auto* group = reinterpret_cast<SubSocketGroup*>(groupPtr);
    for (auto* sub : group->sockets) delete sub;
    delete group;
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeStopReceiveLoop(JNIEnv* env, jobject thiz) {
    receiveLoopRunning = false;
}

// Discover a ZMQ publisher on the local subnet using Poller.
JNIEXPORT jstring JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDiscoverPublisher(
        JNIEnv* env, jobject thiz,
        jlong ctxPtr,
        jstring endpoint,
        jstring initialIp,
        jint timeoutMs) {

    Context* ctx = reinterpret_cast<Context*>(ctxPtr);

    const char* endpointChars = env->GetStringUTFChars(endpoint, nullptr);
    const char* ipChars = env->GetStringUTFChars(initialIp, nullptr);
    std::string endpointStr(endpointChars);
    std::string ipStr(ipChars);
    env->ReleaseStringUTFChars(endpoint, endpointChars);
    env->ReleaseStringUTFChars(initialIp, ipChars);

    // Parse subnet prefix and self octet
    auto lastDot = ipStr.rfind('.');
    if (lastDot == std::string::npos) return nullptr;
    std::string prefix = ipStr.substr(0, lastDot);
    int selfOctet = std::stoi(ipStr.substr(lastDot + 1));

    // Build candidate list, skipping our own octet
    std::vector<int> octets;
    for (int i = 1; i <= 254; i++) {
        if (i != selfOctet) octets.push_back(i);
    }

    // Create all sockets and map them to their IPs
    std::vector<SubSocket*> allSockets;
    std::map<SubSocket*, std::string> socketToIp;
    allSockets.reserve(octets.size());

    for (int octet : octets) {
        std::string candidate = prefix + "." + std::to_string(octet);
        SubSocket* sub = SubSocket::create(ctx, endpointStr, candidate, false, true, 0);
        if (sub) {
            allSockets.push_back(sub);
            socketToIp[sub] = candidate;
        }
    }

    LOGD("Discovery: created %zu sockets on subnet %s.*, timeout %dms",
         allSockets.size(), prefix.c_str(), timeoutMs);

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

    // Poll all batches in round-robin until timeout or hit
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

    // Cleanup
    for (auto* poller : pollers) delete poller;
    for (auto* sub : allSockets) delete sub;

    if (foundIp.empty()) {
        LOGD("Discovery: no publisher found");
        return nullptr;
    }

    LOGD("Discovery: found publisher at %s", foundIp.c_str());
    return env->NewStringUTF(foundIp.c_str());
}

JNIEXPORT jbyteArray JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeGetData(JNIEnv* env, jobject thiz, jlong msgPtr) {
    Message* msg = reinterpret_cast<Message*>(msgPtr);
    size_t size = msg->getSize();
    char* data = msg->getData();

    jbyteArray array = env->NewByteArray(size);
    env->SetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte*>(data));
    return array;
}

JNIEXPORT jint JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeGetSize(JNIEnv* env, jobject thiz, jlong msgPtr) {
    Message* msg = reinterpret_cast<Message*>(msgPtr);
    return static_cast<jint>(msg->getSize());
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteMessage(JNIEnv* env, jobject thiz, jlong msgPtr) {
    Message* msg = reinterpret_cast<Message*>(msgPtr);
    delete msg;
}

// ============================================================
// VehicleDecoder JNI functions
// ============================================================

JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCreateVehicleDecoder(
        JNIEnv* env, jobject thiz,
        jobjectArray dbcContents,
        jintArray busIndices,
        jstring vehicleType) {

    int dbcCount = env->GetArrayLength(dbcContents);
    std::vector<std::string> dbcContentsVec;
    std::vector<int> busIndicesVec;

    jint* busIndicesArr = env->GetIntArrayElements(busIndices, nullptr);
    for (int i = 0; i < dbcCount; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(dbcContents, i);
        const char* str = env->GetStringUTFChars(jstr, nullptr);
        dbcContentsVec.emplace_back(str);
        env->ReleaseStringUTFChars(jstr, str);
        busIndicesVec.push_back(busIndicesArr[i]);
    }
    env->ReleaseIntArrayElements(busIndices, busIndicesArr, 0);

    const char* typeStr = env->GetStringUTFChars(vehicleType, nullptr);
    std::string type(typeStr);
    env->ReleaseStringUTFChars(vehicleType, typeStr);

    auto* decoder = new VehicleDecoder(dbcContentsVec, busIndicesVec, type);
    LOGD("VehicleDecoder created: type=%s, %d buses", type.c_str(), dbcCount);
    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jdoubleArray JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDecodeCanFrame(
        JNIEnv* env, jobject thiz,
        jlong decoderHandle,
        jint bus,
        jint address,
        jbyteArray data) {

    auto* decoder = reinterpret_cast<VehicleDecoder*>(decoderHandle);
    jsize dataLen = env->GetArrayLength(data);
    jbyte* dataBytes = env->GetByteArrayElements(data, nullptr);

    decoder->updateFrame(bus, static_cast<uint32_t>(address),
                         reinterpret_cast<const uint8_t*>(dataBytes), dataLen);
    decoder->updateMapper();

    env->ReleaseByteArrayElements(data, dataBytes, 0);

    // Return full CarState as double array
    double output[CarState::FIELD_COUNT];
    decoder->state().toArray(output);

    jdoubleArray result = env->NewDoubleArray(CarState::FIELD_COUNT);
    env->SetDoubleArrayRegion(result, 0, CarState::FIELD_COUNT, output);
    return result;
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDestroyVehicleDecoder(
        JNIEnv* env, jobject thiz,
        jlong decoderHandle) {
    if (decoderHandle == 0) {
        return;
    }
    auto* decoder = reinterpret_cast<VehicleDecoder*>(decoderHandle);
    delete decoder;
}

// Receive loop using VehicleDecoder + SubSocketGroup (for CommaDataSource / ZMQ)
JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeStartReceiveLoop(
        JNIEnv* env,
        jobject thiz,
        jlong decoderHandle,
        jlong groupPtr,
        jdoubleArray buffer,
        jobject callback) {

    auto* decoder = reinterpret_cast<VehicleDecoder*>(decoderHandle);
    auto* group = reinterpret_cast<SubSocketGroup*>(groupPtr);

    g_callback = env->NewGlobalRef(callback);
    g_buffer = (jdoubleArray) env->NewGlobalRef(buffer);

    jclass callbackClass = env->GetObjectClass(callback);
    g_onCanDataMethod = env->GetMethodID(callbackClass, "onCanData", "([D)V");

    receiveLoopRunning = true;

    long long msgTimeAccum = 0;
    int msgCount = 0;

    double madsActive = 0.0;
    double experimentalMode = 0.0;
    double selfdriveActive = 0.0;
    double changingLane = 0.0;

    while (receiveLoopRunning) {
        bool gotAny = false;

        for (auto* sub : group->sockets) {
            Message *msg = sub->receive(true);
            if (!msg) continue;
            gotAny = true;

            auto t0 = std::chrono::high_resolution_clock::now();

            if (processMessage(msg, decoder, madsActive, experimentalMode, selfdriveActive, changingLane)) {
                double output[CarState::FIELD_COUNT];
                decoder->state().toArray(output);
                env->SetDoubleArrayRegion(g_buffer, 0, CarState::FIELD_COUNT, output);
                env->CallVoidMethod(g_callback, g_onCanDataMethod, g_buffer);
            }

            delete msg;

            auto t1 = std::chrono::high_resolution_clock::now();
            msgTimeAccum += std::chrono::duration_cast<std::chrono::microseconds>(t1 - t0).count();
            msgCount++;
            if (msgCount >= 100) {
                LOGD("receiveLoop avg: %.3f ms/msg (over %d msgs)", msgTimeAccum / 1000.0 / msgCount, msgCount);
                msgTimeAccum = 0;
                msgCount = 0;
            }
        }

        if (!gotAny) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
}

}
