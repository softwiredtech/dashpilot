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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.datasource.CommaDataSource
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.PandaBleDataSource
import com.softwiredtech.dashpilot.jni.VehicleBridge
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import com.softwiredtech.dashpilot.vehicle.VehicleProfileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    dashboardType: String,
    dashboardUrl: String,
    serverAddress: String,
    dataSourceType: String = "comma"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val vehicleName = "tesla" // TODO: make configurable via UI

    val dataSource: IDataSource = remember(dataSourceType, vehicleName) {
        val profile = VehicleProfileLoader.loadProfile(context, vehicleName)
        val bridge = VehicleBridge()
        when (dataSourceType) {
            "ble" -> {
                val decoder = CanFrameDecoder(bridge, profile)
                PandaBleDataSource(context, decoder)
            }
            else -> CommaDataSource(bridge, profile)
        }
    }

    if (!isLandscape) {
        LaunchedEffect(Unit) {
            dataSource.disconnect()
        }

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
                text = "Rotate your phone to landscape",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LaunchedEffect(serverAddress, dataSourceType) {
        launch(Dispatchers.IO) {
            dataSource.connect(serverAddress)
        }
    }

    when (dashboardType) {
        "web" -> {
            WebDashView(
                modifier = Modifier.fillMaxSize(),
                url = dashboardUrl,
                scope = scope,
                datasource = dataSource
            )
        }
        "rive" -> {
            RiveDashView(
                assetName = dashboardUrl,
                dataSource = dataSource,
                scope = scope,
                onError = { message -> Log.d("Rive", message) }
            )
        }
    }
}
