package com.softwiredtech.dashkitconnect

/**
 * Optional hook for forwarding BLE breadcrumbs and connection failures to a
 * crash reporter (Crashlytics, Sentry, ...). The library never depends on a
 * specific reporter; install an implementation via [DashKitBleManager]'s
 * constructor. Everything is also written to logcat regardless.
 */
interface DashKitTelemetry {
    fun log(message: String) {}
    fun recordException(throwable: Throwable) {}

    companion object {
        val None: DashKitTelemetry = object : DashKitTelemetry {}
    }
}
