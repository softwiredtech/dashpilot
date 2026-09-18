package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.ui.controls.ControlAction
import com.softwiredtech.dashpilot.ui.controls.ControlActionButton
import com.softwiredtech.dashpilot.ui.controls.controlById
import com.softwiredtech.dashpilot.ui.tesla.TeslaTile
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.softwiredtech.dashpilot.R

/**
 * Landing screen. DashKit is the default source and shows the connected home
 * content (vehicle info widgets + primary actions) regardless of connection
 * state. Selecting any other source switches to the data-source setup flow.
 */
@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    activeSourceType: String?,
    bleManager: DashKitBleManager?,
    dashState: Flow<DashState>?,
    pinnedControlId: String?,
    teslaStatus: StateFlow<TeslaStatus>?,
    teslaResetPending: StateFlow<Boolean>?,
    onEnrollTesla: () -> Unit,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onNext: () -> Unit,
    onBattery: () -> Unit,
    onClimate: () -> Unit,
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedDataSource by rememberSaveable { mutableStateOf(DataSourceType.DASHKIT) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
            .systemBarsPadding()
    ) {
        if (selectedDataSource == DataSourceType.DASHKIT) {
            ConnectedHomeContent(
                dashState = dashState,
                bleManager = bleManager,
                connectionStatus = connectionStatus,
                activeSourceType = activeSourceType,
                pinnedControlId = pinnedControlId,
                teslaStatus = teslaStatus,
                teslaResetPending = teslaResetPending,
                onEnrollTesla = onEnrollTesla,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onSelectDataSource = { selectedDataSource = it },
                onBattery = onBattery,
                onClimate = onClimate,
                onAutomations = onAutomations,
                onControls = onControls,
                onDrive = onDrive,
                onSettingsClick = onSettingsClick
            )
        } else {
            SetupScreen(
                connectionStatus = connectionStatus,
                selectedDataSource = selectedDataSource,
                onSelectDataSource = { selectedDataSource = it },
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onNext = onNext,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

private val ScreenPadding = 24.dp
private val GridGap = 16.dp
private val HeaderHeight = 48.dp

@Composable
private fun ConnectedHomeContent(
    dashState: Flow<DashState>?,
    bleManager: DashKitBleManager?,
    connectionStatus: ConnectionStatus,
    activeSourceType: String?,
    pinnedControlId: String?,
    teslaStatus: StateFlow<TeslaStatus>?,
    teslaResetPending: StateFlow<Boolean>?,
    onEnrollTesla: () -> Unit,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onSelectDataSource: (String) -> Unit,
    onBattery: () -> Unit,
    onClimate: () -> Unit,
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val fallback = DashState()
    val state by (dashState ?: flowOf(fallback)).collectAsState(initial = fallback)
    val car = state.carState
    val useImperial = state.displaySettings.useImperial
    val idleTesla = remember { MutableStateFlow(TeslaStatus.Idle) }
    val tesla by (teslaStatus ?: idleTesla).collectAsState()
    val idleReset = remember { MutableStateFlow(false) }
    val resetPending by (teslaResetPending ?: idleReset).collectAsState()
    val pinned = controlById(pinnedControlId)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val contentMinHeight = maxHeight - ScreenPadding * 2 - HeaderHeight - 8.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding)
        ) {
            Header(
                showDataSource = landscape,
                connectionStatus = connectionStatus,
                activeSourceType = activeSourceType,
                onSelectDataSource = onSelectDataSource,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (landscape) {
                LandscapeContent(
                    minHeight = contentMinHeight,
                    car = car,
                    useImperial = useImperial,
                    bleManager = bleManager,
                    pinned = pinned,
                    tesla = tesla,
                    resetPending = resetPending,
                    onEnrollTesla = onEnrollTesla,
                    onBattery = onBattery,
                    onClimate = onClimate,
                    onAutomations = onAutomations,
                    onControls = onControls,
                    onDrive = onDrive
                )
            } else {
                PortraitContent(
                    car = car,
                    useImperial = useImperial,
                    bleManager = bleManager,
                    pinned = pinned,
                    tesla = tesla,
                    resetPending = resetPending,
                    connectionStatus = connectionStatus,
                    activeSourceType = activeSourceType,
                    onEnrollTesla = onEnrollTesla,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onSelectDataSource = onSelectDataSource,
                    onBattery = onBattery,
                    onClimate = onClimate,
                    onAutomations = onAutomations,
                    onControls = onControls,
                    onDrive = onDrive
                )
            }
        }
    }
}

@Composable
private fun Header(
    showDataSource: Boolean,
    connectionStatus: ConnectionStatus,
    activeSourceType: String?,
    onSelectDataSource: (String) -> Unit,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
    ) {
        // Offset by the IconButton's built-in content inset so the gear
        // glyph lines up with the tiles' left edge below.
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-12).dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = DarkColors.TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        if (showDataSource) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
            ) {
                DebugDataSourceMenu(connectionStatus, activeSourceType, onSelectDataSource, onConnect, onDisconnect)
            }
        }
    }
}

