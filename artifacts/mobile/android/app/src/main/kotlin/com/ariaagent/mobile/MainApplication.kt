package com.ariaagent.mobile

import android.app.Application
import com.ariaagent.mobile.bridge.AgentCorePackage
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
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
import com.swmansion.worklets.WorkletsPackage
import expo.modules.adapters.react.ModuleRegistryAdapter

class MainApplication : Application(), ReactApplication {

    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> = listOf(
                // React Native community native modules (manually linked)
                AsyncStoragePackage(),
                RNGestureHandlerPackage(),
                KeyboardControllerPackage(),
                ReanimatedPackage(),
                SafeAreaContextPackage(),
                RNScreensPackage(),
                SvgPackage(),
                WorkletsPackage(),
                // Expo modules — ExpoModulesHelper discovers expo.modules.ExpoModulesPackageList
                // via reflection; ExpoModulesPackageList is defined in this module's source.
                ModuleRegistryAdapter(emptyList()),
                // Custom native bridge
                AgentCorePackage(),
            )

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED

            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }

    override val reactHost: ReactHost
        get() = com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
            .reactHost(this, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, OpenSourceMergedSoMapping)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            load()
        }
    }
}
