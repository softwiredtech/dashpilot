package com.softwiredtech.dashpilot.datasource

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.softwiredtech.dashkitconnect.DashKitTelemetry

object CrashlyticsTelemetry : DashKitTelemetry {
    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    override fun log(message: String) = crashlytics.log(message)

    override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)
}
