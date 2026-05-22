package com.softwiredtech.dashpilot

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.startup.AppInitializer
import app.rive.runtime.kotlin.RiveInitializer
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.navigation.DashboardRoute
import com.softwiredtech.dashpilot.navigation.SettingsRoute
import com.softwiredtech.dashpilot.navigation.SetupRoute
import com.softwiredtech.dashpilot.ui.DashboardScreen
import com.softwiredtech.dashpilot.ui.SettingsScreen
import com.softwiredtech.dashpilot.ui.SetupScreen
import com.softwiredtech.dashpilot.ui.theme.DashPilotTheme
import com.softwiredtech.dashpilot.util.NetworkUtil
import com.softwiredtech.dashpilot.viewmodel.ConnectionViewModel
import com.softwiredtech.dashpilot.viewmodel.SpeedCameraViewModel

class MainActivity : ComponentActivity() {
    private val networkUtil by lazy { NetworkUtil(applicationContext) }
    private val connectionVM: ConnectionViewModel by viewModels {
        viewModelFactory {
            initializer { ConnectionViewModel(networkUtil) }
        }
    }

    private val speedCameraVM: SpeedCameraViewModel by viewModels()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.any { it.value }
        if (granted) {
            speedCameraVM.startUpdating(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionVM.bindSpeedCamera(speedCameraVM.nearestApproachingCamera)

        if (hasLocationPermission()) {
            speedCameraVM.startUpdating(this)
        } else {
            requestLocationPermission()
        }

        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
        }

        AppInitializer.getInstance(applicationContext)
            .initializeComponent(RiveInitializer::class.java)

        // Enable edge-to-edge for Compose
        enableEdgeToEdge()

        // Start comma discovery as soon as we launch the app.
        if (!BuildConfig.DEBUG) {
            connectionVM.connect(this, "", DataSourceType.COMMA)
        }

        setContent {
            DashPilotTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val connectionStatus by connectionVM.connectionStatus.collectAsState()
                val hasAutoNavigated by connectionVM.hasAutoNavigatedToDashboard.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val onDashboard = navBackStackEntry?.destination?.route
                    ?.contains("DashboardRoute") == true

                // Auto-open dashboard right after connection establishment
                LaunchedEffect(connectionStatus, hasAutoNavigated) {
                    if (connectionStatus is ConnectionStatus.Connected && !hasAutoNavigated) {
                        connectionVM.markAutoNavigated()
                        val dashboard = getSelectedDashboard(context)
                        navController.navigate(DashboardRoute(
                            dashboard.type.name.lowercase(),
                            dashboard.url)
                        )
                    }
                }

                LaunchedEffect(onDashboard) {
                    if (onDashboard) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        hideSystemBars()
                    } else {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        showSystemBars()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    NavHost(
                        navController = navController,
                        startDestination = SetupRoute,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable<SetupRoute> {
                            SetupScreen(
                                connectionStatus = connectionStatus,
                                onConnect = { serverAddress, dataSourceType ->
                                    connectionVM.connect(context, serverAddress, dataSourceType)
                                },
                                onDisconnect = {
                                    connectionVM.disconnect()
                                },
                                onNext = {
                                    val dashboard = getSelectedDashboard(context)
                                    navController.navigate(DashboardRoute(
                                        dashboard.type.name.lowercase(),
                                        dashboard.url)
                                    )
                                },
                                onSettingsClick = {
                                    navController.navigate(SettingsRoute)
                                },
                                onSwcLeftScrollDown = { connectionVM.sendSwcLeftScroll(-1) }
                            )
                        }
                        composable<SettingsRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onDisplaySettingsChanged = { connectionVM.updateDisplaySettings(it) },
                                bleManager = manager
                            )
                        }
                        composable<DashboardRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<DashboardRoute>()
                            val dashStateFlow by connectionVM.dashState.collectAsState()
                            val flow = dashStateFlow
                            if (flow != null) {
                                DashboardScreen(
                                    dashboardType = route.dashboardType,
                                    dashboardUrl = route.dashboardUrl,
                                    dashStateFlow = flow
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (needed.isNotEmpty()) {
                bluetoothPermissionLauncher.launch(needed)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            locationPermissionLauncher.launch(needed)
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

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
