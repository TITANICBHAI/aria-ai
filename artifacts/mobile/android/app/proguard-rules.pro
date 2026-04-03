-keep class com.ariaagent.mobile.** { *; }
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
# llama.cpp JNI symbols — never strip
-keep class com.ariaagent.mobile.core.ai.LlamaJNI { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
