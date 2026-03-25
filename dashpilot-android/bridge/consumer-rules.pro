# ============================================================================
# Bridge module consumer ProGuard rules
# These rules are automatically included by any app that depends on :bridge
# ============================================================================

# --- JNI: VehicleBridge native methods ---
-keep class com.softwiredtech.dashpilot.jni.VehicleBridge {
    <init>();
    native <methods>;
}

# --- JNI: CanSignalCallback (called from native code) ---
-keep interface com.softwiredtech.dashpilot.jni.CanSignalCallback {
    *;
}
# Keep all implementations of CanSignalCallback so JNI callbacks work
-keep class * implements com.softwiredtech.dashpilot.jni.CanSignalCallback {
    *;
}
