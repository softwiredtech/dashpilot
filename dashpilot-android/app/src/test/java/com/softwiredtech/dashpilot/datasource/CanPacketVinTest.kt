package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.vehicle.VehicleVinAssembler
import com.softwiredtech.dashpilot.vehicle.VehicleVinState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CanPacketVinTest {
    @Test
    fun assembles_vin_from_ble_notification() {
        val vin = "5YJ3E7EB1MF123456"
        val frames = listOf(
            vinFrame(0x12, vin.substring(10), 1),
            vinFrame(0x10, vin.substring(0, 3), 5),
            vinFrame(0x11, vin.substring(3, 10), 1),
        )
        val packet = ByteBuffer.allocate(1 + frames.size * 18)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(frames.size.toByte())
        for (frame in frames) {
            packet.putInt(0)
            packet.put(1.toByte())
            packet.putInt(0x405)
            packet.put(8.toByte())
            packet.put(frame)
        }

        val assembler = VehicleVinAssembler()
        var state: VehicleVinState = VehicleVinState.Waiting
        for (frame in parseCanPacket(packet.array())) {
            state = assembler.onFrame(frame.bus, frame.address, frame.data)
        }

        assertEquals(vin, (state as VehicleVinState.Available).vin)
    }

    private fun vinFrame(mux: Int, text: String, offset: Int): ByteArray {
        val data = ByteArray(8)
        data[0] = mux.toByte()
        text.forEachIndexed { index, char -> data[offset + index] = char.code.toByte() }
        return data
    }
}
