# Consumer-side ProGuard rules for apps using OnyxBridge.
-keep class com.onyx.bridge.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}
