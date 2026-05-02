package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context

const val DASH_PREFS_NAME = "dash_prefs"
const val PREF_SELECTED_DASHBOARD_ID = "selected_dashboard_id"

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
