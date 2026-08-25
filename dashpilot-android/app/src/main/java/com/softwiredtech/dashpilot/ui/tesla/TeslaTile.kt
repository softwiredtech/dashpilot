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

internal fun teslaTileTextRes(status: TeslaStatus, resetPending: Boolean): Int? = when {
    resetPending -> R.string.tesla_connection_removing
    status.linkState == TeslaLinkState.NeverEnrolled -> R.string.tesla_tile_connect
    status.linkState == TeslaLinkState.Staged -> R.string.tesla_tile_staged
    status.linkState == TeslaLinkState.Connecting -> R.string.tesla_enroll_connecting_body
    status.linkState == TeslaLinkState.EnrolledNotConnected -> R.string.tesla_tile_not_connected
    status.linkState == TeslaLinkState.EnrolledConnected -> null
    status.linkState == TeslaLinkState.PairingWindow -> R.string.tesla_enroll_tap_title
    status.linkState == TeslaLinkState.EnrollmentFault -> R.string.tesla_tile_fault
    else -> R.string.tesla_tile_not_connected
}

@Composable
fun TeslaTile(status: TeslaStatus, resetPending: Boolean, onEnroll: () -> Unit) {
    val tapEnabled = !resetPending && (
        status.linkState == TeslaLinkState.Staged ||
            status.linkState == TeslaLinkState.NeverEnrolled ||
            status.linkState == TeslaLinkState.EnrollmentFault
        )

    val textRes = teslaTileTextRes(status, resetPending)
    val text = textRes?.let { stringResource(it) } ?: teslaTileSummary(status)
        .ifBlank { stringResource(R.string.tesla_tile_connected) }

    val ledColor: Color = when {
        resetPending -> DarkColors.Disabled
        status.linkState == TeslaLinkState.EnrolledConnected -> OnboardingColors.Accent
        status.linkState == TeslaLinkState.NeverEnrolled -> TeslaCyan
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
