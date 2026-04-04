# ─── ARIA Agent — ProGuard / R8 rules ────────────────────────────────────────
#
# IMPORTANT: Every native module registered in MainApplication.getPackages()
# must have its classes protected here. R8 will strip or rename any class that
# isn't explicitly kept, breaking React Native's bridge reflection at runtime.
#
# Symptom of missing rules: app crashes immediately after splash screen in
# release APK with "ARIA Agent closed because this app has a bug".
# ─────────────────────────────────────────────────────────────────────────────

# Preserve line numbers in crash stack traces (critical for debugging release crashes)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── React Native core ────────────────────────────────────────────────────────
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.soloader.** { *; }

# ─── ARIA Agent native modules ────────────────────────────────────────────────
-keep class com.ariaagent.mobile.** { *; }

# llama.cpp JNI symbols — never strip
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── Third-party React Native packages (manually linked — NOT auto-linked) ────
# These MUST be kept: R8 cannot see they are used via React Native's reflection.

# react-native-reanimated
-keep class com.swmansion.reanimated.** { *; }
-keep class com.swmansion.reanimated.ReanimatedPackage { *; }

# react-native-gesture-handler
-keep class com.swmansion.gesturehandler.** { *; }
-keep class com.swmansion.gesturehandler.RNGestureHandlerPackage { *; }

# react-native-screens
-keep class com.swmansion.rnscreens.** { *; }
-keep class com.swmansion.rnscreens.RNScreensPackage { *; }

# react-native-safe-area-context
-keep class com.th3rdwave.safeareacontext.** { *; }
-keep class com.th3rdwave.safeareacontext.SafeAreaContextPackage { *; }

# react-native-svg
-keep class com.horcrux.svg.** { *; }
-keep class com.horcrux.svg.SvgPackage { *; }

# @react-native-async-storage/async-storage
-keep class com.reactnativecommunity.asyncstorage.** { *; }
-keep class com.reactnativecommunity.asyncstorage.AsyncStoragePackage { *; }

# react-native-keyboard-controller
-keep class com.reactnativekeyboardcontroller.** { *; }
-keep class com.reactnativekeyboardcontroller.KeyboardControllerPackage { *; }

# ─── Expo modules ─────────────────────────────────────────────────────────────
-keep class expo.modules.** { *; }
-keep class expo.modules.adapters.react.** { *; }
-keep class expo.modules.core.** { *; }

# ─── ONNX Runtime (MiniLM embeddings) ────────────────────────────────────────
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**

# ─── ML Kit OCR ───────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ─── MediaPipe (object detection) ────────────────────────────────────────────
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ─── OkHttp (model download) ──────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── Kotlin coroutines ────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ─── Jetpack / AndroidX ───────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keep class androidx.compose.** { *; }

# ─── Suppress compile-time-only annotation processor warnings ─────────────────
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
