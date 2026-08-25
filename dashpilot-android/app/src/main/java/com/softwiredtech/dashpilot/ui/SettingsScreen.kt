package com.softwiredtech.dashpilot.ui

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.edit
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ble.FirmwareUpdateManager
import com.softwiredtech.dashpilot.ble.OtaState
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.ble.VehicleControl
import com.softwiredtech.dashpilot.ui.tesla.teslaTileSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datamodel.dash.DisplaySettings
import com.softwiredtech.dashpilot.datamodel.dash.ManifestDisplaySetting
import com.softwiredtech.dashpilot.datamodel.dash.PREF_ALWAYS_ON_BLIND_SPOT_MONITOR
import com.softwiredtech.dashpilot.datamodel.dash.PREF_DARK_MODE
import com.softwiredtech.dashpilot.datamodel.dash.PREF_DARK_MODE_BACKGROUND_GRAY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_EXTRA_VEHICLE_BUS
import com.softwiredtech.dashpilot.datamodel.dash.PREF_RENDER_QUALITY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_SHOW_CAR_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_SHOW_ODOMETER
import com.softwiredtech.dashpilot.datamodel.dash.PREF_SHOW_PHONE_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.PREF_USE_IMPERIAL
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_DARK_MODE
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_DARK_MODE_BACKGROUND_GRAY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_EXTRA_VEHICLE_BUS
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_RENDER_QUALITY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_SHOW_CAR_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_SHOW_ODOMETER
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_SHOW_PHONE_BATTERY
import com.softwiredtech.dashpilot.datamodel.dash.DEFAULT_USE_IMPERIAL
import com.softwiredtech.dashpilot.datamodel.dash.dashboardById
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

private data class ToggleSetting(
    @StringRes val labelRes: Int,
    val key: String,
    val defaultValue: Boolean
)

private val vehicleBusToggles = listOf(
    ToggleSetting(R.string.settings_toggle_show_car_battery, PREF_SHOW_CAR_BATTERY, DEFAULT_SHOW_CAR_BATTERY),
    ToggleSetting(R.string.settings_toggle_show_odometer, PREF_SHOW_ODOMETER, DEFAULT_SHOW_ODOMETER),
)

