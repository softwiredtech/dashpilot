package com.softwiredtech.dashpilot.ui.tesla

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.ble.TeslaVehicleScanner
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors
import com.softwiredtech.dashpilot.ui.theme.TeslaCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-line Tesla tile on Home. App-triggered only: "Key not set up" (0x00) and
 * "Car found — Start enrollment?" (0x05 / staged) are tappable and open the
 * enroll flow; connected (0x02) and not-connected (0x01) are informational.
 * The LED dot is an in-app motif — the physical DashKit LED is buried in the
 * car trim and not user-visible.
 */
@Composable
fun TeslaTile(status: TeslaStatus, onEnroll: () -> Unit, carsDetected: Boolean = false) {
    val tapEnabled = status.linkState == TeslaLinkState.Staged ||
        status.linkState == TeslaLinkState.NeverEnrolled ||
        status.linkState == TeslaLinkState.EnrollmentFault

    val text = when {
        // No key yet but the phone has recently heard a Tesla broadcasting
        // nearby: that is the "ready to pair" state, so surface it in cyan
        // instead of a grey "Not connected". (Recent-past, not live presence —
        // see rememberTeslaCarsDetected.)
        status.linkState == TeslaLinkState.NeverEnrolled && carsDetected ->
            stringResource(R.string.tesla_tile_car_found)
        status.linkState == TeslaLinkState.NeverEnrolled ->
            stringResource(R.string.tesla_tile_key_not_set_up)
        status.linkState == TeslaLinkState.Staged -> stringResource(R.string.tesla_tile_staged)
        status.linkState == TeslaLinkState.Connecting -> stringResource(R.string.tesla_enroll_connecting_body)
        status.linkState == TeslaLinkState.EnrolledNotConnected -> stringResource(R.string.tesla_tile_not_connected)
        status.linkState == TeslaLinkState.EnrolledConnected -> teslaTileSummary(status)
            .ifBlank { stringResource(R.string.tesla_tile_connected) }
        status.linkState == TeslaLinkState.PairingWindow -> stringResource(R.string.tesla_enroll_tap_title)
        status.linkState == TeslaLinkState.EnrollmentFault -> stringResource(R.string.tesla_tile_fault)
        else -> stringResource(R.string.tesla_tile_not_connected)
    }

    val ledColor: Color = when {
        status.linkState == TeslaLinkState.EnrolledConnected -> OnboardingColors.Accent
        status.linkState == TeslaLinkState.Staged -> TeslaCyan
        status.linkState == TeslaLinkState.NeverEnrolled && carsDetected -> TeslaCyan
        status.linkState == TeslaLinkState.PairingWindow -> TeslaCyan
        else -> DarkColors.Disabled
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkColors.Surface)
            .then(if (tapEnabled) Modifier.clickable(onClick = onEnroll) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(ledColor),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.tesla_label),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(12.dp))
        // Status is right-justified toward the chevron (with breathing room) and
        // single-line so the three statuses never word-wrap; ellipsizes if long.
        Text(
            text = text,
            color = DarkColors.TextMuted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (tapEnabled) {
            Spacer(Modifier.width(16.dp))
            Text(text = "›", color = DarkColors.TextMuted, fontSize = 20.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * True once a Tesla-format advertisement has been heard recently and the
 * DashKit has no key yet. The firmware has no BLE observer, so the PHONE does
 * discovery; this runs a short scan (respecting the Bluetooth-on + permission
 * state) so the Home tile can light up cyan "Car seen nearby" without waiting
 * for the user to open the enroll flow. Returns false for any connected/staged
 * state — there the firmware already knows the car.
 *
 * The result is intentionally a recent-past signal, not live presence: it
 * latches on the first sighting within a scan window and does not keep
 * scanning afterwards (repeated LOW_LATENCY scans would burn battery and trip
 * Android's scan throttling). Callers must word their UI accordingly.
 */
@android.annotation.SuppressLint("MissingPermission") // BLUETOOTH_SCAN requested at runtime with the other BLE permissions
@Composable
fun rememberTeslaCarsDetected(
    teslaStatus: StateFlow<TeslaStatus>?,
    scanEnabled: Boolean = true,
): Boolean {
    val fallback = remember { MutableStateFlow(TeslaStatus.Idle) }
    val status by (teslaStatus ?: fallback).collectAsState()
    var detected by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val needsDiscovery = scanEnabled &&
        (status.linkState == TeslaLinkState.NeverEnrolled ||
            status.linkState == TeslaLinkState.EnrollmentFault)

    LaunchedEffect(needsDiscovery) {
        detected = false
        if (!needsDiscovery) return@LaunchedEffect
        val adapter = runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }.getOrNull() ?: return@LaunchedEffect
        // Very short window: enough to catch a parked car's ~100ms advert
        // cycle, cheap enough to run while the Home screen is up.
        withTimeoutOrNull(8_000) {
            try {
                TeslaVehicleScanner(adapter).scanNearby().first {
                    detected = true
                    true
                }
            } catch (_: Exception) {
                detected = false
            }
        }
    }
    return detected
}
