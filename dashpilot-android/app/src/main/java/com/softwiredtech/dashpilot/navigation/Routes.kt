package com.softwiredtech.dashpilot.navigation

import kotlinx.serialization.Serializable

@Serializable
object SetupRoute

@Serializable
data class DashboardRoute(
    val dashboardType: String,
    val serverAddress: String
)
