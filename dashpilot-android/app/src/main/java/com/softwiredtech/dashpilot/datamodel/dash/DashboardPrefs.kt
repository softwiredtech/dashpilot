package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context
import androidx.core.content.edit

const val DASH_PREFS_NAME = "dash_prefs"
const val PREF_SELECTED_DASHBOARD_ID = "selected_dashboard_id"
const val PREF_DEV_RIVE_FILE_URI = "dev_rive_file_uri"

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
const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
const val PREF_PINNED_CONTROL = "pinned_control"

// Automations
const val PREF_WIPER_OFF_AUTOMATION = "wiper_off_automation"
const val PREF_THREE_FINGER_ACTION = "three_finger_action"
const val DEFAULT_WIPER_OFF_AUTOMATION = false

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
const val DEFAULT_ONBOARDING_COMPLETED = false

private const val DEFAULT_DASHBOARD_ID = "vanilla"

fun defaultDashboard(): DashboardConfig {
    return dashboardById(DEFAULT_DASHBOARD_ID) ?: availableDashboards.first().withManifest()
}

fun dashboardById(id: String?): DashboardConfig? {
    val normalizedId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return availableDashboards.firstOrNull { it.id == normalizedId }?.withManifest()
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
        .edit {
            putString(PREF_SELECTED_DASHBOARD_ID, dashboard.id)
        }
}

fun saveDevRiveFileUri(context: Context, uri: String) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(PREF_DEV_RIVE_FILE_URI, uri)
        }
}

fun isOnboardingCompleted(context: Context): Boolean =
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREF_ONBOARDING_COMPLETED, DEFAULT_ONBOARDING_COMPLETED)

fun setOnboardingCompleted(context: Context, value: Boolean = true) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit { putBoolean(PREF_ONBOARDING_COMPLETED, value) }
}

fun getPinnedControl(context: Context): String? =
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_PINNED_CONTROL, null)

fun setPinnedControl(context: Context, id: String?) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            if (id.isNullOrBlank()) {
                remove(PREF_PINNED_CONTROL)
            } else {
                putString(PREF_PINNED_CONTROL, id)
            }
        }
}

fun getWiperOffAutomation(context: Context): Boolean =
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREF_WIPER_OFF_AUTOMATION, DEFAULT_WIPER_OFF_AUTOMATION)

fun setWiperOffAutomation(context: Context, value: Boolean) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit { putBoolean(PREF_WIPER_OFF_AUTOMATION, value) }
}

fun getThreeFingerAction(context: Context): String? =
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_THREE_FINGER_ACTION, null)

fun setThreeFingerAction(context: Context, id: String?) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            if (id.isNullOrBlank()) {
                remove(PREF_THREE_FINGER_ACTION)
            } else {
                putString(PREF_THREE_FINGER_ACTION, id)
            }
        }
}
