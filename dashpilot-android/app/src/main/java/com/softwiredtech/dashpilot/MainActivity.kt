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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import com.softwiredtech.dashpilot.navigation.DashboardPreviewRoute
import com.softwiredtech.dashpilot.ui.DashboardPreviewScreen
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import com.softwiredtech.dashpilot.ui.theme.AccentColor
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
import com.softwiredtech.dashpilot.ble.TeslaStatusSource
import com.softwiredtech.dashpilot.datamodel.dash.DashboardType
import com.softwiredtech.dashpilot.datamodel.dash.ManifestLoader
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.datamodel.dash.getSelectedDashboard
import com.softwiredtech.dashpilot.datamodel.dash.setOnboardingCompleted
import com.softwiredtech.dashpilot.datamodel.dash.setLoadedManifests
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.navigation.AutomationsRoute
import com.softwiredtech.dashpilot.navigation.BatteryRoute
import com.softwiredtech.dashpilot.navigation.ClimateRoute
import com.softwiredtech.dashpilot.navigation.ControlsRoute
import com.softwiredtech.dashpilot.navigation.DashboardRoute
import com.softwiredtech.dashpilot.navigation.OnboardingRoute
import com.softwiredtech.dashpilot.navigation.SettingsRoute
import com.softwiredtech.dashpilot.navigation.SetupRoute
import com.softwiredtech.dashpilot.navigation.TeslaEnrollRoute
import com.softwiredtech.dashpilot.navigation.ThemePickerRoute
import com.softwiredtech.dashpilot.ui.AutomationsScreen
import com.softwiredtech.dashpilot.ui.BatteryScreen
import com.softwiredtech.dashpilot.ui.ClimateScreen
import com.softwiredtech.dashpilot.ui.ControlScreen
import com.softwiredtech.dashpilot.ui.DashboardScreen
import com.softwiredtech.dashpilot.ui.HomeScreen
import com.softwiredtech.dashpilot.ui.LOCAL_ASSET_BASE_URL
import com.softwiredtech.dashpilot.ui.SettingsScreen
import com.softwiredtech.dashpilot.ui.ThemePickerScreen
import com.softwiredtech.dashpilot.ui.onboarding.OnboardingScreen
import com.softwiredtech.dashpilot.ui.tesla.TeslaEnrollFlow
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
        val riveIds = availableDashboards.filter { it.type == DashboardType.RIVE }.map { it.id }
        setLoadedManifests(
            ManifestLoader.loadFromAssets(applicationContext, webIds, "web") +
                ManifestLoader.loadFromAssets(applicationContext, riveIds, "rive", optional = true)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadDashboardManifests()

        connectionVM.blePermissionGate = { onGranted -> ensureBluetoothPermission(onGranted) }

        connectionVM.loadPinnedControl(this)
        connectionVM.loadAutomations(this)
        connectionVM.loadAutoStartDrive(this)

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
                val autoStartDrive by connectionVM.autoStartDrive.collectAsState()
                val driveStateFlow by connectionVM.dashState.collectAsState()
                val gearFlow = remember(driveStateFlow) {
                    driveStateFlow?.map { it.carState.gear }?.distinctUntilChanged() ?: flowOf(null)
                }
                val currentGear by gearFlow.collectAsState(initial = null)
                val activityState by lifecycle.currentStateFlow.collectAsState()
                val pinnedControl by connectionVM.pinnedControl.collectAsState()
                val wiperOffAutomation by connectionVM.wiperOffAutomation.collectAsState()
                val carModel by connectionVM.carModel.collectAsState()
                val climateKeepAutomation by connectionVM.climateKeepAutomation.collectAsState()
                val climateKeepMinutes by connectionVM.climateKeepMinutes.collectAsState()
                val sportKickdownAutomation by connectionVM.sportKickdownAutomation.collectAsState()
                val sportKickdownPercent by connectionVM.sportKickdownPercent.collectAsState()
                val acSwing by connectionVM.acSwing.collectAsState()
                val fingerActions by connectionVM.fingerActions.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val onDashboard = navBackStackEntry?.destination?.let {
                    it.hasRoute<DashboardRoute>() || it.hasRoute<DashboardPreviewRoute>()
                } == true

                LaunchedEffect(autoStartDrive, connectionStatus, currentGear, activityState, navBackStackEntry) {
                    val destination = navController.currentDestination
                    val canNavigate = activityState.isAtLeast(Lifecycle.State.RESUMED) &&
                        destination != null && !destination.hasRoute<OnboardingRoute>() &&
                        !destination.hasRoute<TeslaEnrollRoute>()
                    if (connectionVM.autoStartDriveGate.shouldStart(
                            enabled = autoStartDrive,
                            connected = connectionStatus == ConnectionStatus.Connected,
                            gear = currentGear,
                            canNavigate = canNavigate,
                            dashboardVisible = onDashboard
                        )) {
                        val dashboard = getSelectedDashboard(context)
                        navController.navigate(DashboardRoute(dashboard.type.name.lowercase(), dashboard.url)) {
                            launchSingleTop = true
                        }
                    }
                }

                val bleManager by connectionVM.bleManager.collectAsState()
                val dashkitUpdateManager = remember(bleManager) {
                    bleManager?.let { FirmwareUpdateManager(it) }
                }
                DisposableEffect(dashkitUpdateManager) {
                    onDispose { dashkitUpdateManager?.dispose() }
                }

                // Phase 4: Tesla status/command channel rides the same bond.
                val teslaSource = remember(bleManager) { bleManager?.let { TeslaStatusSource(it) } }
                val teslaStatus = teslaSource?.status
                val teslaResetPending = teslaSource?.resetPending
                LaunchedEffect(teslaSource) { teslaSource?.start() }
                DisposableEffect(teslaSource) { onDispose { teslaSource?.stop() } }

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

                val destination = navBackStackEntry?.destination
                val onDrive = destination?.hasRoute<SetupRoute>() == true
                val onCommands = destination?.hasRoute<ControlsRoute>() == true
                val onAutomations = destination?.hasRoute<AutomationsRoute>() == true
                val onSettings = destination?.hasRoute<SettingsRoute>() == true
                val showTabs = onDrive || onCommands || onAutomations || onSettings

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkColors.Background,
                    bottomBar = {
                        if (showTabs) {
                            NavigationBar(containerColor = DarkColors.Surface) {
                                val labels = listOf(R.string.home_drive, R.string.navigation_commands, R.string.home_automations, R.string.settings_title)
                                val icons = listOf(Icons.Rounded.DirectionsCar, Icons.Rounded.Tune, Icons.Rounded.AutoAwesome, Icons.Rounded.Settings)
                                val selected = listOf(onDrive, onCommands, onAutomations, onSettings)
                                labels.forEachIndexed { index, label ->
                                    NavigationBarItem(
                                        selected = selected[index],
                                        onClick = {
                                            if (!selected[index]) {
                                                val options: androidx.navigation.NavOptionsBuilder.() -> Unit = {
                                                    popUpTo<SetupRoute> { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                                when (index) {
                                                    0 -> navController.navigate(SetupRoute, options)
                                                    1 -> navController.navigate(ControlsRoute, options)
                                                    2 -> navController.navigate(AutomationsRoute, options)
                                                    3 -> navController.navigate(SettingsRoute, options)
                                                }
                                            }
                                        },
                                        icon = { Icon(icons[index], contentDescription = null) },
                                        label = { Text(stringResource(label), maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AccentColor,
                                            selectedTextColor = AccentColor,
                                            indicatorColor = AccentColor.copy(alpha = 0.14f),
                                            unselectedIconColor = DarkColors.TextMuted,
                                            unselectedTextColor = DarkColors.TextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        modifier = if (showTabs) Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding())) else Modifier,
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
                                    connectionVM.disconnect(context)
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
                            val activeSourceType by connectionVM.activeSourceType.collectAsState()
                            val toDashboard = {
                                val dashboard = getSelectedDashboard(context)
                                navController.navigate(DashboardRoute(
                                    dashboard.type.name.lowercase(),
                                    dashboard.url)
                                )
                            }
                            HomeScreen(
                                connectionStatus = connectionStatus,
                                activeSourceType = activeSourceType,
                                bleManager = manager,
                                dashState = dashStateFlow,
                                pinnedControlId = pinnedControl,
                                teslaStatus = teslaStatus,
                                teslaResetPending = teslaResetPending,
                                onEnrollTesla = { navController.navigate(TeslaEnrollRoute) },
                                onConnect = { serverAddress, dataSourceType ->
                                    connectionVM.connect(
                                        context, serverAddress, dataSourceType,
                                        userInitiated = true
                                    )
                                },
                                onDisconnect = {
                                    connectionVM.disconnect(context)
                                },
                                onNext = toDashboard,
                                onBattery = {
                                    navController.navigate(BatteryRoute)
                                },
                                onClimate = {
                                    navController.navigate(ClimateRoute)
                                },
                                onDrive = toDashboard,
                                onSettingsClick = {
                                    navController.navigate(SettingsRoute)
                                }
                            )
                        }
                        composable<BatteryRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            val dashStateFlow by connectionVM.dashState.collectAsState()
                            BatteryScreen(
                                dashState = dashStateFlow,
                                bleManager = manager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<ClimateRoute> {
                            val dashStateFlow by connectionVM.dashState.collectAsState()
                            ClimateScreen(
                                dashState = dashStateFlow,
                                climateKeepEnabled = climateKeepAutomation,
                                onClimateKeepChange = {
                                    connectionVM.updateClimateKeepAutomation(context, it)
                                },
                                climateKeepMinutes = climateKeepMinutes,
                                onClimateKeepMinutesChange = {
                                    connectionVM.updateClimateKeepMinutes(context, it)
                                },
                                acSwing = acSwing,
                                onAcSwingChange = { connectionVM.updateAcSwing(context, it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<ControlsRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            ControlScreen(
                                isRoot = true,
                                bleManager = manager,
                                pinnedControlId = pinnedControl,
                                onTogglePin = { connectionVM.togglePinnedControl(context, it) },
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
                        composable<AutomationsRoute> {
                            AutomationsScreen(
                                isRoot = true,
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
                                acSwing = acSwing,
                                onAcSwingChange = { connectionVM.updateAcSwing(context, it) },
                                sportKickdownEnabled = sportKickdownAutomation,
                                onSportKickdownChange = {
                                    connectionVM.updateSportKickdownAutomation(context, it)
                                },
                                sportKickdownPercent = sportKickdownPercent,
                                onSportKickdownPercentChange = {
                                    connectionVM.updateSportKickdownPercent(context, it)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<SettingsRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            SettingsScreen(
                                isRoot = true,
                                autoStartDrive = autoStartDrive,
                                onAutoStartDriveChange = { connectionVM.updateAutoStartDrive(context, it) },
                                onBack = { navController.popBackStack() },
                                onDisplaySettingsChanged = { connectionVM.updateDisplaySettings(it) },
                                bleManager = manager,
                                dashkitUpdateManager = dashkitUpdateManager,
                                teslaStatus = teslaStatus,
                                teslaResetPending = teslaResetPending,
                                onRemoveTesla = { teslaSource?.requestReset() == true },
                                onEnrollTesla = { navController.navigate(TeslaEnrollRoute) },
                                onReplayOnboarding = {
                                    connectionVM.disconnect(context)
                                    setOnboardingCompleted(context, false)
                                    navController.navigate(OnboardingRoute) {
                                        popUpTo(SetupRoute) { inclusive = false }
                                    }
                                },
                                onPreviewDashboard = { dashboard ->
                                    navController.navigate(DashboardPreviewRoute(dashboard.type.name.lowercase(), dashboard.url))
                                },
                                carModel = carModel,
                                onCarModelChanged = { connectionVM.updateCarModel(context, it) }
                            )
                        }
                        composable<TeslaEnrollRoute> {
                            val manager by connectionVM.bleManager.collectAsState()
                            TeslaEnrollFlow(
                                manager = manager,
                                statusFlow = teslaStatus,
                                vinState = connectionVM.vehicleVin,
                                onClose = { navController.popBackStack() },
                            )
                        }
                        composable<ThemePickerRoute> {
                            ThemePickerScreen(
                                onPreview = { dashboard ->
                                    navController.navigate(DashboardPreviewRoute(dashboard.type.name.lowercase(), dashboard.url))
                                },
                                onBack = { navController.popBackStack(ThemePickerRoute, inclusive = true) }
                            )
                        }
                        composable<DashboardPreviewRoute> { entry ->
                            val route = entry.toRoute<DashboardPreviewRoute>()
                            DashboardPreviewScreen(route.dashboardType, route.dashboardUrl) {
                                navController.popBackStack()
                            }
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
