package com.softwiredtech.dashpilot.ui.onboarding

import com.softwiredtech.dashpilot.datasource.ConnectionStatus

enum class PairingState { Idle, Searching, Paired }

internal fun ConnectionStatus.toPairingState(): PairingState = when (this) {
    ConnectionStatus.Connected -> PairingState.Paired
    ConnectionStatus.Connecting -> PairingState.Searching
    ConnectionStatus.Disconnected, is ConnectionStatus.Error -> PairingState.Idle
}
