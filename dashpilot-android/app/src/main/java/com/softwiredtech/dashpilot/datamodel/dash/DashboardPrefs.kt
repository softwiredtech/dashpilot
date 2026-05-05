package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context

const val DASH_PREFS_NAME = "dash_prefs"
const val PREF_SELECTED_DASHBOARD_ID = "selected_dashboard_id"
const val PREF_DEV_RIVE_FILE_URI = "dev_rive_file_uri"

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
    val dashboard = dashboardById(savedId) ?: defaultDashboard()
    if (dashboard.type == DashboardType.DEV_RIVE) {
        val savedUri = prefs.getString(PREF_DEV_RIVE_FILE_URI, null)
        if (!savedUri.isNullOrEmpty()) {
            return dashboard.copy(url = savedUri)
        }
    }
    return dashboard
}

fun saveSelectedDashboard(context: Context, dashboard: DashboardConfig) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_SELECTED_DASHBOARD_ID, dashboard.id)
        .apply()
}

fun getDevRiveFileUri(context: Context): String? {
    return context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_DEV_RIVE_FILE_URI, null)
}

fun saveDevRiveFileUri(context: Context, uri: String) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_DEV_RIVE_FILE_URI, uri)
        .apply()
}
