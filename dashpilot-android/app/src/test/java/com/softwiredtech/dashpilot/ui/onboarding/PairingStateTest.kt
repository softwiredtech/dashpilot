package com.softwiredtech.dashpilot.ui.onboarding

import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingStateTest {
    @Test fun onboarding_reflects_connection_readiness_failures_and_reconnects() {
        val statuses = listOf(
            ConnectionStatus.Disconnected,
            ConnectionStatus.Connecting,
            ConnectionStatus.Error("Subscription failed"),
            ConnectionStatus.Connecting,
            ConnectionStatus.Connected,
            ConnectionStatus.Disconnected,
            ConnectionStatus.Connecting,
            ConnectionStatus.Connected,
        )
        assertEquals(
            listOf(PairingState.Idle, PairingState.Searching, PairingState.Idle,
                PairingState.Searching, PairingState.Paired, PairingState.Idle,
                PairingState.Searching, PairingState.Paired),
            statuses.map { it.toPairingState() },
        )
    }
}
