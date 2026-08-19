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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.softwiredtech.dashpilot.ble.FirmwareUpdateManager
import com.softwiredtech.dashpilot.datamodel.dash.DashboardType
import com.softwiredtech.dashpilot.datamodel.dash.ManifestLoader
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.datamodel.dash.setOnboardingCompleted
import com.softwiredtech.dashpilot.datamodel.dash.setLoadedManifests
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.navigation.AutomationsRoute
import com.softwiredtech.dashpilot.navigation.ControlsRoute
import com.softwiredtech.dashpilot.navigation.DashboardRoute
import com.softwiredtech.dashpilot.navigation.OnboardingRoute
import com.softwiredtech.dashpilot.navigation.SettingsRoute
import com.softwiredtech.dashpilot.navigation.SetupRoute
import com.softwiredtech.dashpilot.navigation.ThemePickerRoute
import com.softwiredtech.dashpilot.ui.AutomationsScreen
import com.softwiredtech.dashpilot.ui.ControlScreen
import com.softwiredtech.dashpilot.ui.DashboardScreen
import com.softwiredtech.dashpilot.ui.HomeScreen
import com.softwiredtech.dashpilot.ui.LOCAL_ASSET_BASE_URL
import com.softwiredtech.dashpilot.ui.SettingsScreen
import com.softwiredtech.dashpilot.ui.ThemePickerScreen
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

    private var pendingBleAction: (() -> Unit)? = null

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val granted = hasBluetoothPermission()
        connectionVM.runStartupDiscovery(this, granted)
        val action = pendingBleAction
        pendingBleAction = null
        if (granted) action?.invoke()
    }

    private fun ensureBluetoothPermission(onGranted: () -> Unit) {
        if (hasBluetoothPermission()) {
            onGranted()
        } else {
            pendingBleAction = onGranted
            requestBluetoothPermission()
        }
    }

    private val startupPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasLocationPermission()) {
            speedCameraVM.startUpdating(this)
        }
        connectionVM.runStartupDiscovery(this, hasBluetoothPermission())
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

        connectionVM.blePermissionGate = { onGranted -> ensureBluetoothPermission(onGranted) }

        connectionVM.loadPinnedControl(this)
        connectionVM.loadAutomations(this)

        connectionVM.bindSpeedCamera(speedCameraVM.nearestApproachingCamera)

        val bleGranted = hasBluetoothPermission()
        if (bleGranted) {
            connectionVM.runStartupDiscovery(this, true)
        }

        val startupPermissions = buildList {
            if (!hasLocationPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!bleGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (startupPermissions.isEmpty()) {
            if (hasLocationPermission()) {
                speedCameraVM.startUpdating(this)
            }
        } else {
            startupPermissionLauncher.launch(startupPermissions.toTypedArray())
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
                val pinnedControl by connectionVM.pinnedControl.collectAsState()
                val wiperOffAutomation by connectionVM.wiperOffAutomation.collectAsState()
                val climateKeepAutomation by connectionVM.climateKeepAutomation.collectAsState()
                val climateKeepMinutes by connectionVM.climateKeepMinutes.collectAsState()
                val fingerActions by connectionVM.fingerActions.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val onDashboard = navBackStackEntry?.destination?.route
                    ?.contains("DashboardRoute") == true

                val bleManager by connectionVM.bleManager.collectAsState()
                val dashkitUpdateManager = remember(bleManager) {
                    bleManager?.let { FirmwareUpdateManager(it) }
                }
                DisposableEffect(dashkitUpdateManager) {
                    onDispose { dashkitUpdateManager?.dispose() }
                }

                LaunchedEffect(startupTarget) {
                    when (startupTarget.route) {
                        ConnectionViewModel.StartupRoute.ONBOARDING_DASHKIT -> {
                            val onOnboarding = navController.currentDestination?.route
                                ?.contains("OnboardingRoute") == true
                            if (!onOnboarding) {
                                navController.navigate(OnboardingRoute) {
                                    popUpTo(SetupRoute) { inclusive = true }
                                }
                            }
                        }
                        ConnectionViewModel.StartupRoute.AUTOCONNECT_DASHKIT ->
                            connectionVM.connect(context, "", DataSourceType.DASHKIT)
                        ConnectionViewModel.StartupRoute.DEFAULT ->
                            if (!BuildConfig.DEBUG) {
                                connectionVM.connect(context, "", DataSourceType.COMMA)
                            }
                        ConnectionViewModel.StartupRoute.LOADING -> Unit
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
                            val manager by connectionVM.bleManager.collectAsState()
                            val dashStateFlow by connectionVM.dashState.collectAsState()
                            val toDashboard = {
                                val dashboard = getSelectedDashboard(context)
                                navController.navigate(DashboardRoute(
                                    dashboard.type.name.lowercase(),
                                    dashboard.url)
                                )
                            }
                            HomeScreen(
                                connectionStatus = connectionStatus,
                                bleManager = manager,
                                dashState = dashStateFlow,
                                pinnedControlId = pinnedControl,
                                onConnect = { serverAddress, dataSourceType ->
                                    connectionVM.connect(
                                        context, serverAddress, dataSourceType,
                                        userInitiated = true
                                    )
                                },
                                onDisconnect = {
                                    connectionVM.disconnect()
                                },
                                onNext = toDashboard,
                                onAutomations = {
                                    navController.navigate(AutomationsRoute)
                                },
                                onControls = {
                                    navController.navigate(ControlsRoute)
                                },
                                onDrive = toDashboard,
                                onSettingsClick = {
                                    navController.navigate(SettingsRoute)
                                }
                            )
                        }
                        composable<ControlsRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            ControlScreen(
                                bleManager = manager,
                                pinnedControlId = pinnedControl,
                                onTogglePin = { connectionVM.togglePinnedControl(context, it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AutomationsRoute> {
                            AutomationsScreen(
                                wiperOffEnabled = wiperOffAutomation,
                                onWiperOffChange = {
                                    connectionVM.updateWiperOffAutomation(context, it)
                                },
                                climateKeepEnabled = climateKeepAutomation,
                                onClimateKeepChange = {
                                    connectionVM.updateClimateKeepAutomation(context, it)
                                },
                                climateKeepMinutes = climateKeepMinutes,
                                onClimateKeepMinutesChange = {
                                    connectionVM.updateClimateKeepMinutes(context, it)
                                },
                                fingerActions = fingerActions,
                                onSetFingerAction = { fingers, id ->
                                    connectionVM.setFingerAction(context, fingers, id)
                                },
                                onChangeFingerCount = { from, to ->
                                    connectionVM.changeFingerCount(context, from, to)
                                },
                                onRemoveFingerAction = { fingers ->
                                    connectionVM.removeFingerAction(context, fingers)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<SettingsRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onDisplaySettingsChanged = { connectionVM.updateDisplaySettings(it) },
                                bleManager = manager,
                                dashkitUpdateManager = dashkitUpdateManager,
                                onReplayOnboarding = {
                                    connectionVM.disconnect()
                                    setOnboardingCompleted(context, false)
                                    navController.navigate(OnboardingRoute) {
                                        popUpTo(SetupRoute) { inclusive = false }
                                    }
                                },
                                onThemeClick = {
                                    navController.navigate(ThemePickerRoute)
                                }
                            )
                        }
                        composable<ThemePickerRoute> {
                            ThemePickerScreen(
                                onBack = { navController.popBackStack(ThemePickerRoute, inclusive = true) }
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

    override fun onStart() {
        super.onStart()
        connectionVM.onAppForegrounded(this, hasBluetoothPermission())
    }

    override fun onStop() {
        super.onStop()
        connectionVM.onAppBackgrounded()
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
