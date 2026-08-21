package com.softwiredtech.dashpilot.ui.tesla

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors
import com.softwiredtech.dashpilot.ui.theme.TeslaCyan

/**
 * One-line Tesla tile on Home. App-triggered only: "Key not set up" (0x00) and
 * "Car found — Start enrollment?" (0x05 / staged) are tappable and open the
 * enroll flow; connected (0x02) and not-connected (0x01) are informational.
 * The LED dot is an in-app motif — the physical DashKit LED is buried in the
 * car trim and not user-visible.
 */
@Composable
fun TeslaTile(status: TeslaStatus, onEnroll: () -> Unit) {
    val tapEnabled = status.linkState == TeslaLinkState.Staged ||
        status.linkState == TeslaLinkState.NeverEnrolled ||
        status.linkState == TeslaLinkState.EnrollmentFault

    val text = when (status.linkState) {
        TeslaLinkState.NeverEnrolled -> stringResource(R.string.tesla_tile_key_not_set_up)
        TeslaLinkState.Staged -> stringResource(R.string.tesla_tile_staged)
        TeslaLinkState.EnrolledNotConnected -> stringResource(R.string.tesla_tile_not_connected)
        TeslaLinkState.EnrolledConnected -> teslaTileSummary(status)
            .ifBlank { stringResource(R.string.tesla_tile_connected) }
        TeslaLinkState.PairingWindow -> stringResource(R.string.tesla_enroll_tap_title)
        TeslaLinkState.EnrollmentFault -> stringResource(R.string.tesla_tile_fault)
        else -> stringResource(R.string.tesla_tile_not_connected)
    }

    val ledColor: Color = when {
        status.linkState == TeslaLinkState.EnrolledConnected -> OnboardingColors.Accent
        status.linkState == TeslaLinkState.Staged -> TeslaCyan
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
