package com.softwiredtech.dashpilot.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.OverlayScrimAlpha
import com.softwiredtech.dashpilot.ui.theme.OverlayScrimBase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val OVERLAY_TIMEOUT_MS = 4_000L
private val OVERLAY_ICON_BUTTON_SIZE = 64.dp
private val OVERLAY_SETTINGS_ICON_SIZE = 36.dp
private val OVERLAY_BATTERY_ICON_SIZE = 34.dp
private val OVERLAY_PADDING = 16.dp
private val OVERLAY_TEXT_SPACER = 6.dp
private val SHADOW_OFFSET = 2.dp

@Composable
fun DashboardScreen(
    dashboardType: String,
    dashboardUrl: String,
    dashStateFlow: Flow<DashState>,
    onSettingsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dashState by dashStateFlow.collectAsStateWithLifecycle(DashState())
    val phoneBattery = dashState.phoneBattery
    val showPhoneBattery = dashState.displaySettings.showPhoneBattery
    var overlayVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val overlayScrimColor = OverlayScrimBase.copy(alpha = OverlayScrimAlpha)
    val showOverlay = {
        overlayVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }
    val onTap = {
        if (overlayVisible) {
            hideJob?.cancel()
            overlayVisible = false
        } else {
            showOverlay()
        }
    }
    val shadowOffsetPx = with(LocalDensity.current) { SHADOW_OFFSET.toPx() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!isLandscape) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "↺",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_rotate_prompt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (dashboardType) {
            "web" -> {
                WebDashView(
                    modifier = Modifier.fillMaxSize(),
                    url = dashboardUrl,
                    scope = scope,
                    dashStateFlow = dashStateFlow,
                    onTap = onTap
                )
            }

            "rive", "dev_rive" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(overlayVisible) {
                            detectTapGestures { onTap() }
                        }
                ) {
                    RiveDashView(
                        assetName = if (dashboardType == "dev_rive") "" else dashboardUrl,
                        dashStateFlow = dashStateFlow,
                        scope = scope,
                        onError = { message -> Log.d("Rive", message) },
                        fileUri = if (dashboardType == "dev_rive") dashboardUrl else null
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayScrimColor)
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(OVERLAY_PADDING)
                        .size(OVERLAY_ICON_BUTTON_SIZE)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier
                                .size(OVERLAY_SETTINGS_ICON_SIZE)
                                .offset(x = SHADOW_OFFSET, y = SHADOW_OFFSET)
                                .blur(radius = 4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = AccentColor,
                            modifier = Modifier.size(OVERLAY_SETTINGS_ICON_SIZE)
                        )
                    }
                }

                if (showPhoneBattery && phoneBattery >= 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = OVERLAY_PADDING, top = OVERLAY_PADDING)
                            .height(OVERLAY_ICON_BUTTON_SIZE),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(OVERLAY_BATTERY_ICON_SIZE)
                                .offset(x = SHADOW_OFFSET, y = SHADOW_OFFSET)
                                .blur(radius = 4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            )
                            Icon(
                                imageVector = Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(OVERLAY_BATTERY_ICON_SIZE)
                            )
                        }
                        Spacer(modifier = Modifier.width(OVERLAY_TEXT_SPACER))
                        Text(
                            text = "$phoneBattery%",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(
                                shadow = Shadow(color = Color.Black, offset = Offset(shadowOffsetPx, shadowOffsetPx))
                            )
                        )
                    }
                }
            }
        }
    }
}
