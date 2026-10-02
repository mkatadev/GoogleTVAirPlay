# JNI entry points and callbacks resolved by name from native code
-keep class pl.prodevcode.airplay.bridge.NativeBridge { *; }
-keep interface pl.prodevcode.airplay.bridge.RaopCallbackHandler { *; }
-keep class * implements pl.prodevcode.airplay.bridge.RaopCallbackHandler { *; }
-keep interface pl.prodevcode.airplay.bridge.LogListener { *; }
