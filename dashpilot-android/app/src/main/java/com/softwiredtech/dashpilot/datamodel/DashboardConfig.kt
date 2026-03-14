package com.softwiredtech.dashpilot.datamodel

import com.softwiredtech.dashpilot.R

enum class DashboardType { WEB, RIVE }

data class DashboardConfig(
    val name: String,
    val url: String,
    val type: DashboardType,
    val screenshotRes: Int
)

val availableDashboards = listOf(
    DashboardConfig(
        name = "Expo Dashboard",
        url = "https://dashpilot-expo.web.app",
        type = DashboardType.WEB,
        screenshotRes = R.drawable.preview_expo
    ),
    DashboardConfig(
        name = "Vanilla Dashboard",
        url = "https://dashpilot-vanilla.web.app",
        type = DashboardType.WEB,
        screenshotRes = R.drawable.preview_vanilla
    ),
    DashboardConfig(
        name = "Rive Dashboard",
        url = "dashboard_test",
        type = DashboardType.RIVE,
        screenshotRes = R.drawable.preview_rive
    ),
)
