package com.softwiredtech.dashpilot.datamodel.dash

import org.junit.Assert.*
import org.junit.Test

class CarStateTest {
    @Test
    fun toImperial_convertsSpeeds() {
        val s = CarState(egoSpeed = 100f, accSetSpeed = 80f)
        val r = s.toImperial()
        assertEquals(100f * 0.621371f, r.egoSpeed, 0.001f)
        assertEquals(80f * 0.621371f, r.accSetSpeed, 0.001f)
    }
}
