#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "common/vehicle_decoder.h"

#define BRIDGE_LOG(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "Bridge", fmt, ##__VA_ARGS__)

extern "C" {

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

    auto* decoder = new VehicleDecoder(dbcVec, busVec, type);
    BRIDGE_LOG("VehicleDecoder created: type=%s, %d buses", type.c_str(), dbcCount);
    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jdoubleArray JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDecodeCanFrames(
        JNIEnv* env, jobject thiz,
        jlong decoderHandle,
        jintArray buses,
        jintArray addresses,
        jintArray lengths,
        jbyteArray data) {

    auto* decoder = reinterpret_cast<VehicleDecoder*>(decoderHandle);
    jsize count = env->GetArrayLength(buses);
    std::vector<jint> bus(count), address(count), length(count);
    env->GetIntArrayRegion(buses, 0, count, bus.data());
    env->GetIntArrayRegion(addresses, 0, count, address.data());
    env->GetIntArrayRegion(lengths, 0, count, length.data());
    jsize dataLen = env->GetArrayLength(data);
    jbyte* dataBytes = env->GetByteArrayElements(data, nullptr);

    // One BLE packet carries several frames. Map after each one, not once at
    // the end: multiplexed messages (TPMS, VIN, liftgate, ...) can repeat
    // within a packet and the mapper only sees each message's latest frame.
    jsize offset = 0;
    for (jsize i = 0; i < count; i++) {
        if (length[i] < 0 || length[i] > dataLen - offset) break;
        decoder->updateFrame(bus[i], static_cast<uint32_t>(address[i]),
                             reinterpret_cast<const uint8_t*>(dataBytes + offset), length[i]);
        decoder->updateMapper();
        offset += length[i];
    }

    env->ReleaseByteArrayElements(data, dataBytes, JNI_ABORT);  // read-only: no copy back

    double output[CarState::FIELD_COUNT];
    decoder->state().toArray(output);

    jdoubleArray result = env->NewDoubleArray(CarState::FIELD_COUNT);
    env->SetDoubleArrayRegion(result, 0, CarState::FIELD_COUNT, output);
    return result;
}

// DashKit CAN filter payload for this decoder's vehicle (the ids its mapper
// reads); empty if there is nothing to subscribe to.
JNIEXPORT jbyteArray JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeCanFilterPayload(
        JNIEnv* env, jobject thiz,
        jlong decoderHandle) {
    std::vector<uint8_t> payload;
    if (decoderHandle != 0) {
        payload = reinterpret_cast<VehicleDecoder*>(decoderHandle)->canFilterPayload();
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(payload.size()));
    if (result != nullptr && !payload.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(payload.size()),
                                reinterpret_cast<const jbyte*>(payload.data()));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeDestroyVehicleDecoder(
        JNIEnv* env, jobject thiz,
        jlong decoderHandle) {
    if (decoderHandle == 0) return;
    delete reinterpret_cast<VehicleDecoder*>(decoderHandle);
}

}
