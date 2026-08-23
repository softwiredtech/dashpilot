package com.softwiredtech.dashpilot.ui.tesla

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ble.TeslaClient
import com.softwiredtech.dashpilot.ble.TeslaFaultDetail
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.ble.TeslaVehicle
import com.softwiredtech.dashpilot.ble.TeslaVehicleScanner
import com.softwiredtech.dashpilot.ble.TeslaVinDecoder
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.onboarding.DevicePuck
import com.softwiredtech.dashpilot.ui.onboarding.OnboardingPageScaffold
import com.softwiredtech.dashpilot.ui.onboarding.PairingState
import com.softwiredtech.dashpilot.ui.onboarding.PrimaryCta
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors
import com.softwiredtech.dashpilot.ui.theme.TeslaCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAP_WINDOW_S = 60
private const val PROVISION_ACK_TIMEOUT_MS = 8_000L
private const val PROVISION_DISPATCH_RETRIES = 3

// A car counts as present while its advertisement was heard within this
// window; advert cycles are sub-second, so silence past it means the car
// left range. Both the scan list and the automatic VIN match expire on it.
private const val CAR_VISIBLE_WINDOW_MS = 10_000L
private const val CAR_PRUNE_TICK_MS = 500L

/**
 * Standalone enroll flow. App-driven end to end: the phone scans for the car
 * and stages it on the DashKit (ProvisionStep -> send 0x04 with VIN + MAC),
 * then watches [statusFlow]: starts enrollment (Start -> send 0x01), shows a
 * live tap-window countdown with a Cancel (0x03), renders success when a key
 * is enrolled, or a fault (with its detail) when enrollment fails. The
 * firmware never starts pairing by itself (its LEDs are not user-visible).
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

    // VIN whose staging was dispatched this session; feeds the decoded car
    // identity line ("Model 3 · 2021 · Fremont") shown on every later step so
    // the payoff of the VIN step stays visible through connect/pair/success.
    var stagedVin by rememberSaveable { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(OnboardingColors.BgBase).systemBarsPadding()) {
        when (status.linkState) {
            TeslaLinkState.NeverEnrolled, TeslaLinkState.Unknown -> ProvisionStep(
                manager = manager,
                onStaged = { stagedVin = it },
                onClose = onClose,
            )
            TeslaLinkState.Staged -> StartStep(
                identityVin = stagedVin,
                onStart = { manager?.let { TeslaClient.sendStart(it) } },
            )
            // Interim state: the app tapped Connect and the firmware accepted
            // it (DashKit connecting to the car / before the tap window
            // opens). Same spinner-onwards UX as staged, but with distinct
            // copy so the user knows the flow is moving.
            TeslaLinkState.Connecting -> ConnectingStep(
                identityVin = stagedVin,
                onCancel = { manager?.let { TeslaClient.sendCancel(it) } },
            )
            TeslaLinkState.PairingWindow -> TapCardStep(
                carReady = status.flags and 0x01 != 0,
                identityVin = stagedVin,
                onCancel = { manager?.let { TeslaClient.sendCancel(it) } },
            )
            TeslaLinkState.EnrollmentFault -> ErrorStep(
                fault = status.faultDetail,
                identityVin = stagedVin,
                onRetry = { manager?.let { TeslaClient.sendStart(it) } },
                onCancel = onClose,
            )
            TeslaLinkState.EnrolledNotConnected, TeslaLinkState.EnrolledConnected -> SuccessStep(
                identityVin = stagedVin,
                onDone = onClose,
            )
        }
    }
}

/** VIN character set: A–Z except I, O, Q, plus digits. */
private fun sanitizeVin(input: String): String = input.uppercase()
    .filter { it.isDigit() || (it in 'A'..'Z' && it != 'I' && it != 'O' && it != 'Q') }
    .take(TeslaClient.VIN_LEN)

/** 17-char VIN grouped as WMI VDS VIS, e.g. "5YJ3E7EB 1MF123456". */private fun formatVin(vin: String): String = vin.uppercase()
    .chunked(8)
    .joinToString(" ")

/** 17-char VIN grouped as WMI VDS VIS, e.g. "5YJ3E7EB 1MF123456". *//**
 * Differentiating data for a car whose advert carries no VIN (legacy format
 * "S" + SHA-1(VIN)[:16] + role). The full hash is one-way, so we surface a
 * short tag unique to that car's broadcast plus signal strength — enough to
 * tell two cars apart without showing the cryptic 18-char name.
 */
