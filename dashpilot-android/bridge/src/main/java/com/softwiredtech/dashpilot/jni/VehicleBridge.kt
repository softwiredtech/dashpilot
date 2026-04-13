package com.softwiredtech.dashpilot.jni

fun interface CanSignalCallback {
    fun onCanData(values: DoubleArray)
}
class VehicleBridge {
    init {
        System.loadLibrary("bridge")
    }

    // Context
    external fun nativeCreateContext(): Long
    external fun nativeDeleteContext(ctxPtr: Long)

    // SubSocket
    external fun nativeCreateSubSocket(
        ctxPtr: Long,
        endpoint: String,
        address: String
    ): Long

    external fun nativeDeleteSubSocket(subPtr: Long)

    // Probe a candidate publisher: returns true if a message was received
    // within `timeoutMs`, false otherwise. Used for endpoint discovery.
    external fun nativeProbeSubSocket(
        ctxPtr: Long,
        endpoint: String,
        address: String,
        timeoutMs: Int
    ): Boolean

    // Receive messages using VehicleDecoder
    external fun nativeStartReceiveLoop(decoderHandle: Long, subPtr: Long, buffer: DoubleArray, callback: CanSignalCallback)
    external fun nativeStopReceiveLoop()

    // VehicleDecoder
    external fun nativeCreateVehicleDecoder(dbcContents: Array<String>, busIndices: IntArray, vehicleType: String): Long
    external fun nativeDecodeCanFrame(decoderHandle: Long, bus: Int, address: Int, data: ByteArray): DoubleArray
    external fun nativeDestroyVehicleDecoder(decoderHandle: Long)

    // Message
    external fun nativeGetData(msgPtr: Long): ByteArray
    external fun nativeGetSize(msgPtr: Long): Int
    external fun nativeDeleteMessage(msgPtr: Long)
}
