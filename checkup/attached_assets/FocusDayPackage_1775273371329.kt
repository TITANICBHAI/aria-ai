package com.tbtechs.focusflow.modules

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * FocusDayPackage
 *
 * Registers all FocusDay native modules with the React Native bridge.
 *
 * Uses plain ReactPackage (not TurboReactPackage) — the correct pattern for
 * Old Architecture (newArchEnabled=false). TurboReactPackage with isTurboModule=true
 * causes all modules to resolve as null on old arch because the bridge routes them
 * through the TurboModule loading path, which requires New Architecture.
 *
 * All six modules use the standard ReactContextBaseJavaModule + @ReactMethod pattern.
 *
 * Wired into MainApplication.kt via the withFocusDayAndroid config plugin automatically
 * during expo prebuild / EAS build.
 */
class FocusDayPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(
            UsageStatsModule(reactContext),
            ForegroundServiceModule(reactContext),
            ForegroundLaunchModule(reactContext),
            FocusDayBridgeModule(reactContext),
            SharedPrefsModule(reactContext),
            InstalledAppsModule(reactContext),
        )

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
