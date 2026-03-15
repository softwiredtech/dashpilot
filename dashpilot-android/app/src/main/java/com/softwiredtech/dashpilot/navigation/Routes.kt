package com.softwiredtech.dashpilot.navigation

import kotlinx.serialization.Serializable

@Serializable
object SetupRoute

@Serializable
object DashboardSelectionRoute

@Serializable
data class DashboardRoute(
    val dashboardType: String,
    val dashboardUrl: String
)
