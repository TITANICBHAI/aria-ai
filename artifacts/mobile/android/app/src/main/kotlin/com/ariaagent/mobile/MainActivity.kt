package com.ariaagent.mobile

import com.ariaagent.mobile.core.system.SustainedPerformanceManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * MainActivity — React Native entry point (Phase 1 launcher).
 *
 * Hosts the React Native JS shell (all 5 tabs: Dashboard, Control, Activity, Modules, Settings).
 * Will remain the launcher until ComposeMainActivity is validated on device via EAS build (Phase 11).
 *
 * Phase 14: SustainedPerformanceManager is registered here so that when AgentLoop starts
 * and calls SustainedPerformanceManager.enable(), it has a valid Activity reference to call
 * Window.setSustainedPerformanceMode(true) on — stabilising Exynos 9611 clocks during inference.
 */
class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "main"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        SustainedPerformanceManager.register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        SustainedPerformanceManager.unregister()
    }
}
