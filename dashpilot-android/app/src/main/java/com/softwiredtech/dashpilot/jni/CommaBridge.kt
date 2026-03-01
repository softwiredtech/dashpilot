package com.softwiredtech.dashpilot.jni

fun interface CanSignalCallback {
    fun onCanData(values: DoubleArray)
}
class CommaBridge {
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

    // DBC
    external fun loadDbcFile(dbcPath: String): Boolean

    // Receive messages
    external fun nativeStartReceiveLoop(subPtr: Long, buffer: DoubleArray, callback: CanSignalCallback)
    external fun nativeStopReceiveLoop()

    // Message
    external fun nativeGetData(msgPtr: Long): ByteArray
    external fun nativeGetSize(msgPtr: Long): Int
    external fun nativeDeleteMessage(msgPtr: Long)
}