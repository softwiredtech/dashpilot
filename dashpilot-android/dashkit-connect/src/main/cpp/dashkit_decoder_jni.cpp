#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "common/vehicle_decoder.h"

#define LOG_TAG "DashKitDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_softwiredtech_dashkitconnect_can_DashKitDecoder_nativeCreate(
        JNIEnv* env, jclass,
        jobjectArray dbcContents,
        jintArray busIndices,
        jstring vehicleType) {

    int count = env->GetArrayLength(dbcContents);
    std::vector<std::string> dbcVec;
    std::vector<int> busVec;

    jint* buses = env->GetIntArrayElements(busIndices, nullptr);
    for (int i = 0; i < count; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(dbcContents, i);
        const char* str = env->GetStringUTFChars(jstr, nullptr);
        dbcVec.emplace_back(str);
        env->ReleaseStringUTFChars(jstr, str);
        env->DeleteLocalRef(jstr);
        busVec.push_back(buses[i]);
    }
    env->ReleaseIntArrayElements(busIndices, buses, JNI_ABORT);

    const char* typeStr = env->GetStringUTFChars(vehicleType, nullptr);
    std::string type(typeStr);
    env->ReleaseStringUTFChars(vehicleType, typeStr);

    auto* decoder = new VehicleDecoder(dbcVec, busVec, type);
    LOGI("Decoder created: type=%s, %d buses", type.c_str(), count);
    return reinterpret_cast<jlong>(decoder);
}

// Feeds one notification's worth of frames through the parser and mapper.
// Frames are passed as parallel arrays plus one concatenated data buffer so
// a batch costs a single JNI transition; the resulting CarState is written
// into `out` (CarState::FIELD_COUNT doubles).
JNIEXPORT void JNICALL
Java_com_softwiredtech_dashkitconnect_can_DashKitDecoder_nativeDecode(
        JNIEnv* env, jclass,
        jlong handle,
        jint frameCount,
        jintArray buses,
        jintArray addresses,
        jintArray lengths,
        jbyteArray data,
        jdoubleArray out) {

    auto* decoder = reinterpret_cast<VehicleDecoder*>(handle);
    if (decoder == nullptr) return;

    jint* bus = env->GetIntArrayElements(buses, nullptr);
    jint* addr = env->GetIntArrayElements(addresses, nullptr);
    jint* len = env->GetIntArrayElements(lengths, nullptr);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    jsize available = env->GetArrayLength(data);

    jsize offset = 0;
    for (jint i = 0; i < frameCount; i++) {
        if (offset + len[i] > available) break;
        decoder->updateFrame(bus[i], static_cast<uint32_t>(addr[i]),
                             reinterpret_cast<const uint8_t*>(bytes + offset), len[i]);
        // Mapper runs per frame so multiplexed messages (VIN segments,
        // BMS muxes) are observed individually, matching the comma path.
        decoder->updateMapper();
        offset += len[i];
    }

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    env->ReleaseIntArrayElements(lengths, len, JNI_ABORT);
    env->ReleaseIntArrayElements(addresses, addr, JNI_ABORT);
    env->ReleaseIntArrayElements(buses, bus, JNI_ABORT);

    double state[CarState::FIELD_COUNT];
    decoder->state().toArray(state);
    env->SetDoubleArrayRegion(out, 0, CarState::FIELD_COUNT, state);
}

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashkitconnect_can_DashKitDecoder_nativeDestroy(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<VehicleDecoder*>(handle);
}

}
