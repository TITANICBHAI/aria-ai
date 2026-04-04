package com.ariaagent.mobile

import android.app.Application
import com.ariaagent.mobile.bridge.AgentCorePackage
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import com.reactnativecommunity.asyncstorage.AsyncStoragePackage
import com.swmansion.gesturehandler.RNGestureHandlerPackage
import com.reactnativekeyboardcontroller.KeyboardControllerPackage
import com.swmansion.reanimated.ReanimatedPackage
import com.th3rdwave.safeareacontext.SafeAreaContextPackage
import com.swmansion.rnscreens.RNScreensPackage
import com.horcrux.svg.SvgPackage
import expo.modules.ExpoModulesPackageList
import expo.modules.adapters.react.ModuleRegistryAdapter
import expo.modules.adapters.react.ReactModuleRegistryProvider

class MainApplication : Application(), ReactApplication {

    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> = listOf(
                AsyncStoragePackage(),
                RNGestureHandlerPackage(),
                KeyboardControllerPackage(),
                ReanimatedPackage(),
                SafeAreaContextPackage(),
                RNScreensPackage(),
                SvgPackage(),
                // Pass ExpoModulesPackageList as the ModulesProvider so all Kotlin
                // expo modules (expo-splash-screen, expo-font, expo-image, expo-haptics,
                // expo-location, expo-linear-gradient, etc.) are registered.
                ModuleRegistryAdapter(
                    ReactModuleRegistryProvider(emptyList()),
                    ExpoModulesPackageList()
                ),
                AgentCorePackage(),
            )

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = false

            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }

    // Old Architecture: return null so ReactActivityDelegate uses the bridge path
    // (reactNativeHost) instead of the bridgeless New-Architecture path.
    //
    // IMPORTANT: Do NOT call getDefaultReactHost(applicationContext, reactNativeHost) here.
    // That function calls reactNativeHost.toReactHost() which creates a ReactHostImpl
    // (the New Architecture bridgeless engine). When ReactActivityDelegate sees a non-null
    // reactHost it launches bridgeless startup, which conflicts with every Old-Architecture
    // package registered in getPackages() above and causes an immediate "app has a bug" crash.
    override val reactHost: ReactHost? = null

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, OpenSourceMergedSoMapping)
    }
}
