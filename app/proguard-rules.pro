# Add project specific ProGuard rules here.
-keep class com.onyx.bridge.** { *; }

# Keep native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}
