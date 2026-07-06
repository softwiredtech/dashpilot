package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.controls.ControlActionButton
import com.softwiredtech.dashpilot.ui.controls.controlById
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

/**
 * Landing screen. While disconnected/connecting it hosts the data-source setup
 * flow; once connected it shows a grid of vehicle info widgets plus the primary
 * actions (Automations, Controls, Drive).
 */
@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    bleManager: DashKitBleManager?,
    dashState: Flow<DashState>?,
    preselectDashKit: Boolean,
    pinnedControlId: String?,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onNext: () -> Unit,
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
            .systemBarsPadding()
    ) {
        // In debug builds show the connected home content by default so the full
        // UI can be exercised without a live DashKit connection.
        if (connectionStatus is ConnectionStatus.Connected || BuildConfig.DEBUG) {
            ConnectedHomeContent(
                dashState = dashState,
                bleManager = bleManager,
                connectionStatus = connectionStatus,
                pinnedControlId = pinnedControlId,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onAutomations = onAutomations,
                onControls = onControls,
                onDrive = onDrive,
                onSettingsClick = onSettingsClick
            )
        } else {
            SetupScreen(
                connectionStatus = connectionStatus,
                preselectDashKit = preselectDashKit,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onNext = onNext,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun ConnectedHomeContent(
    dashState: Flow<DashState>?,
    bleManager: DashKitBleManager?,
    connectionStatus: ConnectionStatus,
    pinnedControlId: String?,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val fallback = DashState()
    val state by (dashState ?: flowOf(fallback)).collectAsState(initial = fallback)
    val car = state.carState
    val useImperial = state.displaySettings.useImperial

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = DarkColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "DashPilot",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2x2 widget grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoWidget(
                icon = Icons.Rounded.BatteryChargingFull,
                label = "Battery",
                value = socText(car),
                modifier = Modifier.weight(1f)
            )
            InfoWidget(
                icon = Icons.Rounded.DeviceThermostat,
                label = "Battery Temp",
                value = batteryTempText(car),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoWidget(
                icon = Icons.Rounded.AcUnit,
                label = "AC Temp",
                value = tempText(car.acTemp),
                modifier = Modifier.weight(1f)
            )
            InfoWidget(
                icon = Icons.Rounded.Speed,
                label = "Odometer",
                value = odometerText(car.odometer, useImperial),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        controlById(pinnedControlId)?.let { action ->
            Text(
                text = "Pinned",
                color = DarkColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ControlActionButton(
                action = action,
                pinned = true,
                enabled = bleManager != null,
                onClick = { action.perform(bleManager) },
                onLongClick = null
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        ActionButton(
            label = "Automations",
            icon = Icons.Rounded.AutoAwesome,
            accent = false,
            onClick = onAutomations
        )
        Spacer(modifier = Modifier.height(12.dp))
        ActionButton(
            label = "Controls",
            icon = Icons.Rounded.Tune,
            accent = false,
            onClick = onControls
        )
        Spacer(modifier = Modifier.height(12.dp))
        ActionButton(
            label = "Drive",
            icon = Icons.Rounded.DirectionsCar,
            accent = true,
            onClick = onDrive
        )
        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                DebugDataSourceMenu(connectionStatus, onConnect, onDisconnect)
            }
        }
    }
}

@Composable
private fun InfoWidget(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .aspectRatio(1.4f)
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentColor,
            modifier = Modifier.size(28.dp)
        )
        Column {
            Text(
                text = value,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = DarkColors.TextMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    accent: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) AccentColor else DarkColors.Surface,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun socText(car: CarState): String {
    val fullPack = car.fullPackEnergy
    val remaining = car.nominalEnergyRemaining
    val buffer = car.energyBuffer
    if (fullPack <= 0f || remaining <= 0f || (fullPack - buffer) <= 0f) return "—"
    val soc = ((remaining - buffer) / (fullPack - buffer) * 100f).roundToInt()
    return "$soc%"
}

private fun tempText(temp: Float): String {
    if (temp == 0f) return "—"
    return "${temp.roundToInt()}°"
}

private fun batteryTempText(car: CarState): String {
    val tMin = car.packTMin
    val tMax = car.packTMax
    if (tMin == 0f && tMax == 0f) return "—"
    val avg = (tMin + tMax) / 2f
    return "${avg.roundToInt()}°"
}

private fun odometerText(odometer: Float, useImperial: Boolean): String {
    if (odometer <= 0f) return "—"
    val unit = if (useImperial) "mi" else "km"
    return "${odometer.roundToInt()} $unit"
}
