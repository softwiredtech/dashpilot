package com.softwiredtech.dashpilot.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object SetupRoute

@Serializable
object SettingsRoute

@Serializable
object ThemePickerRoute

@Serializable
object AutomationsRoute

@Serializable
object ControlsRoute

@Serializable
data class DashboardRoute(
    val dashboardType: String,
    val dashboardUrl: String
)