private val settingsTabs = listOf(
    R.string.settings_tab_general,
    R.string.settings_tab_dashkit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDisplaySettingsChanged: (DisplaySettings) -> Unit = {},
    bleManager: DashKitBleManager? = null,
    dashkitUpdateManager: FirmwareUpdateManager? = null,
    teslaStatus: StateFlow<TeslaStatus>? = null,
    teslaResetPending: StateFlow<Boolean>? = null,
    onRemoveTesla: () -> Boolean = { false },
    onEnrollTesla: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onThemeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE) }

    val extraBusState = remember {
        mutableStateOf(sharedPrefs.getBoolean(PREF_EXTRA_VEHICLE_BUS, DEFAULT_EXTRA_VEHICLE_BUS))
    }

    val showPhoneBatteryState = remember {
        mutableStateOf(sharedPrefs.getBoolean(PREF_SHOW_PHONE_BATTERY, DEFAULT_SHOW_PHONE_BATTERY))
    }

    val useImperialState = remember {
        mutableStateOf(sharedPrefs.getBoolean(PREF_USE_IMPERIAL, DEFAULT_USE_IMPERIAL))
    }

    val darkModeState = remember {
        mutableStateOf(sharedPrefs.getBoolean(PREF_DARK_MODE, DEFAULT_DARK_MODE))
    }

    val alwaysOnBlindSpotMonitorState = remember {
        mutableStateOf(sharedPrefs.getBoolean(PREF_ALWAYS_ON_BLIND_SPOT_MONITOR, DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR))
    }

    val renderQualityState = remember {
        mutableIntStateOf(sharedPrefs.getInt(PREF_RENDER_QUALITY, DEFAULT_RENDER_QUALITY))
    }

    val darkModeBackgroundGrayState = remember {
        mutableFloatStateOf(sharedPrefs.getInt(PREF_DARK_MODE_BACKGROUND_GRAY, DEFAULT_DARK_MODE_BACKGROUND_GRAY).toFloat())
    }

    val selectedDashboardId = remember {
        mutableStateOf(getSelectedDashboard(context).id)
    }

    val selectedDashboardManifest = remember(selectedDashboardId.value) {
        dashboardById(selectedDashboardId.value)?.manifest
    }

    fun hasSetting(key: ManifestDisplaySetting): Boolean =
        selectedDashboardManifest?.settings?.contains(key) == true

    val showUseImperialToggle = hasSetting(ManifestDisplaySetting.USE_IMPERIAL)
    val showAlwaysOnBlindSpotMonitorToggle = hasSetting(ManifestDisplaySetting.ALWAYS_ON_BLIND_SPOT_MONITOR)
    val showDarkModeToggle = hasSetting(ManifestDisplaySetting.DARK_MODE)
    val showDarkModeBackgroundGraySlider = hasSetting(ManifestDisplaySetting.DARK_MODE_BACKGROUND_GRAY) && showDarkModeToggle
    val showRenderQualitySelector = hasSetting(ManifestDisplaySetting.RENDER_QUALITY)
    val showVisualizerSettings = hasSetting(ManifestDisplaySetting.RENDER_QUALITY) ||
                                 showDarkModeBackgroundGraySlider ||
                                 showAlwaysOnBlindSpotMonitorToggle ||
                                 showDarkModeToggle
    val showCarBatteryToggle = hasSetting(ManifestDisplaySetting.SHOW_CAR_BATTERY)
    val showOdometerToggle = hasSetting(ManifestDisplaySetting.SHOW_ODOMETER)
    val showPhoneBatteryToggle = hasSetting(ManifestDisplaySetting.SHOW_PHONE_BATTERY)

    val vehicleBusToggleStates = remember {
        vehicleBusToggles.associate { toggle ->
            toggle.key to mutableStateOf(sharedPrefs.getBoolean(toggle.key, toggle.defaultValue))
        }
    }

    fun buildDisplaySettings() = DisplaySettings(
        showPhoneBattery = showPhoneBatteryState.value,
        showCarBattery = vehicleBusToggleStates.getValue(PREF_SHOW_CAR_BATTERY).value,
        showOdometer = vehicleBusToggleStates.getValue(PREF_SHOW_ODOMETER).value,
        useImperial = useImperialState.value,
        darkMode = darkModeState.value,
        alwaysOnBlindSpotMonitor = alwaysOnBlindSpotMonitorState.value,
        renderQuality = renderQualityState.intValue,
        darkModeBackgroundGray = darkModeBackgroundGrayState.floatValue.toInt(),
    )

    val selectedTab = remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkColors.Background
                )
            )
        },
        containerColor = DarkColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab.intValue,
                containerColor = DarkColors.Background,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.intValue]),
                        color = AccentColor
                    )
                }
            ) {
                settingsTabs.forEachIndexed { index, titleRes ->
                    Tab(
                        selected = selectedTab.intValue == index,
                        onClick = { selectedTab.intValue = index },
                        selectedContentColor = AccentColor,
                        unselectedContentColor = DarkColors.TextMuted,
                        text = { Text(stringResource(titleRes)) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
            if (selectedTab.intValue == 0) {
            val packageInfo = remember {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            SectionHeader(stringResource(R.string.settings_section_dashboard))
            Spacer(modifier = Modifier.height(10.dp))

            val currentTheme = dashboardById(selectedDashboardId.value)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkColors.Surface)
                    .clickable { onThemeClick() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentTheme != null && currentTheme.screenshotRes != 0) {
                    Image(
                        painter = painterResource(id = currentTheme.screenshotRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkColors.SurfaceSelected)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTheme?.let { stringResource(it.nameRes) } ?: "",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_dashboard_subtitle),
                        color = DarkColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
                Text(text = "›", color = DarkColors.TextMuted, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(R.string.settings_section_configuration), color = DarkColors.TextMuted, fontSize = 16.sp)

            SettingsToggle(stringResource(R.string.settings_toggle_extra_vehicle_bus), extraBusState.value) { enabled ->
                extraBusState.value = enabled
                sharedPrefs.edit { putBoolean(PREF_EXTRA_VEHICLE_BUS, enabled) }
            }

            Text(stringResource(R.string.settings_section_display), color = DarkColors.TextMuted, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (extraBusState.value && showCarBatteryToggle) {
                val key = PREF_SHOW_CAR_BATTERY
                val state = vehicleBusToggleStates.getValue(key)
                SettingsToggle(stringResource(R.string.settings_toggle_show_car_battery), state.value) { enabled ->
                    state.value = enabled
                    sharedPrefs.edit { putBoolean(key, enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }
            }
            if (extraBusState.value && showOdometerToggle) {
                val key = PREF_SHOW_ODOMETER
                val state = vehicleBusToggleStates.getValue(key)
                SettingsToggle(stringResource(R.string.settings_toggle_show_odometer), state.value) { enabled ->
                    state.value = enabled
                    sharedPrefs.edit { putBoolean(key, enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }
            }

            if (showUseImperialToggle) {
                SettingsToggle(stringResource(R.string.settings_toggle_use_imperial), useImperialState.value) { enabled ->
                    useImperialState.value = enabled
                    sharedPrefs.edit { putBoolean(PREF_USE_IMPERIAL, enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }
            }

            if (showPhoneBatteryToggle) {
                SettingsToggle(stringResource(R.string.settings_toggle_show_phone_battery), showPhoneBatteryState.value) { enabled ->
                    showPhoneBatteryState.value = enabled
                    sharedPrefs.edit { putBoolean(PREF_SHOW_PHONE_BATTERY, enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showVisualizerSettings) {
                Text(stringResource(R.string.settings_section_3d_visualizer), color = DarkColors.TextMuted, fontSize = 16.sp)

                Spacer(modifier = Modifier.height(8.dp))

                if (showAlwaysOnBlindSpotMonitorToggle) {
                    SettingsToggle(stringResource(R.string.settings_toggle_always_on_blind_spot_monitor), alwaysOnBlindSpotMonitorState.value) { enabled ->
                        alwaysOnBlindSpotMonitorState.value = enabled
                        sharedPrefs.edit { putBoolean(PREF_ALWAYS_ON_BLIND_SPOT_MONITOR, enabled) }
                        onDisplaySettingsChanged(buildDisplaySettings())
                    }
                }

                if (showDarkModeToggle) {
                    SettingsToggle(stringResource(R.string.settings_toggle_dark_mode), darkModeState.value) { enabled ->
                        darkModeState.value = enabled
                        sharedPrefs.edit { putBoolean(PREF_DARK_MODE, enabled) }
                        onDisplaySettingsChanged(buildDisplaySettings())
                    }
                }

                if (showDarkModeBackgroundGraySlider && darkModeState.value) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.settings_dark_mode_background_brightness), color = Color.White, fontSize = 16.sp)
                        val grayVal = darkModeBackgroundGrayState.floatValue.toInt()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(grayVal, grayVal, grayVal))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$grayVal",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Slider(
                        value = darkModeBackgroundGrayState.floatValue,
                        onValueChange = { darkModeBackgroundGrayState.floatValue = it },
                        onValueChangeFinished = {
                            val v = darkModeBackgroundGrayState.floatValue.toInt()
                            sharedPrefs.edit { putInt(PREF_DARK_MODE_BACKGROUND_GRAY, v) }
                            onDisplaySettingsChanged(buildDisplaySettings())
                        },
                        valueRange = 0f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentColor,
                            activeTrackColor = AccentColor,
                            inactiveTrackColor = DarkColors.Border
                        )
                    )
                }

                if (showRenderQualitySelector) {
                    Spacer(modifier = Modifier.height(8.dp))

                    RenderQualitySelector(renderQualityState.intValue) { quality ->
                        renderQualityState.intValue = quality
                        sharedPrefs.edit { putInt(PREF_RENDER_QUALITY, quality) }
                        onDisplaySettingsChanged(buildDisplaySettings())
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (BuildConfig.DEBUG) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReplayOnboarding() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_replay_onboarding), color = Color.White, fontSize = 16.sp)
                    Text(text = "›", color = DarkColors.TextMuted, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.settings_version), color = Color.White, fontSize = 16.sp)
                Text(
                    text = "${packageInfo.versionName} (${packageInfo.longVersionCode})",
                    color = DarkColors.TextMuted,
                    fontSize = 16.sp
                )
            }
            } // end General tab

            if (selectedTab.intValue == 1) {
                if (bleManager != null && dashkitUpdateManager != null) {
                    DashKitSettingsContent(
                        bleManager,
                        dashkitUpdateManager,
                        teslaStatus,
                        teslaResetPending,
                        onRemoveTesla,
                        onEnrollTesla,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_dashkit_not_connected),
                        color = DarkColors.TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
            } // end scrollable content column
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = DarkColors.TextMuted,
        fontSize = 16.sp
    )
}

@Composable
private fun RenderQualitySelector(selected: Int, onSelected: (Int) -> Unit) {
    val options = listOf(1 to stringResource(R.string.settings_render_low), 2 to stringResource(R.string.settings_render_med), 3 to stringResource(R.string.settings_render_high))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(R.string.settings_render_quality_title), color = Color.White, fontSize = 16.sp)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(DarkColors.Border)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentColor else Color.Transparent)
                        .clickable { onSelected(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else DarkColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PairNewDeviceSection(bleManager: DashKitBleManager) {
    val context = LocalContext.current
    val connectionState by bleManager.connectionState.collectAsState()
    val connected = connectionState == ConnectionStatus.Connected
    val showDialog = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.settings_section_pairing))
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { showDialog.value = true },
            enabled = connected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = Color.White,
                disabledContainerColor = DarkColors.Border,
                disabledContentColor = DarkColors.TextMuted
            )
        ) {
            Text(text = stringResource(R.string.settings_pair_new_device), fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_pair_caption),
            color = DarkColors.TextMuted,
            fontSize = 12.sp
        )

        if (!connected) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_pair_not_connected),
                color = DarkColors.TextMuted,
                fontSize = 13.sp
            )
        }
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text(stringResource(R.string.settings_pair_dialog_title)) },
            text = { Text(stringResource(R.string.settings_pair_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog.value = false
                    val ok = VehicleControl.sendEnterPairing(bleManager)
                    val msg = if (ok) {
                        context.getString(R.string.settings_pair_opened)
                    } else {
                        context.getString(R.string.settings_pair_not_connected)
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }) {
                    Text(stringResource(R.string.settings_pair_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text(stringResource(R.string.settings_pair_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun DashKitSettingsContent(
    bleManager: DashKitBleManager,
    updateManager: FirmwareUpdateManager,
    teslaStatus: StateFlow<TeslaStatus>?,
    teslaResetPending: StateFlow<Boolean>?,
    onRemoveTesla: () -> Boolean,
    onEnrollTesla: () -> Unit,
) {
    val connectionState by bleManager.connectionState.collectAsState()
    val connected = connectionState == ConnectionStatus.Connected

    // On connect, read the installed version and check the server for updates.
    LaunchedEffect(connectionState) {
        if (connected) {
            updateManager.start()
            updateManager.checkForUpdate()
        }
    }

    FirmwareInfoSection(updateManager, connected)
    Spacer(modifier = Modifier.height(24.dp))

    FirmwareUpdateSection(updateManager)
    Spacer(modifier = Modifier.height(24.dp))

    PairNewDeviceSection(bleManager)
    Spacer(modifier = Modifier.height(24.dp))

    TeslaKeySection(
        bleManager,
        teslaStatus,
        teslaResetPending,
        onRemoveTesla,
        onEnrollTesla,
    )
    Spacer(modifier = Modifier.height(24.dp))

    DashKitMaintenanceSection(bleManager, connected)
}

@Composable
private fun FirmwareInfoSection(updateManager: FirmwareUpdateManager, connected: Boolean) {
    val installedVersion by updateManager.installedVersion.collectAsState()

    SectionHeader(stringResource(R.string.settings_section_firmware_info))
    Spacer(modifier = Modifier.height(12.dp))
    InfoRow(
        label = stringResource(R.string.settings_firmware_info_status),
        value = stringResource(
            if (connected) R.string.settings_firmware_info_connected
            else R.string.settings_firmware_info_disconnected
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
    InfoRow(
        label = stringResource(R.string.settings_firmware_info_version),
        value = installedVersion ?: stringResource(R.string.settings_firmware_info_unknown)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Text(text = value, color = DarkColors.TextMuted, fontSize = 16.sp)
    }
}

@Composable
private fun DashKitMaintenanceSection(bleManager: DashKitBleManager, connected: Boolean) {
    val context = LocalContext.current
    val showRebootDialog = remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_section_maintenance))
    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = { showRebootDialog.value = true },
        enabled = connected,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentColor,
            contentColor = Color.White,
            disabledContainerColor = DarkColors.Border,
            disabledContentColor = DarkColors.TextMuted
        )
    ) {
        Text(text = stringResource(R.string.settings_reboot), fontSize = 16.sp)
    }

    if (showRebootDialog.value) {
        AlertDialog(
            onDismissRequest = { showRebootDialog.value = false },
            title = { Text(stringResource(R.string.settings_reboot_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reboot_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRebootDialog.value = false
                    val ok = VehicleControl.sendReboot(bleManager)
                    val msg = if (ok) {
                        context.getString(R.string.settings_reboot_sent)
                    } else {
                        context.getString(R.string.settings_reboot_failed)
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }) {
                    Text(stringResource(R.string.settings_reboot_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog.value = false }) {
                    Text(stringResource(R.string.settings_reboot_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun TeslaKeySection(
    bleManager: DashKitBleManager,
    teslaStatus: StateFlow<TeslaStatus>?,
    teslaResetPending: StateFlow<Boolean>?,
    onRemoveTesla: () -> Boolean,
    onEnrollTesla: () -> Unit,
) {
    val context = LocalContext.current
    val connectionState by bleManager.connectionState.collectAsState()
    val connected = connectionState == ConnectionStatus.Connected
    val idleTesla = remember { MutableStateFlow(TeslaStatus.Idle) }
    val status by (teslaStatus ?: idleTesla).collectAsState()
    val idleReset = remember { MutableStateFlow(false) }
    val resetPending by (teslaResetPending ?: idleReset).collectAsState()
    val showResetDialog = remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_section_tesla_key))
    Text(
        text = stringResource(R.string.settings_tesla_key_caption),
        color = DarkColors.TextMuted,
        fontSize = 12.sp,
    )
    Spacer(modifier = Modifier.height(12.dp))
    val statusLabel = if (resetPending) {
        stringResource(R.string.tesla_connection_removing)
    } else {
        teslaStatusText(status)
    }
    InfoRow(label = stringResource(R.string.settings_tesla_key_status), value = statusLabel)
    Spacer(modifier = Modifier.height(12.dp))

    val hasKey = status.linkState.hasKey
    Button(
        onClick = if (hasKey) ({ showResetDialog.value = true }) else onEnrollTesla,
        enabled = connected && !resetPending,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (hasKey) DarkColors.SurfaceSelected else AccentColor,
            contentColor = Color.White,
            disabledContainerColor = DarkColors.Border,
            disabledContentColor = DarkColors.TextMuted
        )
    ) {
        Text(
            text = stringResource(
                when {
                    resetPending -> R.string.tesla_connection_removing
                    hasKey -> R.string.settings_tesla_reset
                    else -> R.string.settings_tesla_enroll
                }
            ),
            fontSize = 16.sp
        )
    }

    if (showResetDialog.value) {
        AlertDialog(
            onDismissRequest = { showResetDialog.value = false },
            title = { Text(stringResource(R.string.settings_tesla_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_tesla_reset_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog.value = false
                    val ok = onRemoveTesla()
                    val msg = if (ok) {
                        context.getString(R.string.settings_tesla_reset_sent)
                    } else {
                        context.getString(R.string.settings_tesla_reset_failed)
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }) {
                    Text(stringResource(R.string.settings_tesla_reset_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog.value = false }) {
                    Text(stringResource(R.string.settings_tesla_reset_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun teslaStatusText(status: TeslaStatus): String = when (status.linkState) {
    TeslaLinkState.NeverEnrolled -> stringResource(R.string.tesla_tile_key_not_set_up)
    TeslaLinkState.Staged -> stringResource(R.string.tesla_tile_staged)
    TeslaLinkState.Connecting -> stringResource(R.string.tesla_enroll_connecting_body)
    TeslaLinkState.EnrolledNotConnected -> stringResource(R.string.tesla_tile_not_connected)
    TeslaLinkState.EnrolledConnected -> teslaTileSummary(status)
        .ifBlank { stringResource(R.string.tesla_tile_connected) }
    TeslaLinkState.PairingWindow -> stringResource(R.string.tesla_status_pairing)
    TeslaLinkState.EnrollmentFault -> stringResource(R.string.tesla_status_fault)
    TeslaLinkState.Unknown -> "—"
}

@Composable
private fun FirmwareUpdateSection(updateManager: FirmwareUpdateManager) {
    val scope = rememberCoroutineScope()
    val otaState by updateManager.otaState.collectAsState()
    val checkState by updateManager.check.collectAsState()
    val downloadProgress by updateManager.downloadProgress.collectAsState()

    Text(
        text = stringResource(R.string.settings_section_firmware),
        color = DarkColors.TextMuted,
        fontSize = 16.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    // While an upload is in progress (or just finished), show only OTA status.
    val uploadActive = otaState is OtaState.Uploading ||
        otaState is OtaState.Connecting ||
        otaState is OtaState.Rebooting ||
        otaState is OtaState.Error

    val dlProgress = downloadProgress
    when {
        dlProgress != null -> {
            val percent = (dlProgress * 100).toInt()
            Text(
                stringResource(R.string.settings_firmware_downloading, percent),
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { dlProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentColor,
                trackColor = DarkColors.Border
            )
        }
        uploadActive -> OtaStatus(otaState, onRetry = { scope.launch { updateManager.checkForUpdate() } })
        else -> {
            when (val check = checkState) {
                is FirmwareUpdateManager.Check.Checking -> {
                    Text(
                        stringResource(R.string.settings_firmware_checking),
                        color = DarkColors.ContentDisabled,
                        fontSize = 14.sp
                    )
                }
                is FirmwareUpdateManager.Check.Available -> {
                    Text(
                        stringResource(R.string.settings_firmware_available, check.manifest.version),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    check.manifest.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(notes, color = DarkColors.ContentDisabled, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { scope.launch { updateManager.install(check.manifest) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                    ) {
                        Text(stringResource(R.string.settings_firmware_install))
                    }
                }
                is FirmwareUpdateManager.Check.UpToDate -> {
                    Text(
                        stringResource(R.string.settings_firmware_up_to_date),
                        color = DarkColors.ContentDisabled,
                        fontSize = 14.sp
                    )
                }
                is FirmwareUpdateManager.Check.Error -> {
                    Text(
                        stringResource(R.string.settings_firmware_error, check.message),
                        color = DarkColors.Error,
                        fontSize = 14.sp
                    )
                }
                FirmwareUpdateManager.Check.Idle -> {}
            }
        }
    }
}

@Composable
private fun OtaStatus(otaState: OtaState, onRetry: () -> Unit) {
    when (val state = otaState) {
        is OtaState.Idle -> {}
        is OtaState.Connecting -> {
            Text(
                stringResource(R.string.settings_firmware_connecting),
                color = DarkColors.ContentDisabled,
                fontSize = 14.sp
            )
        }
        is OtaState.Uploading -> {
            val percent = (state.progress * 100).toInt()
            Text(
                stringResource(R.string.settings_firmware_uploading, percent),
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentColor,
                trackColor = DarkColors.Border
            )
        }
        is OtaState.Rebooting -> {
            Text(
                stringResource(R.string.settings_firmware_rebooting),
                color = AccentColor,
                fontSize = 14.sp
            )
        }
        is OtaState.Error -> {
            Text(
                stringResource(R.string.settings_firmware_error, state.message),
                color = DarkColors.Error,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text(stringResource(R.string.settings_firmware_update))
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentColor,
                uncheckedThumbColor = DarkColors.TextMuted,
                uncheckedTrackColor = DarkColors.Border,
                uncheckedBorderColor = DarkColors.Disabled
            )
        )
    }
}
