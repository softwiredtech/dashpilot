package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context

const val DASH_PREFS_NAME = "dash_prefs"
const val PREF_SELECTED_DASHBOARD_ID = "selected_dashboard_id"

// Display settings pref keys
const val PREF_SHOW_PHONE_BATTERY = "show_phone_battery"
const val PREF_SHOW_CAR_BATTERY = "show_car_battery"
const val PREF_SHOW_ODOMETER = "show_odometer"
const val PREF_USE_IMPERIAL = "use_imperial"
const val PREF_DARK_MODE = "dark_mode"
const val PREF_ALWAYS_ON_BLIND_SPOT_MONITOR = "always_on_blind_spot_monitor"
const val PREF_RENDER_QUALITY = "render_quality"
const val PREF_DARK_MODE_BACKGROUND_GRAY = "dark_mode_background_gray"
const val PREF_EXTRA_VEHICLE_BUS = "extra_vehicle_bus"

// Display settings defaults
const val DEFAULT_SHOW_PHONE_BATTERY = true
const val DEFAULT_SHOW_CAR_BATTERY = true
const val DEFAULT_SHOW_ODOMETER = true
const val DEFAULT_USE_IMPERIAL = false
const val DEFAULT_DARK_MODE = false
const val DEFAULT_ALWAYS_ON_BLIND_SPOT_MONITOR = true
const val DEFAULT_RENDER_QUALITY = 3
const val DEFAULT_DARK_MODE_BACKGROUND_GRAY = 0
const val DEFAULT_EXTRA_VEHICLE_BUS = false

private const val DEFAULT_DASHBOARD_ID = "vanilla"

fun defaultDashboard(): DashboardConfig {
    return dashboardById(DEFAULT_DASHBOARD_ID) ?: availableDashboards.first()
}

fun dashboardById(id: String?): DashboardConfig? {
    val normalizedId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return availableDashboards.firstOrNull { it.id == normalizedId }
}

fun getSelectedDashboard(context: Context): DashboardConfig {
    val prefs = context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
    val savedId = prefs.getString(PREF_SELECTED_DASHBOARD_ID, null)
    return dashboardById(savedId) ?: defaultDashboard()
}

fun saveSelectedDashboard(context: Context, dashboard: DashboardConfig) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SELECTED_DASHBOARD_ID, dashboard.id)
        .apply()
}
