-keep class com.ariaagent.mobile.** { *; }
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
# llama.cpp JNI symbols — never strip
-keep class com.ariaagent.mobile.core.ai.LlamaJNI { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── R8 / ProGuard: suppress missing-class warnings for compile-time-only deps ──
#
# google-auto-value and its shaded javapoet copy reference javax.lang.model.*
# (the Java annotation-processing API, part of tools.jar / JDK internals).
# These classes are used ONLY at annotation-processor compile time; they are
# never present at Android runtime.  R8 must be told to ignore them.
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
