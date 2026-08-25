package com.softwiredtech.dashpilot.ui.tesla

import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TeslaTileTest {
    @Test
    fun pending_removal_hides_connected_status() {
        val status = TeslaStatus.Idle.copy(linkState = TeslaLinkState.EnrolledConnected)

        assertEquals(R.string.tesla_connection_removing, teslaTileTextRes(status, true))
    }
}
