package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.viewmodel.ConnectionViewModel

@Composable
fun PairingPage(
    connectionVM: ConnectionViewModel,
    state: PairingState,
    onStateChanged: (PairingState) -> Unit,
    onAdvance: () -> Unit,
) {
    val context = LocalContext.current
    val connStatus by connectionVM.connectionStatus.collectAsState()

    LaunchedEffect(connStatus) {
        when (connStatus) {
            is ConnectionStatus.Connected ->
                if (state != PairingState.Paired) onStateChanged(PairingState.Paired)
            is ConnectionStatus.Error, ConnectionStatus.Disconnected ->
                if (state == PairingState.Searching) onStateChanged(PairingState.Idle)
            else -> Unit
        }
    }

    val (titleRes, subtitleRes, ctaRes) = when (state) {
        PairingState.Idle -> Triple(
            R.string.onboarding_pair_idle_title,
            R.string.onboarding_pair_idle_subtitle,
            R.string.onboarding_pair_cta,
        )
        PairingState.Searching -> Triple(
            R.string.onboarding_pair_searching_title,
            R.string.onboarding_pair_searching_subtitle,
            R.string.onboarding_pair_searching_cta,
        )
        PairingState.Paired -> Triple(
            R.string.onboarding_pair_success_title,
            R.string.onboarding_pair_success_subtitle,
            R.string.onboarding_pair_success_cta,
        )
    }

    OnboardingPageScaffold(
        title = stringResource(titleRes),
        subtitle = stringResource(subtitleRes),
        hero = { DevicePuck(state = state, modifier = Modifier.size(220.dp)) },
        cta = {
            PrimaryCta(
                label = stringResource(ctaRes),
                icon = if (state == PairingState.Idle) Icons.Outlined.Bluetooth else null,
                showSpinner = state == PairingState.Searching,
                enabled = state != PairingState.Searching,
                onClick = {
                    when (state) {
                        PairingState.Idle -> {
                            onStateChanged(PairingState.Searching)
                            connectionVM.connect(context, "", DataSourceType.DASHKIT)
                        }
                        PairingState.Searching -> Unit
                        PairingState.Paired -> onAdvance()
                    }
                },
            )
        },
    )
}
