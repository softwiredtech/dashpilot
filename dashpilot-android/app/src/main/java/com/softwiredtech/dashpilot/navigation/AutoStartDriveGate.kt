package com.softwiredtech.dashpilot.navigation

/** Opens once per departure from Park; unknown gear values never trigger a drive. */
class AutoStartDriveGate {
    private var started = false

    fun shouldStart(
        enabled: Boolean,
        connected: Boolean,
        gear: Float?,
        canNavigate: Boolean,
        dashboardVisible: Boolean
    ): Boolean {
        if (!enabled || !connected || gear == 1f) {
            started = false
            return false
        }
        if (gear != 2f && gear != 3f && gear != 4f) return false
        if (!canNavigate) return false
        if (started) return false
        started = true
        return !dashboardVisible
    }
}
