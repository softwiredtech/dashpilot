package com.softwiredtech.dashpilot.ui.tesla

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
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

@Composable
fun TeslaEnrollFlow(
    manager: DashKitBleManager?,
    statusFlow: StateFlow<TeslaStatus>?,
    vinState: StateFlow<String?>,
    onClose: () -> Unit,
) {
    val idleStatus = remember { MutableStateFlow(TeslaStatus.Idle) }
    val status = statusFlow ?: idleStatus
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val controller = remember(manager, status, vinState) {
        TeslaEnrollmentController.create(scope, context, manager, vinState, status)
    }
    LaunchedEffect(controller) { controller.begin() }
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    BackHandler(onBack = onClose)

    val state by controller.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(OnboardingColors.BgBase).systemBarsPadding()) {
        TeslaEnrollmentContent(
            state = state,
            vinState = vinState,
            onConnect = controller::connect,
            onRetry = controller::retry,
            onCancelPairing = controller::cancelPairingWindow,
            onClose = onClose,
        )
    }
}

@Composable
fun TeslaEnrollmentContent(
    state: TeslaEnrollmentState,
    vinState: StateFlow<String?>,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    onCancelPairing: () -> Unit,
    onClose: () -> Unit,
) {
    when (val s = state) {
        TeslaEnrollmentState.CheckingFirmware -> ProgressStep(
            subtitle = stringResource(R.string.tesla_enroll_checking),
            onCancel = onClose,
        )
        TeslaEnrollmentState.WaitingForVin -> ProgressStep(
            subtitle = stringResource(R.string.tesla_enroll_reading_vin),
            onCancel = onClose,
            footer = { MaskedVinLine(vinState) },
        )
        is TeslaEnrollmentState.FindingVehicle -> ProgressStep(
            subtitle = stringResource(R.string.tesla_enroll_finding),
            onCancel = onClose,
            footer = { MaskedTextLine(s.maskedVin) },
        )
        is TeslaEnrollmentState.Provisioning -> ProgressStep(
            subtitle = stringResource(R.string.tesla_enroll_found),
            onCancel = onClose,
            footer = { MaskedTextLine(s.maskedVin) },
        )
        is TeslaEnrollmentState.ReadyToConnect -> ReadyStep(
            maskedVin = s.maskedVin,
            vinState = vinState,
            onConnect = onConnect,
        )
        is TeslaEnrollmentState.Connecting -> ConnectingStep(
            maskedVin = s.maskedVin,
            vinState = vinState,
            onCancel = onCancelPairing,
        )
        is TeslaEnrollmentState.WaitingForKeyCard -> TapCardStep(
            carReady = s.carReady,
            maskedVin = s.maskedVin,
            vinState = vinState,
            onCancel = onCancelPairing,
        )
        TeslaEnrollmentState.Success -> SuccessStep(
            vinState = vinState,
            onDone = onClose,
        )
        is TeslaEnrollmentState.Error -> ErrorStep(
            reason = s.reason,
            vinState = vinState,
            onRetry = onRetry,
            onCancel = onClose,
        )
    }
}

@Composable
private fun ProgressStep(
    subtitle: String,
    onCancel: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = subtitle,
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Column(Modifier.fillMaxWidth()) { footer() }
        },
        cta = {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tesla_enroll_cancel), color = OnboardingColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun ReadyStep(
    maskedVin: String?,
    vinState: StateFlow<String?>,
    onConnect: () -> Unit,
) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_explain_body),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                if (maskedVin != null) MaskedTextLine(maskedVin) else MaskedVinLine(vinState)
                RoleChip(stringResource(R.string.tesla_enroll_role_chip))
            }
        },
        cta = {
            Column(Modifier.fillMaxWidth()) {
                PrimaryCta(label = stringResource(R.string.tesla_enroll_start), onClick = onConnect)
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
private fun ConnectingStep(maskedVin: String?, vinState: StateFlow<String?>, onCancel: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_connecting_body),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                if (maskedVin != null) MaskedTextLine(maskedVin) else MaskedVinLine(vinState)
                RoleChip(stringResource(R.string.tesla_enroll_connecting_hint))
            }
        },
        cta = {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tesla_enroll_cancel), color = OnboardingColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun TapCardStep(
    carReady: Boolean,
    maskedVin: String?,
    vinState: StateFlow<String?>,
    onCancel: () -> Unit,
) {
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
            Column(Modifier.fillMaxWidth()) {
                if (maskedVin != null) MaskedTextLine(maskedVin) else MaskedVinLine(vinState)
                if (carReady) {
                    Text(
                        text = stringResource(R.string.tesla_enroll_tap_ready, remaining),
                        color = TeslaCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tesla_enroll_tap_hint, remaining),
                        color = OnboardingColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        cta = {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tesla_enroll_cancel), color = OnboardingColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun SuccessStep(vinState: StateFlow<String?>, onDone: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_success_title),
        subtitle = stringResource(R.string.tesla_enroll_success_body),
        hero = { DevicePuck(state = PairingState.Paired) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                MaskedVinLine(vinState)
                RoleChip(stringResource(R.string.tesla_enroll_success_role))
            }
        },
        cta = { PrimaryCta(label = stringResource(R.string.tesla_enroll_done), onClick = onDone) },
    )
}

