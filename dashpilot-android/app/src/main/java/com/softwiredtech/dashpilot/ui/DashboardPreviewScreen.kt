package com.softwiredtech.dashpilot.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.*
import com.softwiredtech.dashpilot.datasource.DemoDataSource
import com.softwiredtech.dashpilot.datasource.DataSourceType
import kotlinx.coroutines.flow.map

/** A separate simulator: previewing never replaces the user's live connection. */
@Composable
fun DashboardPreviewScreen(type: String, url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val demo = remember { DemoDataSource() }
    val settings = remember {
        val prefs = context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        DisplaySettings(
            useImperial = prefs.getBoolean(PREF_USE_IMPERIAL, DEFAULT_USE_IMPERIAL),
            darkMode = prefs.getBoolean(PREF_DARK_MODE, DEFAULT_DARK_MODE),
            showCarBattery = prefs.getBoolean(PREF_SHOW_CAR_BATTERY, DEFAULT_SHOW_CAR_BATTERY),
            showOdometer = prefs.getBoolean(PREF_SHOW_ODOMETER, DEFAULT_SHOW_ODOMETER),
            showPhoneBattery = prefs.getBoolean(PREF_SHOW_PHONE_BATTERY, DEFAULT_SHOW_PHONE_BATTERY),
            alwaysOnBlindSpotMonitor = prefs.getBoolean(PREF_ALWAYS_ON_BLIND_SPOT_MONITOR, DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR),
            renderQuality = prefs.getInt(PREF_RENDER_QUALITY, DEFAULT_RENDER_QUALITY),
            darkModeBackgroundGray = prefs.getInt(PREF_DARK_MODE_BACKGROUND_GRAY, DEFAULT_DARK_MODE_BACKGROUND_GRAY)
        )
    }
    val state = remember(demo, settings) {
        demo.incomingMessages.map { car ->
            DashState(
                carState = if (settings.useImperial) car.toImperial() else car,
                displaySettings = settings,
                currentTime = System.currentTimeMillis(),
                phoneBattery = 85,
                dataSourceType = DataSourceType.DEMO
            )
        }
    }
    DisposableEffect(demo) {
        demo.connect("")
        onDispose { demo.disconnect() }
    }
    Box(Modifier.fillMaxSize()) {
        DashboardScreen(type, url, state, isPreview = true)
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.75f), contentColor = Color.White)
        ) { Text(stringResource(R.string.dashboard_preview_close)) }
    }
}
