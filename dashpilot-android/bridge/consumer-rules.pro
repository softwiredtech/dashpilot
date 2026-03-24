# ── JNI Bridge ────────────────────────────────────────────────────────
# VehicleBridge: C++ resolves methods via JNI mangled names
# (Java_com_softwiredtech_dashpilot_jni_VehicleBridge_native*).
# Class name and all native methods must remain untouched.
-keep class com.softwiredtech.dashpilot.jni.VehicleBridge {
    native <methods>;
    <init>();
}

# CanSignalCallback: C++ calls onCanData via GetMethodID at runtime.
-keep interface com.softwiredtech.dashpilot.jni.CanSignalCallback {
    void onCanData(double[]);
}

# Any class implementing CanSignalCallback (including SAM lambdas)
# must also keep onCanData so the JNI lookup succeeds.
-keep class * implements com.softwiredtech.dashpilot.jni.CanSignalCallback {
    void onCanData(double[]);
}

# Prevent R8 from merging/inlining Kotlin SAM lambda wrappers for
# CanSignalCallback. Without this, R8 may optimize away the anonymous
# class generated for the fun interface, causing GetMethodID("onCanData")
# to return null silently and the callback to never fire.
-keep,allowobfuscation class ** implements com.softwiredtech.dashpilot.jni.CanSignalCallback
-keepnames class ** implements com.softwiredtech.dashpilot.jni.CanSignalCallback
