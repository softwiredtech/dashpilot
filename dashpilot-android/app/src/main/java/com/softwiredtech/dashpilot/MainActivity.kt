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
import com.softwiredtech.dashpilot.navigation.AutomationsRoute
import com.softwiredtech.dashpilot.navigation.ControlsRoute
import com.softwiredtech.dashpilot.navigation.DashboardRoute
import com.softwiredtech.dashpilot.navigation.OnboardingRoute
import com.softwiredtech.dashpilot.navigation.SettingsRoute
import com.softwiredtech.dashpilot.navigation.SetupRoute
import com.softwiredtech.dashpilot.ui.AutomationsScreen
import com.softwiredtech.dashpilot.ui.ControlScreen
import com.softwiredtech.dashpilot.ui.DashboardScreen
import com.softwiredtech.dashpilot.ui.HomeScreen
import com.softwiredtech.dashpilot.ui.LOCAL_ASSET_BASE_URL
import com.softwiredtech.dashpilot.ui.SettingsScreen
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

    // A BLE connection attempt that arrived before we held permission. Run once
    // the user grants the prompt (see bluetoothPermissionLauncher).
    private var pendingBleAction: (() -> Unit)? = null

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val granted = hasBluetoothPermission()
        // Kick off the startup DashKit scan once the user has responded to the
        // permission prompt (granted or not — runStartupDiscovery handles both).
        connectionVM.runStartupDiscovery(this, granted)
        // Resume any connection that was waiting on permission, but only if the
        // user actually granted it.
        val action = pendingBleAction
        pendingBleAction = null
        if (granted) action?.invoke()
    }

    // Ensures BLE permission is held before running a BLE action, requesting it
    // first if needed. Wired into ConnectionViewModel so every connect path is
    // gated. onGranted runs immediately when permission is already held, or from
    // the permission-launcher callback once the user grants it.
    private fun ensureBluetoothPermission(onGranted: () -> Unit) {
        if (hasBluetoothPermission()) {
            onGranted()
        } else {
            pendingBleAction = onGranted
            requestBluetoothPermission()
        }
    }

    // Startup permissions — location (for speed cameras) and BLE (for DashKit) —
    // are requested together in one flow. Android only surfaces one permission
    // dialog at a time, so launching them from separate launchers would drop all
    // but the first (which is why the BLE prompt never appeared).
    private val startupPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasLocationPermission()) {
            speedCameraVM.startUpdating(this)
        }
        // Kick off the startup DashKit scan once the user has responded
        // (granted or not — runStartupDiscovery handles both).
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

        // Route every BLE connect through the runtime-permission gate so we
        // never start a scan/connect before the user has granted permission.
        connectionVM.blePermissionGate = { onGranted -> ensureBluetoothPermission(onGranted) }

        connectionVM.loadPinnedControl(this)
        connectionVM.loadAutomations(this)

        connectionVM.bindSpeedCamera(speedCameraVM.nearestApproachingCamera)

        // Request every missing startup permission (location + BLE) in a single
        // prompt flow via startupPermissionLauncher. If BLE is already granted,
        // kick off DashKit discovery immediately so route selection isn't blocked
        // behind the location prompt.
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
                val fingerActions by connectionVM.fingerActions.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val onDashboard = navBackStackEntry?.destination?.route
                    ?.contains("DashboardRoute") == true

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
                                preselectDashKit = startupTarget ==
                                        ConnectionViewModel.StartupTarget.AUTOCONNECT_DASHKIT ||
                                        startupTarget ==
                                        ConnectionViewModel.StartupTarget.ONBOARDING_DASHKIT,
                                pinnedControlId = pinnedControl,
                                onConnect = { serverAddress, dataSourceType ->
                                    connectionVM.connect(context, serverAddress, dataSourceType)
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
