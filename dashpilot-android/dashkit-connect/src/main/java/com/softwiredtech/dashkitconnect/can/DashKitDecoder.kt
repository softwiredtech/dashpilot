package com.softwiredtech.dashkitconnect.can

import android.content.Context
import com.softwiredtech.dashkitconnect.CarState

/**
 * Native CAN-to-CarState decoder (DBC parsing + vehicle mapper, shared with
 * the comma bridge). Create it off the main thread: parsing the Tesla DBC
 * takes a noticeable fraction of a second. Not thread-safe; callers serialize
 * [decode] and [close].
 */
class DashKitDecoder private constructor(private var handle: Long) {

    private val out = DoubleArray(CarState.FIELD_COUNT)

    /** Run one batch of frames through the decoder and return the resulting state. */
    fun decode(frames: List<RawCanFrame>): CarState {
        check(handle != 0L) { "decoder is closed" }
        val n = frames.size
        val buses = IntArray(n)
        val addresses = IntArray(n)
        val lengths = IntArray(n)
        var total = 0
        for (i in 0 until n) {
            val f = frames[i]
            buses[i] = f.bus
            addresses[i] = f.address
            lengths[i] = f.data.size
            total += f.data.size
        }
        val data = ByteArray(total)
        var offset = 0
        for (f in frames) {
            f.data.copyInto(data, offset)
            offset += f.data.size
        }
        nativeDecode(handle, n, buses, addresses, lengths, data, out)
        return CarState.fromArray(out)
    }

    fun close() {
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0L
    }

    companion object {
        init {
            System.loadLibrary("dashkitdecoder")
        }

        /** Blocking; reads the profile's DBC assets and builds the parser. */
        fun create(context: Context, profile: DashKitVehicleProfile = DashKitVehicleProfile.Tesla): DashKitDecoder {
            val handle = nativeCreate(profile.loadDbcContents(context), profile.busIndices, profile.vehicleType)
            return DashKitDecoder(handle)
        }

        @JvmStatic
        private external fun nativeCreate(dbcContents: Array<String>, busIndices: IntArray, vehicleType: String): Long

        @JvmStatic
        private external fun nativeDecode(
            handle: Long,
            frameCount: Int,
            buses: IntArray,
            addresses: IntArray,
            lengths: IntArray,
            data: ByteArray,
            out: DoubleArray,
        )

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
