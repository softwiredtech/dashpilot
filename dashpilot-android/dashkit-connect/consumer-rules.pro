# JNI resolves these by name; keep them through the host app's R8 pass.
-keepclasseswithmembernames class com.softwiredtech.dashkitconnect.can.DashKitDecoder {
    native <methods>;
}
