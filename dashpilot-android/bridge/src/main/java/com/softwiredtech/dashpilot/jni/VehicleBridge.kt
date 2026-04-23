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

    // SubSocketGroup (multi-endpoint subscription)
    external fun nativeCreateSubSockets(
        ctxPtr: Long,
        endpoints: Array<String>,
        address: String
    ): Long

    external fun nativeDeleteSubSockets(groupPtr: Long)

    // Discover a ZMQ publisher on the local subnet.
    // Returns the IP address of the publisher, or null if not found.
    external fun nativeDiscoverPublisher(
        ctxPtr: Long,
        endpoint: String,
        initialIp: String,
        timeoutMs: Int
    ): String?

    // Receive messages using VehicleDecoder + SubSocketGroup
    external fun nativeStartReceiveLoop(decoderHandle: Long, groupPtr: Long, buffer: DoubleArray, callback: CanSignalCallback)
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
