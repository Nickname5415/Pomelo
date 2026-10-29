# Keep enough source information for useful release crash reports.
-keepattributes SourceFile,LineNumberTable

# ONNX Runtime's JNI layer resolves its Java classes, fields and constructors by
# hardcoded name from native code (FindClass/GetMethodID/GetFieldID). R8 renaming
# or stripping any of them turns into `java_class == null` and a SIGABRT inside
# convertToTensorInfo the first time a model runs, so the whole package must
# survive shrinking untouched.
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.providers.** { *; }
-dontwarn ai.onnxruntime.**

# Listening Party peers bind these natives by name and are called back from C++.
-keep class xyz.stuffbyren.pomelo.social.PartyPeerNative { native <methods>; }
-keep interface xyz.stuffbyren.pomelo.social.PartyPeerNative$Listener { *; }
-keep class * implements xyz.stuffbyren.pomelo.social.PartyPeerNative$Listener {
    void onSignal(java.lang.String);
    void onOpen();
    void onText(java.lang.String);
    void onClosed(java.lang.String);
}


# The Discord shim binds these natives by name and calls onStatus back from C++.
-keep class xyz.stuffbyren.pomelo.discord.DiscordNative { native <methods>; public static void onStatus(java.lang.String, boolean); }

# Orchard Connect's native core binds these by name and calls the listener back from C++.
-keep class xyz.stuffbyren.pomelo.connect.ConnectNative { native <methods>; }
-keep interface xyz.stuffbyren.pomelo.connect.ConnectNative$Listener { *; }
-keep class * implements xyz.stuffbyren.pomelo.connect.ConnectNative$Listener {
    void onHubSend(java.lang.String);
    void onEvent(java.lang.String);
    void onData(java.lang.String, java.lang.String, byte[]);
}

# Strip verbose/debug logging from release; several sit on playback and UI hot paths.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

-keep class xyz.stuffbyren.pomelo.playback.slop.SlopNative { native <methods>; }
