package com.softwiredtech.dashpilot.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleVinAssemblerTest {
    private val vin = "5YJ3E7EB1MF123456"

    private fun frame(mux: Int, text: String): ByteArray {
        val data = ByteArray(8)
        data[0] = mux.toByte()
        val offset = if (mux == VehicleVinAssembler.MUX_A) 5 else 1
        text.forEachIndexed { index, char -> data[offset + index] = char.code.toByte() }
        return data
    }

    private fun frameA() = frame(VehicleVinAssembler.MUX_A, vin.substring(0, 3))
    private fun frameB() = frame(VehicleVinAssembler.MUX_B, vin.substring(3, 10))
    private fun frameC() = frame(VehicleVinAssembler.MUX_C, vin.substring(10, 17))

    @Test
    fun assembles_out_of_order_frames() {
        val assembler = VehicleVinAssembler()
        assembler.onFrame(1, 0x405, frameC())
        assembler.onFrame(1, 0x405, frameA())
        assembler.onFrame(1, 0x405, frameA())
        val state = assembler.onFrame(1, 0x405, frameB())

        assertEquals(vin, (state as VehicleVinState.Available).vin)
    }

    @Test
    fun rejects_illegal_vin_characters() {
        val assembler = VehicleVinAssembler()
        val invalid = frameC().also { it[1] = 'I'.code.toByte() }
        assembler.onFrame(1, 0x405, frameA())
        assembler.onFrame(1, 0x405, frameB())
        val state = assembler.onFrame(1, 0x405, invalid)

        assertTrue(state is VehicleVinState.Invalid)
    }

    @Test
    fun reset_prevents_cross_session_assembly() {
        val assembler = VehicleVinAssembler()
        assembler.onFrame(1, 0x405, frameA())
        assembler.onFrame(1, 0x405, frameB())
        assembler.reset()
        val state = assembler.onFrame(1, 0x405, frameC())

        assertEquals(VehicleVinState.Waiting, state)
    }
}
