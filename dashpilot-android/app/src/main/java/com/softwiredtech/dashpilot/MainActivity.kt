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
import com.softwiredtech.dashpilot.datamodel.dash.DashboardType
import com.softwiredtech.dashpilot.datamodel.dash.ManifestLoader
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.datamodel.dash.setOnboardingCompleted
import com.softwiredtech.dashpilot.datamodel.dash.setLoadedManifests
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.navigation.DashboardRoute
import com.softwiredtech.dashpilot.navigation.OnboardingRoute
import com.softwiredtech.dashpilot.navigation.SettingsRoute
import com.softwiredtech.dashpilot.navigation.SetupRoute
import com.softwiredtech.dashpilot.ui.DashboardScreen
import com.softwiredtech.dashpilot.ui.LOCAL_ASSET_BASE_URL
import com.softwiredtech.dashpilot.ui.SettingsScreen
import com.softwiredtech.dashpilot.ui.SetupScreen
import com.softwiredtech.dashpilot.ui.onboarding.OnboardingScreen
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
    ) { _ ->
        // Kick off the startup DashKit scan once the user has responded to the
        // permission prompt (granted or not — runStartupDiscovery handles both).
        connectionVM.runStartupDiscovery(this, hasBluetoothPermission())
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.any { it.value }
        if (granted) {
            speedCameraVM.startUpdating(this)
        }
    }

    private fun loadDashboardManifests() {
        val webIds = availableDashboards
            .filter { it.type == DashboardType.WEB && it.url.startsWith(LOCAL_ASSET_BASE_URL) }
            .map { it.id }
        setLoadedManifests(ManifestLoader.loadFromAssets(applicationContext, webIds))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadDashboardManifests()

        connectionVM.bindSpeedCamera(speedCameraVM.nearestApproachingCamera)

        if (hasLocationPermission()) {
            speedCameraVM.startUpdating(this)
        } else {
            requestLocationPermission()
        }

        // Decide the launch route by briefly scanning for an advertising DashKit.
        // If we don't yet have BLE permission, the scan is kicked off from the
        // permission launcher callback instead.
        if (hasBluetoothPermission()) {
            connectionVM.runStartupDiscovery(this, true)
        } else {
            requestBluetoothPermission()
        }

        AppInitializer.getInstance(applicationContext)
            .initializeComponent(RiveInitializer::class.java)

        // Enable edge-to-edge for Compose
        enableEdgeToEdge()

        setContent {
            DashPilotTheme {
                val startupTarget by connectionVM.startupTarget.collectAsState()

                val navController = rememberNavController()
                val context = LocalContext.current
                val connectionStatus by connectionVM.connectionStatus.collectAsState()
                val hasAutoNavigated by connectionVM.hasAutoNavigatedToDashboard.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val onDashboard = navBackStackEntry?.destination?.route
                    ?.contains("DashboardRoute") == true
                val onOnboarding = navBackStackEntry?.destination?.route
                    ?.contains("OnboardingRoute") == true

                // One-shot routing once the startup DashKit scan resolves. The UI
                // shows the normal Setup screen (disconnected/connecting) until then.
                LaunchedEffect(startupTarget) {
                    when (startupTarget) {
                        ConnectionViewModel.StartupTarget.ONBOARDING_DASHKIT ->
                            navController.navigate(OnboardingRoute) {
                                popUpTo(SetupRoute) { inclusive = true }
                            }
                        ConnectionViewModel.StartupTarget.AUTOCONNECT_DASHKIT ->
                            connectionVM.connect(context, "", DataSourceType.DASHKIT)
                        ConnectionViewModel.StartupTarget.DEFAULT ->
                            if (!BuildConfig.DEBUG) {
                                connectionVM.connect(context, "", DataSourceType.COMMA)
                            }
                        ConnectionViewModel.StartupTarget.LOADING -> Unit
                    }
                }

                LaunchedEffect(connectionStatus, hasAutoNavigated, onOnboarding) {
                    if (connectionStatus is ConnectionStatus.Connected && !hasAutoNavigated && !onOnboarding) {
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
                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                connectionVM = connectionVM,
                                onFinish = {
                                    setOnboardingCompleted(context, true)
                                    navController.navigate(SetupRoute) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                    }
                                },
                                onSkip = {
                                    connectionVM.disconnect()
                                    setOnboardingCompleted(context, true)
                                    navController.navigate(SetupRoute) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<SetupRoute> {
                            SetupScreen(
                                connectionStatus = connectionStatus,
                                preselectDashKit = startupTarget ==
                                        ConnectionViewModel.StartupTarget.AUTOCONNECT_DASHKIT ||
                                        startupTarget ==
                                        ConnectionViewModel.StartupTarget.ONBOARDING_DASHKIT,
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
                                }
                            )
                        }
                        composable<SettingsRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onDisplaySettingsChanged = { connectionVM.updateDisplaySettings(it) },
                                bleManager = manager,
                                onReplayOnboarding = {
                                    connectionVM.disconnect()
                                    setOnboardingCompleted(context, false)
                                    navController.navigate(OnboardingRoute) {
                                        popUpTo(SetupRoute) { inclusive = false }
                                    }
                                }
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
