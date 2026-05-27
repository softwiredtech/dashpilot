package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.viewmodel.ConnectionViewModel
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors

private data class PairingCopySnapshot(
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val ctaEnabled: Boolean,
    val showSpinner: Boolean
)

@Composable
fun PairingPage(
    page: OnboardingPage.Pairing,
    connectionVM: ConnectionViewModel,
    onCanAdvanceChanged: (Boolean) -> Unit,
    onPairingStateChanged: (PairingState) -> Unit,
    onAdvance: () -> Unit
) {
    val context = LocalContext.current
    var state by rememberSaveable { mutableStateOf(PairingState.Idle) }
    val connStatus by connectionVM.connectionStatus.collectAsState()

    LaunchedEffect(connStatus) {
        when (connStatus) {
            is ConnectionStatus.Connected -> if (state == PairingState.Searching) state = PairingState.Paired
            is ConnectionStatus.Error, ConnectionStatus.Disconnected ->
                if (state == PairingState.Searching) state = PairingState.Idle
            else -> Unit
        }
    }

    LaunchedEffect(state) {
        onCanAdvanceChanged(state == PairingState.Paired)
        onPairingStateChanged(state)
    }

    val copy = when (state) {
        PairingState.Idle -> PairingCopySnapshot(
            title = page.pairingCopy.idleTitle,
            subtitle = page.pairingCopy.idleSubtitle,
            ctaLabel = page.primaryCta.label,
            ctaEnabled = true,
            showSpinner = false
        )
        PairingState.Searching -> PairingCopySnapshot(
            title = page.pairingCopy.searchingTitle,
            subtitle = page.pairingCopy.searchingSubtitle,
            ctaLabel = stringResource(R.string.onboarding_pair_searching_cta),
            ctaEnabled = false,
            showSpinner = true
        )
        PairingState.Paired -> PairingCopySnapshot(
            title = page.pairingCopy.successTitle,
            subtitle = page.pairingCopy.successSubtitle,
            ctaLabel = page.pairingCopy.successCtaLabel,
            ctaEnabled = true,
            showSpinner = false
        )
    }

    OnboardingPageScaffold(
        title = copy.title,
        subtitle = copy.subtitle,
        hero = {
            DevicePuck(
                state = state,
                modifier = Modifier.size(page.media.heightDp.dp)
            )
        },
        cta = {
            Button(
                onClick = {
                    when (state) {
                        PairingState.Idle -> {
                            state = PairingState.Searching
                            connectionVM.connect(context, "", DataSourceType.DASHKIT)
                        }
                        PairingState.Searching -> Unit
                        PairingState.Paired -> onAdvance()
                    }
                },
                enabled = copy.ctaEnabled,
                shape = RoundedCornerShape(OnboardingTokens.RadiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnboardingColors.Accent,
                    contentColor = Color.Black,
                    disabledContainerColor = OnboardingColors.Accent,
                    disabledContentColor = Color.Black
                ),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (copy.showSpinner) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                } else if (page.primaryCta.icon != null && state == PairingState.Idle) {
                    Icon(
                        imageVector = page.primaryCta.icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                }
                Text(
                    text = copy.ctaLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        secondaryCta = if (page.secondaryCta != null && state == PairingState.Idle) {
            {
                TextButton(onClick = { }) {
                    Text(
                        text = page.secondaryCta.label,
                        color = OnboardingColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else null
    )
}
