package com.softwiredtech.dashpilot.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    serverAddress: String,
    dataSourceType: String = "comma"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    LaunchedEffect(serverAddress, dataSourceType) {
        launch(Dispatchers.IO) {
            dataSource.connect(serverAddress)
        }
    }

    when (dashboardType) {
        "web" -> {
            val url = "http://$serverAddress:3000"
            WebDashView(
                modifier = Modifier.fillMaxSize(),
                url = url,
                scope = scope,
                datasource = dataSource
            )
        }
        "rive" -> {
            RiveDashView(
                dataSource = dataSource,
                scope = scope,
                onError = { message -> Log.d("Rive", message) }
            )
        }
    }
}
