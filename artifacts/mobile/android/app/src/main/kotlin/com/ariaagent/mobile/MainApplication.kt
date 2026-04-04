package com.ariaagent.mobile

import android.app.Application
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader

/**
 * MainApplication — plain Application subclass.
 *
 * React Native and Expo have been removed. This class only initialises
 * SoLoader so the NDK / llama.cpp JNI layer can load its shared libraries
 * at startup. All UI is handled by ComposeMainActivity (Jetpack Compose).
 *
 * Migration phase: 1 — RN host stripped, Compose is now the launcher.
 */
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Required for llama.cpp JNI — loads libllama.so and libc++_shared.so
        // from the APK's jniLibs directory. Must run before any native call.
        SoLoader.init(this, OpenSourceMergedSoMapping)
    }
}
