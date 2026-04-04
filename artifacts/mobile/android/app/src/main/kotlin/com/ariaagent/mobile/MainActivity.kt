package com.ariaagent.mobile

import com.ariaagent.mobile.core.system.SustainedPerformanceManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * MainActivity — React Native entry point.
 *
 * Hosts the React Native JS shell (Dashboard, Control, Activity, Modules, Settings tabs).
 * Uses Old Architecture (bridge-based, Fabric disabled).
 */
class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "main"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, false)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        SustainedPerformanceManager.register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        SustainedPerformanceManager.unregister()
    }
}
