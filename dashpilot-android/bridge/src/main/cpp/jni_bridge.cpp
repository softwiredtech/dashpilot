#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include "common/bridge_core.h"

extern "C" {

static std::atomic<bool> receiveLoopRunning{false};
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
    return reinterpret_cast<jlong>(bridge::createContext());
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteContext(JNIEnv *env, jobject thiz,
                                                           jlong ctxPtr) {
    if (ctxPtr == 0) return;
    bridge::deleteContext(reinterpret_cast<Context*>(ctxPtr));
}

// === SubSocket (single) ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCreateSubSocket(
        JNIEnv* env, jobject thiz,
        jlong ctxPtr,
        jstring endpoint,
        jstring address) {

    Context* ctx = reinterpret_cast<Context*>(ctxPtr);

    const char* epChars = env->GetStringUTFChars(endpoint, nullptr);
    const char* addrChars = env->GetStringUTFChars(address, nullptr);
    std::string endpointStr(epChars);
    std::string addressStr(addrChars);
    env->ReleaseStringUTFChars(endpoint, epChars);
    env->ReleaseStringUTFChars(address, addrChars);

    SubSocket* sub = SubSocket::create(ctx, endpointStr, addressStr, false, true, 0);
    return reinterpret_cast<jlong>(sub);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteSubSocket(JNIEnv* env, jobject thiz, jlong subPtr) {
    if (subPtr == 0) return;
    delete reinterpret_cast<SubSocket*>(subPtr);
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
    std::vector<std::string> eps;
    for (int i = 0; i < count; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(endpoints, i);
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        eps.emplace_back(chars);
        env->ReleaseStringUTFChars(jstr, chars);
    }

    auto* group = bridge::createSubSockets(ctx, eps, addressStr);
    for (const auto& ep : eps) {
        BRIDGE_LOG("SubSocketGroup: subscribed to '%s' at %s", ep.c_str(), addressStr.c_str());
    }
    return reinterpret_cast<jlong>(group);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDeleteSubSockets(
        JNIEnv* env, jobject thiz, jlong groupPtr) {
    if (groupPtr == 0) return;
    bridge::deleteSubSockets(reinterpret_cast<SubSocketGroup*>(groupPtr));
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeStopReceiveLoop(JNIEnv* env, jobject thiz) {
    receiveLoopRunning.store(false);
}

// === Publisher discovery ===
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

    BRIDGE_LOG("Discovery: starting on subnet %s, timeout %dms", ipStr.c_str(), timeoutMs);
    std::string foundIp = bridge::discoverPublisher(ctx, endpointStr, ipStr, timeoutMs);

    if (foundIp.empty()) {
        BRIDGE_LOG("Discovery: no publisher found");
        return nullptr;
    }

    BRIDGE_LOG("Discovery: found publisher at %s", foundIp.c_str());
    return env->NewStringUTF(foundIp.c_str());
}

// === Raw message accessors ===
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
    delete reinterpret_cast<Message*>(msgPtr);
}

// === VehicleDecoder ===
JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCreateVehicleDecoder(
        JNIEnv* env, jobject thiz,
        jobjectArray dbcContents,
        jintArray busIndices,
        jstring vehicleType) {

    int dbcCount = env->GetArrayLength(dbcContents);
    std::vector<std::string> dbcVec;
    std::vector<int> busVec;

    jint* busArr = env->GetIntArrayElements(busIndices, nullptr);
    for (int i = 0; i < dbcCount; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(dbcContents, i);
        const char* str = env->GetStringUTFChars(jstr, nullptr);
        dbcVec.emplace_back(str);
        env->ReleaseStringUTFChars(jstr, str);
        busVec.push_back(busArr[i]);
    }
    env->ReleaseIntArrayElements(busIndices, busArr, 0);

    const char* typeStr = env->GetStringUTFChars(vehicleType, nullptr);
    std::string type(typeStr);
    env->ReleaseStringUTFChars(vehicleType, typeStr);

    auto* decoder = bridge::createVehicleDecoder(dbcVec, busVec, type);
    BRIDGE_LOG("VehicleDecoder created: type=%s, %d buses", type.c_str(), dbcCount);
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
    if (decoderHandle == 0) return;
    bridge::destroyVehicleDecoder(reinterpret_cast<VehicleDecoder*>(decoderHandle));
}

// === Receive loop ===
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

    receiveLoopRunning.store(true);

    bridge::runReceiveLoop(group, decoder, receiveLoopRunning,
        [&](const CarState& state) {
            double output[CarState::FIELD_COUNT];
            state.toArray(output);
            env->SetDoubleArrayRegion(g_buffer, 0, CarState::FIELD_COUNT, output);
            env->CallVoidMethod(g_callback, g_onCanDataMethod, g_buffer);
        });
}

}
