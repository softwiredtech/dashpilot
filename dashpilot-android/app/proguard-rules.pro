# ── JS Bridge ─────────────────────────────────────────────────────────
# CarStateBridge is exposed to JS via addJavascriptInterface("NativeCarState").
# All @JavascriptInterface methods are called by name from the WebView.
-keepclassmembers class com.softwiredtech.dashpilot.js.CarStateBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── JNI callback (SAM lambda) ─────────────────────────────────────────
# The SAM lambda in CommaDataSource that implements CanSignalCallback
# must not be merged/inlined by R8, otherwise C++ GetMethodID("onCanData")
# fails silently and the app stays stuck in Connecting.
-keep class com.softwiredtech.dashpilot.datasource.CommaDataSource$* {
    void onCanData(double[]);
}

# ── Gson serialization ────────────────────────────────────────────────
# CarState is deserialized via Gson.fromJson — field names must be kept.
-keep class com.softwiredtech.dashpilot.datamodel.CarState { *; }

# Generic Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── OkHttp ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Rive ──────────────────────────────────────────────────────────────
-keep class app.rive.runtime.** { *; }

# ── Debug info ────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