@Composable
private fun ErrorStep(
    reason: TeslaEnrollmentErrorReason,
    vinState: StateFlow<String?>,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val message = when (reason) {
        TeslaEnrollmentErrorReason.FirmwareUnsupported ->
            stringResource(R.string.tesla_enroll_firmware_update_needed)
        TeslaEnrollmentErrorReason.VinUnavailable ->
            stringResource(R.string.tesla_enroll_vin_timeout)
        TeslaEnrollmentErrorReason.VehicleNotFound ->
            stringResource(R.string.tesla_enroll_scan_timeout)
        TeslaEnrollmentErrorReason.NotAcknowledged ->
            stringResource(R.string.tesla_enroll_provision_failed)
        TeslaEnrollmentErrorReason.TapTimeout -> stringResource(R.string.tesla_fault_tap_timeout)
        TeslaEnrollmentErrorReason.Rejected -> stringResource(R.string.tesla_fault_rejected)
        TeslaEnrollmentErrorReason.Protocol -> stringResource(R.string.tesla_fault_protocol)
        TeslaEnrollmentErrorReason.Persist -> stringResource(R.string.tesla_fault_persist)
        TeslaEnrollmentErrorReason.Generic -> stringResource(R.string.tesla_fault_generic)
    }
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_error_title),
        subtitle = message,
        hero = { DevicePuck(state = PairingState.Idle, accent = OnboardingColors.LedDim) },
        extra = { MaskedVinLine(vinState) },
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

@Composable
private fun MaskedVinLine(vinState: StateFlow<String?>) {
    val vin by vinState.collectAsState()
    MaskedTextLine(maskVinTail(vin))
}

@Composable
private fun MaskedTextLine(maskedTail: String?) {
    if (maskedTail == null) return
    Text(
        text = stringResource(R.string.tesla_enroll_masked_vin, maskedTail),
        color = OnboardingColors.TextMuted,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

@Composable
private fun InlineScaffold(
    title: String,
    subtitle: String,
    cta: @Composable () -> Unit,
    hero: @Composable () -> Unit,
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

/** Builds the one-line Home-tile summary, e.g. "Present · Unlocked · Awake". Always
 * shows the presence/lock/sleep state so the driver can see live values — including
 * the "negative" states (not present / unlocked / awake); unknown (0xFF) is omitted. */
@Composable
fun teslaTileSummary(status: TeslaStatus): String {
    if (status.linkState != TeslaLinkState.EnrolledConnected &&
        status.linkState != TeslaLinkState.EnrolledNotConnected
    ) {
        return ""
    }
    val parts = buildList {
        add(
            when (status.presence) {
                1 -> stringResource(R.string.tesla_status_present)
                0 -> stringResource(R.string.tesla_status_not_present)
                else -> null
            }
        )
        add(
            when (status.lock) {
                1 -> stringResource(R.string.tesla_status_locked)
                0 -> stringResource(R.string.tesla_status_unlocked)
                else -> null
            }
        )
        add(
            when (status.sleep) {
                1 -> stringResource(R.string.tesla_status_asleep)
                0 -> stringResource(R.string.tesla_status_awake)
                else -> null
            }
        )
    }.filterNotNull()
    return parts.joinToString(" · ")
}
