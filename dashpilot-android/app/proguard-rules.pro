# ============================================================================
# DashPilot App ProGuard Rules
# ============================================================================

# --- JavaScript Bridge (WebView @JavascriptInterface) ---
-keepclassmembers class com.softwiredtech.dashpilot.js.CarStateBridge {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface public *;
}

# --- CarState data class (used by JS bridge and Gson) ---
-keep class com.softwiredtech.dashpilot.datamodel.dash.CarState { *; }
-keep class com.softwiredtech.dashpilot.datamodel.dash.DashboardConfig { *; }
-keep class com.softwiredtech.dashpilot.datamodel.dash.DashboardConfig$* { *; }

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep all @Serializable classes (name + members) and their generated serializers
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class ** { *; }
-keepclasseswithmembers class **$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Gson (used by WebsocketDataSource) ---
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- OkHttp ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Debugging: preserve source file and line numbers in stack traces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
