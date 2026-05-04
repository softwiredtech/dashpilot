package com.softwiredtech.dashpilot.ui

import android.content.Context
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datamodel.dash.DisplaySettings
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.datamodel.dash.dashboardById
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.datamodel.dash.saveSelectedDashboard
import com.softwiredtech.dashpilot.ui.theme.AccentColor

private data class ToggleSetting(
    @StringRes val labelRes: Int,
    val key: String,
    val defaultValue: Boolean
)

private val vehicleBusToggles = listOf(
    ToggleSetting(R.string.settings_toggle_show_car_battery, "show_car_battery", true),
    ToggleSetting(R.string.settings_toggle_show_odometer, "show_odometer", true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onDisplaySettingsChanged: (DisplaySettings) -> Unit = {}) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE) }

    val extraBusState = remember {
        mutableStateOf(sharedPrefs.getBoolean("extra_vehicle_bus", false))
    }

    val showPhoneBatteryState = remember {
        mutableStateOf(sharedPrefs.getBoolean("show_phone_battery", true))
    }

    val useImperialState = remember {
        mutableStateOf(sharedPrefs.getBoolean("use_imperial", false))
    }

    val darkModeState = remember {
        mutableStateOf(sharedPrefs.getBoolean("dark_mode", false))
    }

    val alwaysOnBlindSpotMonitorState = remember {
        mutableStateOf(sharedPrefs.getBoolean("always_on_blind_spot_monitor", true))
    }

    val renderQualityState = remember {
        mutableIntStateOf(sharedPrefs.getInt("render_quality", 3))
    }

    val darkModeBackgroundGrayState = remember {
        mutableFloatStateOf(sharedPrefs.getInt("dark_mode_background_gray", 0).toFloat())
    }

    val selectedDashboardId = remember {
        mutableStateOf(getSelectedDashboard(context).id)
    }
    val selectedDashboardCapabilities = remember(selectedDashboardId.value) {
        dashboardById(selectedDashboardId.value)?.capabilities
    }
    val showVanillaSettings = selectedDashboardCapabilities?.supports3dVisualizerSettings ?: false
    val showPhoneBatteryToggle = selectedDashboardCapabilities?.supportsPhoneBatteryToggle ?: true
    val supportsVehicleBusWidgets = selectedDashboardCapabilities?.supportsVehicleBusWidgets == true

    val vehicleBusToggleStates = remember {
        vehicleBusToggles.associate { toggle ->
            toggle.key to mutableStateOf(sharedPrefs.getBoolean(toggle.key, toggle.defaultValue))
        }
    }

    fun buildDisplaySettings() = DisplaySettings(
        showPhoneBattery = showPhoneBatteryState.value,
        showCarBattery = vehicleBusToggleStates.getValue("show_car_battery").value,
        showOdometer = vehicleBusToggleStates.getValue("show_odometer").value,
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
                    containerColor = Color(0xFF0D0D0D)
                )
            )
        },
        containerColor = Color(0xFF0D0D0D)
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
                color = Color(0xFF666666),
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
                                    if (isSelected) Modifier.border(2.dp, Color(0xFF5CBD68), RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .background(Color(0xFF1A1A1A))
                                .clickable {
                                    selectedDashboardId.value = dashboard.id
                                    saveSelectedDashboard(context, dashboard)
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
                                        .background(Color(0xFF2A2A2A))
                                )
                            }
                            Text(
                                text = dashboardName,
                                color = if (isSelected) Color(0xFF5CBD68) else Color(0xFFAAAAAA),
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
                color = Color(0xFF666666),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (supportsVehicleBusWidgets) {
                SettingsToggle(stringResource(R.string.settings_toggle_extra_vehicle_bus), extraBusState.value) { enabled ->
                    extraBusState.value = enabled
                    sharedPrefs.edit { putBoolean("extra_vehicle_bus", enabled) }
                }

                if (!extraBusState.value) {
                    Text(
                        text = stringResource(R.string.settings_vehicle_bus_requires_extra),
                        color = Color(0xFF666666),
                        fontSize = 13.sp,
                    )
                }

                if (extraBusState.value) {
                    vehicleBusToggleStates.forEach { (key, state) ->
                        SettingsToggle(stringResource(vehicleBusToggles.first { it.key == key }.labelRes), state.value) { enabled ->
                            state.value = enabled
                            sharedPrefs.edit { putBoolean(key, enabled) }
                            onDisplaySettingsChanged(buildDisplaySettings())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(stringResource(R.string.settings_section_display), color = Color(0xFF888888), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(stringResource(R.string.settings_toggle_use_imperial), useImperialState.value) { enabled ->
                useImperialState.value = enabled
                sharedPrefs.edit { putBoolean("use_imperial", enabled) }
                onDisplaySettingsChanged(buildDisplaySettings())
            }

            if (showPhoneBatteryToggle) {
                SettingsToggle(stringResource(R.string.settings_toggle_show_phone_battery), showPhoneBatteryState.value) { enabled ->
                    showPhoneBatteryState.value = enabled
                    sharedPrefs.edit { putBoolean("show_phone_battery", enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showVanillaSettings) {
                Text(stringResource(R.string.settings_section_3d_visualizer), color = Color(0xFF888888), fontSize = 16.sp)

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggle(stringResource(R.string.settings_toggle_always_on_blind_spot_monitor), alwaysOnBlindSpotMonitorState.value) { enabled ->
                    alwaysOnBlindSpotMonitorState.value = enabled
                    sharedPrefs.edit { putBoolean("always_on_blind_spot_monitor", enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }

                SettingsToggle(stringResource(R.string.settings_toggle_dark_mode), darkModeState.value) { enabled ->
                    darkModeState.value = enabled
                    sharedPrefs.edit { putBoolean("dark_mode", enabled) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }

                if (darkModeState.value) {
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
                            sharedPrefs.edit { putInt("dark_mode_background_gray", v) }
                            onDisplaySettingsChanged(buildDisplaySettings())
                        },
                        valueRange = 0f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentColor,
                            activeTrackColor = AccentColor,
                            inactiveTrackColor = Color(0xFF333333)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                RenderQualitySelector(renderQualityState.intValue) { quality ->
                    renderQualityState.intValue = quality
                    sharedPrefs.edit { putInt("render_quality", quality) }
                    onDisplaySettingsChanged(buildDisplaySettings())
                }

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
                    color = Color(0xFF888888),
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
        color = Color(0xFF888888),
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
                .background(Color(0xFF333333))
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
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        fontSize = 13.sp
                    )
                }
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
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF333333),
                uncheckedBorderColor = Color(0xFF555555)
            )
        )
    }
}
