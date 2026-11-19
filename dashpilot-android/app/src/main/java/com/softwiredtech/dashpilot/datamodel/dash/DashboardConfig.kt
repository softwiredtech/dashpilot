package com.softwiredtech.dashpilot.datamodel.dash

import androidx.annotation.StringRes
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
            id = "vanilla-v2",
            nameRes = R.string.dashboard_name_vanilla_v2,
            url = "${LOCAL_ASSET_BASE_URL}web-vanilla-v2/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_vanilla_v2
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
            id = "compose",
            nameRes = R.string.dashboard_name_compose,
            url = "${LOCAL_ASSET_BASE_URL}web-compose/index.html",
            type = DashboardType.WEB,
            screenshotRes = R.drawable.preview_compose
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
            id = "rive",
            nameRes = R.string.dashboard_name_rive,
            url = "dash_demo",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_rive
        )
    )
    add(
        DashboardConfig(
            id = "felina",
            nameRes = R.string.dashboard_name_felina,
            url = "felina",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_felina
        )
    )
    add(
        DashboardConfig(
            id = "halo",
            nameRes = R.string.dashboard_name_halo,
            url = "halo",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_halo
        )
    )
    add(
        DashboardConfig(
            id = "monte-carlo",
            nameRes = R.string.dashboard_name_monte_carlo,
            url = "monte_carlo",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_monte_carlo
        )
    )
    add(
        DashboardConfig(
            id = "parabellum",
            nameRes = R.string.dashboard_name_parabellum,
            url = "parabellum",
            type = DashboardType.RIVE,
            screenshotRes = R.drawable.preview_parabellum
        )
    )
}

const val DEV_RIVE_DASHBOARD_ID = "dev_rive"
const val DEV_URL_DASHBOARD_ID = "dev_url"

// Selectable only from the Developer settings; their url is filled in from prefs.
val developerDashboards = listOf(
    DashboardConfig(
        id = DEV_RIVE_DASHBOARD_ID,
        nameRes = R.string.dashboard_name_dev_rive,
        url = "",
        type = DashboardType.DEV_RIVE
    ),
    DashboardConfig(
        id = DEV_URL_DASHBOARD_ID,
        nameRes = R.string.dashboard_name_dev_url,
        url = "",
        type = DashboardType.WEB
    ),
)

// Mutable, written once at app startup by MainActivity via setLoadedManifests().
// Reads happen via DashboardConfig.withManifest() and dashboardById().
@Volatile
private var manifestsById: Map<String, DashboardManifest> = emptyMap()

internal fun setLoadedManifests(map: Map<String, DashboardManifest>) {
    manifestsById = map
}

fun DashboardConfig.withManifest(): DashboardConfig =
    if (type == DashboardType.DEV_RIVE) this else copy(manifest = manifestsById[id])

fun riveDashboardRequiresGpuCanvas(url: String): Boolean {
    val id = availableDashboards.firstOrNull { it.type == DashboardType.RIVE && it.url == url }?.id
    return manifestsById[id]?.requires?.contains(ManifestRequirement.GPU_CANVAS) == true
}
