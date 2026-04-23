package com.softwiredtech.dashpilot.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.DashState
import com.softwiredtech.dashpilot.datamodel.SpeedCamera
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun DashboardScreen(
    dashboardType: String,
    dashboardUrl: String,
    dashStateFlow: Flow<DashState>,
    speedCameraFlow: Flow<Pair<SpeedCamera, Int>?> = emptyFlow()
) {
    val scope = rememberCoroutineScope()
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

    when (dashboardType) {
        "web" -> {
            WebDashView(
                modifier = Modifier.fillMaxSize(),
                url = dashboardUrl,
                scope = scope,
                dashStateFlow = dashStateFlow,
                speedCameraFlow = speedCameraFlow
            )
        }
        "rive", "dev_rive" -> {
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
