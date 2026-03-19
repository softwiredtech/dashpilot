#include <jni.h>
#include <string>
#include <iostream>
#include <cmath>
#include <memory>
#include "msgq/ipc.h"
#include "dbc/dbcfile.h"
#include "car/car_state.h"
#include "car/can_parsers.h"
#include "car/car_state_mapper.h"
#include "car/cars/tesla.h"
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

static std::unique_ptr<CarStateMapper> createMapper(const std::string& vehicleType) {
    if (vehicleType == "tesla") return std::make_unique<TeslaCarState>();
    // Add new vehicles here:
    // if (vehicleType == "honda") return std::make_unique<HondaCarState>();
    return nullptr;
}

class VehicleDecoder {
public:
    VehicleDecoder(const std::vector<std::string>& dbcContents,
                   const std::vector<int>& busIndices,
                   const std::string& vehicleType) {

        for (size_t i = 0; i < dbcContents.size(); i++) {
            parsers_.addBus(busIndices[i], dbcContents[i]);
        }
        parsers_.buildCache();

        mapper_ = createMapper(vehicleType);
        if (!mapper_) {
            LOGD("VehicleDecoder: unknown vehicle type '%s'", vehicleType.c_str());
        }
    }

    // Feed a CAN frame, update internal state
    void updateFrame(int bus, uint32_t address, const uint8_t* data, size_t len) {
        parsers_.updateFrame(bus, address, data, len);
        if (mapper_) {
            mapper_->update(parsers_, state_);
        }
    }

    const CarState& state() const { return state_; }

private:
    CANParsers parsers_;
    std::unique_ptr<CarStateMapper> mapper_;
    CarState state_;
};

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

JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeStopReceiveLoop(JNIEnv* env, jobject thiz) {
    receiveLoopRunning = false;
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

// Receive loop using VehicleDecoder (for CommaDataSource / ZMQ)
JNIEXPORT void JNICALL
Java_com_softwiredtech_dashpilot_jni_VehicleBridge_nativeStartReceiveLoop(
        JNIEnv* env,
        jobject thiz,
        jlong decoderHandle,
        jlong subPtr,
        jdoubleArray buffer,
        jobject callback) {

    auto* decoder = reinterpret_cast<VehicleDecoder*>(decoderHandle);

    g_callback = env->NewGlobalRef(callback);
    g_buffer = (jdoubleArray) env->NewGlobalRef(buffer);

    jclass callbackClass = env->GetObjectClass(callback);
    g_onCanDataMethod = env->GetMethodID(callbackClass, "onCanData", "([D)V");

    auto sub = reinterpret_cast<SubSocket*>(subPtr);
    receiveLoopRunning = true;

    sub->setTimeout(0);

    while (receiveLoopRunning) {
        Message *msg = sub->receive(true);
        if (msg) {

            kj::ArrayPtr<capnp::word> canArray = kj::ArrayPtr<capnp::word>(
                (capnp::word*)msg->getData(), msg->getSize() / sizeof(capnp::word));
            capnp::FlatArrayMessageReader reader(canArray);
            auto event = reader.getRoot<cereal::Event>();

            if (event.which() == cereal::Event::Which::CAN) {
                auto canList = event.getCan();

                for (const auto &c : canList) {
                    decoder->updateFrame(c.getSrc(), c.getAddress(),
                                         reinterpret_cast<const uint8_t*>(c.getDat().begin()),
                                         c.getDat().size());
                }

                double output[CarState::FIELD_COUNT];
                decoder->state().toArray(output);

                env->SetDoubleArrayRegion(g_buffer, 0, CarState::FIELD_COUNT, output);
                env->CallVoidMethod(g_callback, g_onCanDataMethod, g_buffer);
            }
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
    }
}

}
