package com.softwiredtech.dashpilot.datamodel

import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ui.LOCAL_ASSET_BASE_URL

enum class DashboardType { WEB, RIVE, DEV_RIVE }

data class DashboardConfig(
    val name: String,
    val url: String,
    val type: DashboardType,
    val screenshotRes: Int = 0
)

val availableDashboards = buildList {
    add(
        DashboardConfig(
            name = "Vanilla",
            url = "${LOCAL_ASSET_BASE_URL}web-vanilla/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_vanilla
        )
    )
    add(
        DashboardConfig(
            name = "Expo",
            url = "https://dashpilot-expo.web.app",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_expo
        )
    )
    add(
        DashboardConfig(
            name = "Rive",
            url = "dashboard_test",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_rive
        )
    )

    if (BuildConfig.DEBUG) {
        add(
            DashboardConfig(
                name = "Load Rive File",
                url = "",
                type = DashboardType.DEV_RIVE
            )
        )
    }
}