private fun identitySnippet(v: TeslaVehicle): String =
    if (TeslaVehicleScanner.isLegacyTeslaName(v.advertisedName)) {
        "ID ····" + v.advertisedName.drop(1).takeLast(4)
    } else {
        "ID ····" + v.advertisedName.takeLast(4)
    }

/** Signal-strength label: strongest = your car (closest). */
private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "▮▮▮  $rssi dBm"
    rssi >= -70 -> "▮▮   $rssi dBm"
    rssi >= -85 -> "▮    $rssi dBm"
    else -> "—    $rssi dBm"
}

private fun signalColor(rssi: Int): Color = when {
    rssi >= -70 -> OnboardingColors.Accent
    rssi >= -85 -> Color(0xFFF5A623)
    else -> DarkColors.Error
}

/**
 * VIN entry + vehicle discovery: scan nearby Tesla adverts, stage the chosen
 * car via TESLA_CMD_PROVISION. Staging is an explicit beat — decoded card +
 * "Stage this car" — gated on a live advert match and VIN trust (check digit,
 * or override).
 */
@Composable
private fun ProvisionStep(
    manager: DashKitBleManager?,
    onStaged: (String) -> Unit,
    onClose: () -> Unit,
) {
    var vin by rememberSaveable { mutableStateOf("") }
    var provisioning by remember { mutableStateOf(false) }
    var provisionError by remember { mutableStateOf(false) }
    var provisionRetry by remember { mutableIntStateOf(0) }
    var scanError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val complete = vin.length == TeslaClient.VIN_LEN
    val valid = TeslaVinDecoder.isTeslaVin(vin)

    // Failing digit ⇒ likely typo; never blocks staging, just requires the
    // explicit confirmation below (see TeslaVinDecoder.checkDigitValid).
    val checkOk = remember(valid, vin) { valid && TeslaVinDecoder.checkDigitValid(vin) }
    var confirmedBadVin by remember { mutableStateOf<String?>(null) }
    val stageConfirmed = checkOk || confirmedBadVin == vin

    // Confirm beat: staging fires only after the user taps "Stage this car"
    // for THIS vin (editing the vin re-arms the requirement).
    var stageRequestedForVin by rememberSaveable { mutableStateOf<String?>(null) }
    val stageRequested = stageRequestedForVin == vin

    // Live scan of every Tesla-format advertisement, deduped by address so the
    // list shows each car once (newest RSSI/name wins for the row), stamped
    // with when its advert was last heard. This single scan both drives the
    // list and (for the confirm beat) finds the car matching the entered VIN,
    // so we never run two BLE scans side by side.
    val sightings = remember { mutableStateOf(mapOf<String, Pair<TeslaVehicle, Long>>()) }
    LaunchedEffect(manager) {
        if (manager == null) return@LaunchedEffect
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        scanError = false
        try {
            TeslaVehicleScanner(adapter).scanNearby().collect { v ->
                val now = SystemClock.elapsedRealtime()
                sightings.value =
                    (sightings.value + (v.device.address to (v to now)))
                        .filterValues { (_, seenAt) -> now - seenAt <= CAR_VISIBLE_WINDOW_MS }
            }
        } catch (_: Exception) {
            if (!provisioning) scanError = true
        }
    }

    // Tick so cars that stop advertising age out of the list even when nothing
    // new arrives; without this a ghost row would linger until the next advert.
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CAR_PRUNE_TICK_MS)
            nowMs = SystemClock.elapsedRealtime()
        }
    }

    // Cars heard within [CAR_VISIBLE_WINDOW_MS]. Both the list and the
    // automatic VIN match use only these — a stale entry must never be shown
    // as present or provisioned against.
    val liveVehicles = sightings.value.values
        .filter { (_, seenAt) -> nowMs - seenAt <= CAR_VISIBLE_WINDOW_MS }
        .map { (v, _) -> v }
        .sortedByDescending { it.rssi }

    val automaticMatch = if (valid) {
        liveVehicles
            .filter { TeslaVehicleScanner.matchesVin(it.advertisedName, vin) }
            .maxByOrNull { it.rssi }
    } else null

    // One controlled attempt per VIN/target pair, and only once the user has
    // confirmed the decoded card (Stage this car) AND the VIN is trusted
    // (check digit passed, or explicit override). Android accepting the GATT
    // write is not firmware acknowledgement: the outer status state advances
    // only when firmware reports Staged. If that never arrives, allow retry.
    LaunchedEffect(valid, stageConfirmed, stageRequested, vin, automaticMatch?.device?.address, manager, provisionRetry) {
        if (!valid || !stageConfirmed || !stageRequested || manager == null || automaticMatch == null) return@LaunchedEffect
        provisioning = false
        provisionError = false
        repeat(PROVISION_DISPATCH_RETRIES) { attempt ->
            if (TeslaClient.sendProvision(manager, vin, automaticMatch.device.address)) {
                onStaged(vin)
                provisioning = true
                delay(PROVISION_ACK_TIMEOUT_MS)
                provisioning = false
                provisionError = true
                return@LaunchedEffect
            }
            if (attempt < PROVISION_DISPATCH_RETRIES - 1) delay(750)
        }
        provisionError = true
    }

    val decoded = remember(valid, vin) { if (valid) TeslaVinDecoder.decode(vin) else null }

    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_vin_help),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = sanitizeVin(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tesla_enroll_vin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                Spacer(Modifier.height(8.dp))

                if (complete && !valid) {
                    Text(
                        text = stringResource(R.string.tesla_enroll_vin_invalid),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Once a full VIN is entered we can decode it to make/model/
                // year/plant (the advert itself only carries an unreversible
                // hash or the last 6 VIN characters).
                if (decoded?.valid == true) {
                    Text(
                        text = stringResource(R.string.tesla_enroll_vin_decoded,
                            TeslaVinDecoder.descriptive(decoded)),
                        color = TeslaCyan,
                        fontSize = 13.sp,
                    )
                    val detail = TeslaVinDecoder.detailLine(decoded)
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            color = OnboardingColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Staging stays locked until the user accepts the failing
                // check digit for this exact VIN.
                if (complete && valid && !stageConfirmed) {
                    Text(
                        text = stringResource(R.string.tesla_enroll_check_digit_warning),
                        color = Color(0xFFF5A623),
                        fontSize = 13.sp,
                    )
                    TextButton(onClick = { confirmedBadVin = vin }) {
                        Text(stringResource(R.string.tesla_enroll_stage_anyway), color = TeslaCyan)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (liveVehicles.isEmpty()) {
                    val statusText = when {
                        scanError -> stringResource(R.string.tesla_enroll_scan_failed)
                        else -> stringResource(R.string.tesla_enroll_scanning)
                    }
                    Text(
                        text = statusText,
                        color = if (scanError) MaterialTheme.colorScheme.error else OnboardingColors.TextMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tesla_enroll_cars_found),
                        color = OnboardingColors.TextMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    liveVehicles.forEach { v ->
                        // Row identity reflects evidence strength: a matched
                        // VIN decodes fully; a modern advert leaks only its
                        // last-4..6 tail; a legacy advert carries no VIN at
                        // all (one-way hash) — signal + broadcast tag instead.
                        val thisVinOk = valid && TeslaVehicleScanner.matchesVin(v.advertisedName, vin)
                        val modernTail = v.advertisedName.removePrefix("Tesla ")
                        val label = when {
                            thisVinOk -> TeslaVinDecoder.descriptive(decoded!!)
                            v.advertisedName.startsWith("Tesla ") ->
                                "Tesla · VIN ······" + modernTail.takeLast(6)
                            else -> stringResource(R.string.tesla_enroll_car_unknown)
                        }
                        // Secondary line: VIN evidence where the advert
                        // carries it, else signal + broadcast tag.
                        val vinText = when {
                            thisVinOk -> formatVin(vin)
                            modernTail.length in 4..6 ->
                                stringResource(R.string.tesla_enroll_vin_tail, modernTail.takeLast(6))
                            else -> identitySnippet(v)
                        }
                        val isMatch = thisVinOk
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isMatch) DarkColors.SurfaceSelected
                                    else OnboardingColors.Surface
                                )
                                .border(
                                    width = if (isMatch) 1.dp else 0.dp,
                                    color = if (isMatch) TeslaCyan else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    color = if (thisVinOk) TeslaCyan else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isMatch) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = vinText,
                                    color = DarkColors.TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Make/model detail once the VIN is confirmed:
                                // body · battery · drive, straight from the VDS.
                                if (thisVinOk) {
                                    val detail = TeslaVinDecoder.detailLine(decoded!!)
                                    if (detail.isNotEmpty()) {
                                        Text(
                                            text = detail,
                                            color = OnboardingColors.TextMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                if (isMatch) {
                                    // Evidence-strength matters here: a modern
                                    // advert only proves the last 4–6 VIN
                                    // characters, while a legacy advert matches
                                    // a 64-bit SHA-1 prefix of the whole VIN.
                                    Text(
                                        text =
                                            if (v.advertisedName.startsWith("Tesla ")) {
                                                stringResource(R.string.tesla_enroll_tail_matches)
                                            } else {
                                                stringResource(R.string.tesla_enroll_match_check)
                                            },
                                        color = TeslaCyan,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = signalLabel(v.rssi),
                                    color = signalColor(v.rssi),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (!isMatch) Spacer(Modifier.height(4.dp))
                    }
                }

                if (provisioning) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tesla_enroll_found),
                        color = OnboardingColors.TextMuted,
                        fontSize = 13.sp,
                    )
                } else if (provisionError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tesla_enroll_provision_failed),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                    TextButton(onClick = { provisionRetry++ }) {
                        Text(stringResource(R.string.tesla_enroll_retry), color = TeslaCyan)
                    }
                }
            }
        },
        cta = {
            Column(Modifier.fillMaxWidth()) {
                // The confirm beat: enabled only once a live advert match
                // exists and the VIN is trusted, so the tap is always an
                // informed commitment.
                PrimaryCta(
                    label = stringResource(R.string.tesla_enroll_stage_cta),
                    onClick = {
                        stageRequestedForVin = vin
                        onStaged(vin)
                    },
                    enabled = valid && stageConfirmed && !provisioning && automaticMatch != null,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tesla_enroll_cancel), color = OnboardingColors.TextSecondary)
                }
            }
        },
    )
}

