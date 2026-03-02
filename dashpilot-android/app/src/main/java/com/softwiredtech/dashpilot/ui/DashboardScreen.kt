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
import com.softwiredtech.dashpilot.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    dashboardType: String,
    serverAddress: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataSource = remember {
        CommaDataSource(FileUtils.copyAssetToFile(context, "tesla_model3_party.dbc"))
    }

    LaunchedEffect(serverAddress) {
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
