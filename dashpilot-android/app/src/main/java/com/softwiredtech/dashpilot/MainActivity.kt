package com.softwiredtech.dashpilot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.startup.AppInitializer
import app.rive.runtime.kotlin.RiveInitializer
import com.softwiredtech.dashpilot.datamodel.DashboardType
import com.softwiredtech.dashpilot.navigation.DashboardRoute
import com.softwiredtech.dashpilot.navigation.DashboardSelectionRoute
import com.softwiredtech.dashpilot.navigation.SetupRoute
import com.softwiredtech.dashpilot.ui.DashboardScreen
import com.softwiredtech.dashpilot.ui.DashboardSelectionScreen
import com.softwiredtech.dashpilot.ui.SetupScreen
import com.softwiredtech.dashpilot.ui.theme.DashPilotTheme

class MainActivity : ComponentActivity() {

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — BLE will fail gracefully if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothPermissions()

        AppInitializer.getInstance(applicationContext)
            .initializeComponent(RiveInitializer::class.java)

        // Enable edge-to-edge for Compose
        enableEdgeToEdge()
        hideSystemBars()

        setContent {
            DashPilotTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    NavHost(
                        navController = navController,
                        startDestination = SetupRoute
                    ) {
                        composable<SetupRoute> {
                            SetupScreen(
                                onLaunch = { serverAddress, dataSourceType ->
                                    navController.navigate(
                                        DashboardSelectionRoute(serverAddress, dataSourceType)
                                    )
                                }
                            )
                        }
                        composable<DashboardSelectionRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<DashboardSelectionRoute>()
                            DashboardSelectionScreen(
                                onSelect = { dashboard ->
                                    navController.navigate(
                                        DashboardRoute(
                                            dashboardType = dashboard.type.name.lowercase(),
                                            dashboardUrl = dashboard.url,
                                            serverAddress = route.serverAddress,
                                            dataSourceType = route.dataSourceType
                                        )
                                    )
                                }
                            )
                        }
                        composable<DashboardRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<DashboardRoute>()
                            DashboardScreen(
                                dashboardType = route.dashboardType,
                                dashboardUrl = route.dashboardUrl,
                                serverAddress = route.serverAddress,
                                dataSourceType = route.dataSourceType
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(needed)
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // fallback for pre-R devices
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Re-apply when focus changes
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }
}