@Composable
private fun PortraitContent(
    car: CarState,
    useImperial: Boolean,
    bleManager: DashKitBleManager?,
    pinned: ControlAction?,
    tesla: TeslaStatus,
    resetPending: Boolean,
    connectionStatus: ConnectionStatus,
    activeSourceType: String?,
    onEnrollTesla: () -> Unit,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onSelectDataSource: (String) -> Unit,
    onBattery: () -> Unit,
    onClimate: () -> Unit,
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WidgetGrid(car = car, useImperial = useImperial, fillHeight = false, onBattery = onBattery, onClimate = onClimate)

        Spacer(modifier = Modifier.height(32.dp))

        if (BuildConfig.DEBUG) {
            TeslaTile(status = tesla, resetPending = resetPending, onEnroll = onEnrollTesla)
        }

        pinned?.let { action ->
            Text(
                text = stringResource(R.string.home_pinned),
                color = DarkColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            PinnedButton(action = action, bleManager = bleManager)
            Spacer(modifier = Modifier.height(24.dp))
        }

        ActionButton(
            label = stringResource(R.string.home_automations),
            icon = Icons.Rounded.AutoAwesome,
            accent = false,
            onClick = onAutomations
        )
        Spacer(modifier = Modifier.height(12.dp))
        ActionButton(
            label = stringResource(R.string.home_controls),
            icon = Icons.Rounded.Tune,
            accent = false,
            onClick = onControls
        )
        Spacer(modifier = Modifier.height(12.dp))
        DriveButton(onDrive)
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            DebugDataSourceMenu(connectionStatus, activeSourceType, onSelectDataSource, onConnect, onDisconnect)
        }
    }
}

// Mirrors HomeView.swift: widget grid on the left, action tiles and the
// primary buttons on the right, both stretched to the viewport height so the
// screen fills without scrolling unless the content is genuinely taller.
@Composable
private fun LandscapeContent(
    minHeight: Dp,
    car: CarState,
    useImperial: Boolean,
    bleManager: DashKitBleManager?,
    pinned: ControlAction?,
    tesla: TeslaStatus,
    resetPending: Boolean,
    onEnrollTesla: () -> Unit,
    onBattery: () -> Unit,
    onClimate: () -> Unit,
    onAutomations: () -> Unit,
    onControls: () -> Unit,
    onDrive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ScreenPadding)
    ) {
        WidgetGrid(
            car = car,
            useImperial = useImperial,
            fillHeight = true,
            onBattery = onBattery,
            onClimate = onClimate,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(GridGap)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GridGap)
            ) {
                ActionTile(
                    label = stringResource(R.string.home_automations),
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = onAutomations,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ActionTile(
                    label = stringResource(R.string.home_controls),
                    icon = Icons.Rounded.Tune,
                    onClick = onControls,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (BuildConfig.DEBUG) {
                    TeslaTile(status = tesla, resetPending = resetPending, onEnroll = onEnrollTesla)
                }
                pinned?.let { action ->
                    PinnedButton(action = action, bleManager = bleManager)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                DriveButton(onDrive)
            }
        }
    }
}

@Composable
private fun WidgetGrid(
    car: CarState,
    useImperial: Boolean,
    fillHeight: Boolean,
    onBattery: () -> Unit,
    onClimate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridGap)
    ) {
        val rowModifier = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(GridGap)
        ) {
            InfoWidget(
                icon = Icons.Rounded.BatteryChargingFull,
                label = stringResource(R.string.home_widget_battery),
                value = socText(car),
                fillHeight = fillHeight,
                modifier = Modifier.weight(1f),
                onClick = onBattery
            )
            InfoWidget(
                icon = Icons.Rounded.DeviceThermostat,
                label = stringResource(R.string.home_widget_battery_temp),
                value = batteryTempText(car),
                fillHeight = fillHeight,
                modifier = Modifier.weight(1f),
                onClick = onBattery
            )
        }
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(GridGap)
        ) {
            InfoWidget(
                icon = Icons.Rounded.AcUnit,
                label = stringResource(R.string.home_widget_ac_temp),
                value = tempText(car.acTemp),
                fillHeight = fillHeight,
                modifier = Modifier.weight(1f),
                onClick = onClimate
            )
            InfoWidget(
                icon = Icons.Rounded.Speed,
                label = stringResource(R.string.home_widget_odometer),
                value = odometerText(car.odometer, useImperial),
                fillHeight = fillHeight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PinnedButton(action: ControlAction, bleManager: DashKitBleManager?) {
    ControlActionButton(
        action = action,
        pinned = true,
        enabled = bleManager != null,
        onClick = { action.perform(bleManager) },
        onLongClick = null
    )
}

@Composable
private fun DriveButton(onDrive: () -> Unit) {
    ActionButton(
        label = stringResource(R.string.home_drive),
        icon = Icons.Rounded.DirectionsCar,
        accent = true,
        onClick = onDrive
    )
}

@Composable
private fun InfoWidget(
    icon: ImageVector,
    label: String,
    value: String,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.aspectRatio(1.4f))
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconChip(icon = icon, tint = AccentColor, background = AccentColor.copy(alpha = 0.14f))
        Column {
            Text(
                text = value,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                color = DarkColors.TextMuted,
                fontSize = 13.sp
            )
        }
    }
}

// Landscape counterpart of ActionButton: icon at the top, label at the
// bottom, sized to match the stat tiles beside it.
@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconChip(icon = icon, tint = Color.White, background = Color.White.copy(alpha = 0.08f))
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun IconChip(icon: ImageVector, tint: Color, background: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(background, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
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
