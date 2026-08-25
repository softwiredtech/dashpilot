package com.softwiredtech.dashpilot.datasource

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CanPacketVinTest {
    @Test
    fun parses_vin_frames_from_ble_notification() {
        val vin = "5YJ3E7EB1MF123456"
        val frames = listOf(
            Triple(0x12, vin.substring(10), 1),
            Triple(0x10, vin.substring(0, 3), 5),
            Triple(0x11, vin.substring(3, 10), 1),
        )
        val packet = ByteBuffer.allocate(1 + frames.size * 18)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(frames.size.toByte())
        for ((mux, text, offset) in frames) {
            packet.putInt(0)
            packet.put(1.toByte())
            packet.putInt(0x405)
            packet.put(8.toByte())
            val data = ByteArray(8)
            data[0] = mux.toByte()
            text.forEachIndexed { index, char -> data[offset + index] = char.code.toByte() }
            packet.put(data)
        }

        val parsed = parseCanPacket(packet.array())

        assertEquals(frames.size, parsed.size)
        for ((i, frame) in parsed.withIndex()) {
            val (mux, text, offset) = frames[i]
            assertEquals(1, frame.bus)
            assertEquals(0x405, frame.address)
            assertEquals(8, frame.data.size)
            assertEquals(mux.toByte(), frame.data[0])
            text.forEachIndexed { index, char ->
                assertEquals(char.code.toByte(), frame.data[offset + index])
            }
        }
    }
}
