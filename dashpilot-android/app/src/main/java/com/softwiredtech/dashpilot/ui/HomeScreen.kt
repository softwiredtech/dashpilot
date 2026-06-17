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
    ) {
        // In debug builds always show the connected home so the layout can be
        // inspected without an active data source (widgets show placeholders).
        if (connectionStatus is ConnectionStatus.Connected || BuildConfig.DEBUG) {
            ConnectedHomeContent(
                dashState = dashState,
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
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by (dashState ?: flowOf(DashState())).collectAsState(initial = DashState())
    val car = state.carState
    val useImperial = state.displaySettings.useImperial

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = DarkColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
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
                value = tempText(car.packTMax),
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
                // TODO: wire real climate setpoint from CarState once available.
                value = "21°",
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
    if (car.fullPackEnergy <= 0f) return "—"
    val soc = (car.nominalEnergyRemaining / car.fullPackEnergy * 100f).roundToInt()
    return "$soc%"
}

private fun tempText(temp: Float): String {
    if (temp == 0f) return "—"
    return "${temp.roundToInt()}°"
}

private fun odometerText(odometer: Float, useImperial: Boolean): String {
    if (odometer <= 0f) return "—"
    val unit = if (useImperial) "mi" else "km"
    return "${odometer.roundToInt()} $unit"
}
