package com.softwiredtech.dashpilot.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.softwiredtech.dashpilot.datamodel.DisplaySettings
import com.softwiredtech.dashpilot.ui.theme.AccentColor

private data class ToggleSetting(
    val label: String,
    val key: String,
    val defaultValue: Boolean
)

private val vehicleBusToggles = listOf(
    ToggleSetting("Show car battery", "show_car_battery", true),
    ToggleSetting("Show odometer", "show_odometer", true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onDisplaySettingsChanged: (DisplaySettings) -> Unit = {}) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("dash_prefs", Context.MODE_PRIVATE) }

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

    val vehicleBusToggleStates = remember {
        vehicleBusToggles.map { toggle ->
            toggle to mutableStateOf(sharedPrefs.getBoolean(toggle.key, toggle.defaultValue))
        }
    }

    fun buildDisplaySettings() = DisplaySettings(
        showPhoneBattery = showPhoneBatteryState.value,
        showCarBattery = vehicleBusToggleStates[0].second.value,
        showOdometer = vehicleBusToggleStates[1].second.value,
        useImperial = useImperialState.value,
        darkMode = darkModeState.value,
        alwaysOnBlindSpotMonitor = alwaysOnBlindSpotMonitorState.value,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
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
        ) {
            val packageInfo = remember {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Version", color = Color.White, fontSize = 16.sp)
                Text(
                    text = "${packageInfo.versionName} (${packageInfo.longVersionCode})",
                    color = Color(0xFF888888),
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SettingsToggle("Extra Vehicle bus", extraBusState.value) { enabled ->
                extraBusState.value = enabled
                sharedPrefs.edit { putBoolean("extra_vehicle_bus", enabled) }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Display", color = Color(0xFF888888), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle("Use imperial units", useImperialState.value) { enabled ->
                useImperialState.value = enabled
                sharedPrefs.edit { putBoolean("use_imperial", enabled) }
                onDisplaySettingsChanged(buildDisplaySettings())
            }

            SettingsToggle("Dark mode", darkModeState.value) { enabled ->
                darkModeState.value = enabled
                sharedPrefs.edit { putBoolean("dark_mode", enabled) }
                onDisplaySettingsChanged(buildDisplaySettings())
            }

            SettingsToggle("Show phone battery", showPhoneBatteryState.value) { enabled ->
                showPhoneBatteryState.value = enabled
                sharedPrefs.edit { putBoolean("show_phone_battery", enabled) }
                onDisplaySettingsChanged(buildDisplaySettings())
            }

            SettingsToggle("Always on blind-spot monitor", alwaysOnBlindSpotMonitorState.value) { enabled ->
                alwaysOnBlindSpotMonitorState.value = enabled
                sharedPrefs.edit { putBoolean("always_on_blind_spot_monitor", enabled) }
                onDisplaySettingsChanged(buildDisplaySettings())
            }

            if (extraBusState.value) {
                vehicleBusToggleStates.forEach { (toggle, state) ->
                    SettingsToggle(toggle.label, state.value) { enabled ->
                        state.value = enabled
                        sharedPrefs.edit { putBoolean(toggle.key, enabled) }
                        onDisplaySettingsChanged(buildDisplaySettings())
                    }
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
