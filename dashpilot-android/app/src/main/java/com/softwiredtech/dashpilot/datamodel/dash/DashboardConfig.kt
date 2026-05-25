package com.softwiredtech.dashpilot.datamodel.dash

import androidx.annotation.StringRes
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ui.LOCAL_ASSET_BASE_URL
import kotlinx.serialization.Serializable

@Serializable
enum class DashboardType { WEB, RIVE, DEV_RIVE }

data class DashboardConfig(
    val id: String,
    @StringRes val nameRes: Int,
    val url: String,
    val type: DashboardType,
    val screenshotRes: Int = 0,
    val manifest: DashboardManifest? = null,
)

val availableDashboards = buildList {
    add(
        DashboardConfig(
            id = "vanilla",
            nameRes = R.string.dashboard_name_vanilla,
            url = "${LOCAL_ASSET_BASE_URL}web-vanilla/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_vanilla,
        )
    )
    add(
        DashboardConfig(
            id = "ambient",
            nameRes = R.string.dashboard_name_ambient,
            url = "${LOCAL_ASSET_BASE_URL}web-ambient/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_ambient
        )
    )
    add(
        DashboardConfig(
            id = "analog",
            nameRes = R.string.dashboard_name_analog,
            url = "${LOCAL_ASSET_BASE_URL}web-analog/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_analog
        )
    )
    add(
        DashboardConfig(
            id = "retro",
            nameRes = R.string.dashboard_name_retro,
            url = "${LOCAL_ASSET_BASE_URL}web-retro/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_retro
        )
    )
    add(
        DashboardConfig(
            id = "expo",
            nameRes = R.string.dashboard_name_expo,
            url = "https://dashpilot-expo.web.app",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_expo
        )
    )
    add(
        DashboardConfig(
            id = "rive",
            nameRes = R.string.dashboard_name_rive,
            url = "dashboard_test",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_rive
        )
    )

    if (BuildConfig.DEBUG) {
        add(
            DashboardConfig(
                id = "dev_rive",
                nameRes = R.string.dashboard_name_dev_rive,
                url = "",
                type = DashboardType.DEV_RIVE
            )
        )
    }
}

// Mutable, written once at app startup by MainActivity via setLoadedManifests().
// Reads happen via DashboardConfig.withManifest() and dashboardById().
@Volatile
private var manifestsById: Map<String, DashboardManifest> = emptyMap()

internal fun setLoadedManifests(map: Map<String, DashboardManifest>) {
    manifestsById = map
}

/**
 * Returns a copy of this DashboardConfig with its manifest field populated
 * from the loaded manifest store. WEB dashboards may have a non-null manifest;
 * other types always return the receiver unchanged (manifest stays null).
 */
fun DashboardConfig.withManifest(): DashboardConfig =
    if (type == DashboardType.WEB) copy(manifest = manifestsById[id]) else this
