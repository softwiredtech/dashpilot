package com.softwiredtech.dashpilot.datasource

class RawCanFrame(
    val bus: Int,
    val address: Int,
    val data: ByteArray,
)

// Wire format from firmware (build_ble_packet):
//   [count : 1]
//   per frame:
//     [timestamp_us : LE32]
//     [bus          : 1]
//     [addr         : LE32]
//     [len          : 1]
//     [data         : len bytes]
internal fun parseCanPacket(payload: ByteArray): List<RawCanFrame> {
    if (payload.isEmpty()) return emptyList()
    val frames = ArrayList<RawCanFrame>(payload[0].toInt() and 0xFF)
    var offset = 1
    val count = payload[0].toInt() and 0xFF
    for (i in 0 until count) {
        if (offset + 4 > payload.size) break
        offset += 4
        if (offset >= payload.size) break
        val bus = payload[offset].toInt() and 0xFF
        offset += 1
        if (offset + 4 > payload.size) break
        val addr = java.nio.ByteBuffer.wrap(payload, offset, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        offset += 4
        if (offset >= payload.size) break
        val len = payload[offset].toInt() and 0xFF
        offset += 1
        if (offset + len > payload.size) break
        frames.add(RawCanFrame(bus, addr, payload.copyOfRange(offset, offset + len)))
        offset += len
    }
    return frames
}
