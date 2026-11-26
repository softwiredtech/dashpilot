package com.softwiredtech.dashpilot.jni

class VehicleBridge {
    init {
        System.loadLibrary("bridge")
    }

    // VehicleDecoder
    external fun nativeCreateVehicleDecoder(dbcContents: Array<String>, busIndices: IntArray, vehicleType: String): Long
    // Feeds frames i = 0..n-1 (data[i] is the next lengths[i] bytes of `data`),
    // mapping after each, and returns the resulting CarState array.
    external fun nativeDecodeCanFrames(
        decoderHandle: Long, buses: IntArray, addresses: IntArray, lengths: IntArray, data: ByteArray
    ): DoubleArray
    external fun nativeDestroyVehicleDecoder(decoderHandle: Long)
    // DashKit CAN filter payload: the (bus, id) list this decoder's mapper reads.
    external fun nativeCanFilterPayload(decoderHandle: Long): ByteArray
}
