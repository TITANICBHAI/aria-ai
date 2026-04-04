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
import expo.modules.kotlin.ExpoModulesPackage
import expo.modules.ExpoModulesPackageList

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
                ExpoModulesPackage(ExpoModulesPackageList()),
                AgentCorePackage(),
            )

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = false

            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, OpenSourceMergedSoMapping)
    }
}
