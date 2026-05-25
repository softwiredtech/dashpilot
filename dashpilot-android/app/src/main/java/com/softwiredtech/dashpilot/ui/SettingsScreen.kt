package com.softwiredtech.dashpilot.ui

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.datasource.DashKitOtaUpdate
import com.softwiredtech.dashpilot.datasource.OtaState

import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datamodel.dash.DashboardType
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
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.datamodel.dash.dashboardById
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.datamodel.dash.saveDevRiveFileUri
import com.softwiredtech.dashpilot.datamodel.dash.saveSelectedDashboard
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onDisplaySettingsChanged: (DisplaySettings) -> Unit = {}, bleManager: DashKitBleManager? = null) {
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            saveDevRiveFileUri(context, uri.toString())
            selectedDashboardId.value = "dev_rive"
            saveSelectedDashboard(context, dashboardById("dev_rive")!!)
        }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val packageInfo = remember {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            SectionHeader(stringResource(R.string.settings_section_dashboard))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_dashboard_subtitle),
                color = DarkColors.TextSubtle,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                availableDashboards.forEach { dashboard ->
                    item {
                        val isSelected = dashboard.id == selectedDashboardId.value
                        Column(
                            modifier = Modifier
                                .width(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isSelected) Modifier.border(2.dp, AccentColor, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .background(DarkColors.Surface)
                                .clickable {
                                    if (dashboard.type == DashboardType.DEV_RIVE) {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    } else {
                                        selectedDashboardId.value = dashboard.id
                                        saveSelectedDashboard(context, dashboard)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val dashboardName = stringResource(dashboard.nameRes)
                            if (dashboard.screenshotRes != 0) {
                                Image(
                                    painter = painterResource(id = dashboard.screenshotRes),
                                    contentDescription = dashboardName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .background(DarkColors.SurfaceSelected)
                                )
                            }
                            Text(
                                text = dashboardName,
                                color = if (isSelected) AccentColor else DarkColors.ContentDisabled,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_theme_availability_note),
                color = DarkColors.TextSubtle,
                fontSize = 13.sp
            )

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

            // Firmware update (only for DashKit)
            if (bleManager != null) {
                FirmwareUpdateSection(bleManager)
                Spacer(modifier = Modifier.height(24.dp))
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
private fun FirmwareUpdateSection(bleManager: DashKitBleManager) {
    val context = LocalContext.current
    val otaUpdate = remember(bleManager) { DashKitOtaUpdate(bleManager) }
    val otaState by otaUpdate.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("FirmwareUpdate", "Failed to read file: ${e.message}")
            null
        }
        if (bytes != null && bytes.isNotEmpty()) {
            otaUpdate.start(bytes)
        }
    }

    DisposableEffect(otaUpdate) {
        onDispose { otaUpdate.cancel() }
    }

    Text(
        text = stringResource(R.string.settings_section_firmware),
        color = DarkColors.TextMuted,
        fontSize = 16.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    when (val state = otaState) {
        is OtaState.Idle -> {
            Button(
                onClick = { filePicker.launch("application/octet-stream") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text(stringResource(R.string.settings_firmware_update))
            }
        }
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
                onClick = { filePicker.launch("application/octet-stream") },
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
