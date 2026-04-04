package com.ariaagent.mobile

import android.app.Application
import com.ariaagent.mobile.bridge.AgentCorePackage
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
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
                // Previously ModuleRegistryAdapter(emptyList()) was used, which set
                // ModulesProvider to null — causing all expo native module calls from JS
                // to fail with "module not found", crashing the app on launch.
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

    // React Native 0.76+ calls getReactHost() during Activity lifecycle even in
    // Old Architecture mode. Providing a real ReactHost via getDefaultReactHost
    // prevents the UnsupportedOperationException crash on app launch.
    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, OpenSourceMergedSoMapping)
    }
}
