package com.softwiredtech.dashpilot.ui.tesla

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ble.TeslaClient
import com.softwiredtech.dashpilot.ble.TeslaFaultDetail
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.onboarding.DevicePuck
import com.softwiredtech.dashpilot.ui.onboarding.OnboardingPageScaffold
import com.softwiredtech.dashpilot.ui.onboarding.PairingState
import com.softwiredtech.dashpilot.ui.onboarding.PrimaryCta
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors
import com.softwiredtech.dashpilot.ui.theme.TeslaCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAP_WINDOW_S = 60

/**
 * Standalone Phase 4 enroll flow. App-triggered only: the firmware never starts
 * pairing by itself (the DashKit's LEDs are not user-visible). The app watches
 * [statusFlow] and: starts enrollment (Start -> send 0x01), shows a live
 * tap-window countdown with a Cancel (0x03), renders success when a key is
 * enrolled, or a fault (with its detail) when enrollment fails.
 */
@Composable
fun TeslaEnrollFlow(
    manager: DashKitBleManager?,
    statusFlow: StateFlow<TeslaStatus>?,
    onClose: () -> Unit,
) {
    val idleFlow = remember { MutableStateFlow(TeslaStatus.Idle) }
    val status by (statusFlow ?: idleFlow).collectAsState()
    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(OnboardingColors.BgBase).systemBarsPadding()) {
        when (status.linkState) {
            TeslaLinkState.NeverEnrolled, TeslaLinkState.Unknown -> LookingStep()
            TeslaLinkState.Staged -> StartStep(onStart = { manager?.let { TeslaClient.sendStart(it) } })
            TeslaLinkState.PairingWindow -> TapCardStep(onCancel = { manager?.let { TeslaClient.sendCancel(it) } })
            TeslaLinkState.EnrollmentFault -> ErrorStep(
                fault = status.faultDetail,
                onRetry = { manager?.let { TeslaClient.sendStart(it) } },
                onCancel = onClose,
            )
            TeslaLinkState.EnrolledNotConnected, TeslaLinkState.EnrolledConnected -> SuccessStep(onDone = onClose)
        }
    }
}

@Composable
private fun LookingStep() {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_looking),
        hero = { DevicePuck(state = PairingState.Idle, accent = TeslaCyan) },
        cta = { Spacer(Modifier.height(8.dp)) },
    )
}

@Composable
private fun StartStep(onStart: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_explain_body),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            RoleChip(stringResource(R.string.tesla_enroll_role_chip))
        },
        cta = {
            Column(Modifier.fillMaxWidth()) {
                PrimaryCta(label = stringResource(R.string.tesla_enroll_start), onClick = onStart)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tesla_enroll_need_card),
                    color = OnboardingColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
        },
    )
}

@Composable
private fun TapCardStep(onCancel: () -> Unit) {
    var remaining by remember { mutableIntStateOf(TAP_WINDOW_S) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_tap_title),
        subtitle = stringResource(R.string.tesla_enroll_tap_body),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Text(
                text = stringResource(R.string.tesla_enroll_tap_hint, remaining),
                color = TeslaCyan,
                fontSize = 13.sp,
            )
        },
        cta = {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tesla_enroll_cancel), color = OnboardingColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun SuccessStep(onDone: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_success_title),
        subtitle = stringResource(R.string.tesla_enroll_success_body),
        hero = { DevicePuck(state = PairingState.Paired) },
        extra = { RoleChip(stringResource(R.string.tesla_enroll_success_role)) },
        cta = { PrimaryCta(label = stringResource(R.string.tesla_enroll_done), onClick = onDone) },
    )
}

@Composable
private fun ErrorStep(fault: TeslaFaultDetail, onRetry: () -> Unit, onCancel: () -> Unit) {
    val message = when (fault) {
        TeslaFaultDetail.TapTimeout -> stringResource(R.string.tesla_fault_tap_timeout)
        TeslaFaultDetail.Rejected -> stringResource(R.string.tesla_fault_rejected)
        TeslaFaultDetail.Protocol -> stringResource(R.string.tesla_fault_protocol)
        TeslaFaultDetail.Persist -> stringResource(R.string.tesla_fault_persist)
        TeslaFaultDetail.Link -> stringResource(R.string.tesla_fault_link)
        TeslaFaultDetail.None -> stringResource(R.string.tesla_fault_generic)
    }
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_error_title),
        subtitle = message,
        hero = { DevicePuck(state = PairingState.Idle, accent = OnboardingColors.LedDim) },
        cta = {
            Column(Modifier.fillMaxWidth()) {
                PrimaryCta(label = stringResource(R.string.tesla_enroll_retry), onClick = onRetry)
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tesla_enroll_cancel), color = OnboardingColors.TextSecondary)
                }
            }
        },
    )
}

/** Thin wrapper over the onboarding scaffold with consistent hero sizing + a spinner-capable CTA. */
@Composable
private fun InlineScaffold(
    title: String,
    subtitle: String,
    cta: @Composable () -> Unit,
    hero: @Composable () -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    OnboardingPageScaffold(
        title = title,
        subtitle = subtitle,
        cta = cta,
        hero = {
            Box(contentAlignment = Alignment.Center) {
                hero()
            }
        },
        extra = extra,
    )
}

@Composable
private fun RoleChip(text: String) {
    Box(
        modifier = Modifier
            .background(OnboardingColors.Surface, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = OnboardingColors.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

/** Builds the one-line Home-tile summary, e.g. "Present · Locked · Asleep". */
@Composable
fun teslaTileSummary(status: TeslaStatus): String {
    if (status.linkState != TeslaLinkState.EnrolledConnected &&
        status.linkState != TeslaLinkState.EnrolledNotConnected
    ) {
        return ""
    }
    val parts = buildList {
        if (status.presence == 1) add(stringResource(R.string.tesla_status_present))
        if (status.lock == 1) add(stringResource(R.string.tesla_status_locked))
        if (status.sleep == 1) add(stringResource(R.string.tesla_status_asleep))
    }
    return parts.joinToString(" · ")
}
