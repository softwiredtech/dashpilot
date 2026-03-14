package com.softwiredtech.dashpilot.navigation

import kotlinx.serialization.Serializable

@Serializable
object SetupRoute

@Serializable
data class DashboardSelectionRoute(
    val serverAddress: String,
    val dataSourceType: String
)

@Serializable
data class DashboardRoute(
    val dashboardType: String,
    val dashboardUrl: String,
    val serverAddress: String,
    val dataSourceType: String = "comma"
)
