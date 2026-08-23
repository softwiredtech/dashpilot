package com.softwiredtech.dashpilot

import com.softwiredtech.dashpilot.ble.TeslaFaultDetail
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeslaStatusTest {

    @Test
    fun `parse full connected frame`() {
        // [version=1][link=0x02 connected][presence=1][lock=1][sleep=1][flags=0][fault=0xFF]
        val frame = byteArrayOf(0x01, 0x02, 0x01, 0x01, 0x01, 0x00, 0xFF.toByte())
        val st = TeslaStatus.parse(frame)!!
        assertEquals(TeslaLinkState.EnrolledConnected, st.linkState)
        assertTrue(st.linkState.hasKey)
        assertTrue(st.linkState.connected)
        assertEquals(1, st.presence)
        assertEquals(1, st.lock)
        assertEquals(1, st.sleep)
        assertEquals(0, st.flags)
        assertEquals(TeslaFaultDetail.None, st.faultDetail)
    }

    @Test
    fun `parse staged frame maps to staged`() {
        val frame = byteArrayOf(0x01, 0x05, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xFF.toByte())
        val st = TeslaStatus.parse(frame)!!
        assertEquals(TeslaLinkState.Staged, st.linkState)
        assertFalse(st.linkState.hasKey)
        assertFalse(st.linkState.connected)
    }

    @Test
    fun `parse fault frame surfaces fault detail`() {
        // link_state 0x04 (fault), fault_detail 0x00 (tap window expired)
        val frame = byteArrayOf(0x01, 0x04, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00)
        val st = TeslaStatus.parse(frame)!!
        assertEquals(TeslaLinkState.EnrollmentFault, st.linkState)
        assertEquals(TeslaFaultDetail.TapTimeout, st.faultDetail)
    }

    @Test
    fun `parse rejects short frames`() {
        assertNull(TeslaStatus.parse(byteArrayOf(0x01, 0x02, 0x01)))
        assertNull(TeslaStatus.parse(byteArrayOf()))
    }

    @Test
    fun `parse rejects unsupported frame versions`() {
        assertNull(TeslaStatus.parse(byteArrayOf(0x02, 0x02, 0x01, 0x01, 0x01, 0x00, 0xFF.toByte())))
    }

    @Test
    fun `link state unknown for out of range byte`() {
        val frame = byteArrayOf(0x01, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xFF.toByte())
        val st = TeslaStatus.parse(frame)!!
        assertEquals(TeslaLinkState.Unknown, st.linkState)
        assertFalse(st.linkState.hasKey)
        assertFalse(st.linkState.connected)
    }

    @Test
    fun `enrolled not connected has key but not connected`() {
        val frame = byteArrayOf(0x01, 0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xFF.toByte())
        val st = TeslaStatus.parse(frame)!!
        assertEquals(TeslaLinkState.EnrolledNotConnected, st.linkState)
        assertTrue(st.linkState.hasKey)
        assertFalse(st.linkState.connected)
    }
}