@Composable
private fun StartStep(identityVin: String?, onStart: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_explain_body),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                CarIdentityLine(identityVin)
                RoleChip(stringResource(R.string.tesla_enroll_role_chip))
            }
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
private fun ConnectingStep(identityVin: String?, onCancel: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_title),
        subtitle = stringResource(R.string.tesla_enroll_connecting_body),
        hero = { DevicePuck(state = PairingState.Searching, accent = TeslaCyan) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                CarIdentityLine(identityVin)
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
private fun TapCardStep(carReady: Boolean, identityVin: String?, onCancel: () -> Unit) {
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
                CarIdentityLine(identityVin)
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
private fun SuccessStep(identityVin: String?, onDone: () -> Unit) {
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_success_title),
        subtitle = stringResource(R.string.tesla_enroll_success_body),
        hero = { DevicePuck(state = PairingState.Paired) },
        extra = {
            Column(Modifier.fillMaxWidth()) {
                CarIdentityLine(identityVin)
                RoleChip(stringResource(R.string.tesla_enroll_success_role))
            }
        },
        cta = { PrimaryCta(label = stringResource(R.string.tesla_enroll_done), onClick = onDone) },
    )
}

@Composable
private fun ErrorStep(fault: TeslaFaultDetail, identityVin: String?, onRetry: () -> Unit, onCancel: () -> Unit) {
    val message = when (fault) {
        TeslaFaultDetail.TapTimeout -> stringResource(R.string.tesla_fault_tap_timeout)
        TeslaFaultDetail.Rejected -> stringResource(R.string.tesla_fault_rejected)
        TeslaFaultDetail.Protocol -> stringResource(R.string.tesla_fault_protocol)
        TeslaFaultDetail.Persist -> stringResource(R.string.tesla_fault_persist)
        TeslaFaultDetail.None -> stringResource(R.string.tesla_fault_generic)
    }
    InlineScaffold(
        title = stringResource(R.string.tesla_enroll_error_title),
        subtitle = message,
        hero = { DevicePuck(state = PairingState.Idle, accent = OnboardingColors.LedDim) },
        extra = { CarIdentityLine(identityVin) },
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

/**
 * Muted one-line identity of the staged car ("Model 3 · 2021 · Fremont, CA"),
 * carried through the post-provision steps so the payoff of the VIN step stays
 * on screen for the whole flow instead of flashing once during hand-off.
 * Renders nothing until a decodable VIN exists.
 */
@Composable
private fun CarIdentityLine(vin: String?) {
    val decoded = remember(vin) {
        vin?.takeIf { TeslaVinDecoder.isTeslaVin(it) }?.let { TeslaVinDecoder.decode(it) }
    } ?: return
    val line = listOfNotNull(
        decoded.model,
        decoded.modelYear?.toString(),
        decoded.plant,
    ).joinToString(" · ")
    Text(
        text = line,
        color = OnboardingColors.TextMuted,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

/** Thin wrapper over the onboarding scaffold with consistent hero sizing + a spinner-capable CTA. */
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
