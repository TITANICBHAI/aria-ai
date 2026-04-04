package com.ariaagent.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ariaagent.mobile.core.system.SustainedPerformanceManager
import com.ariaagent.mobile.ui.theme.ARIAColors

/**
 * ComposeMainActivity — Phase 11 native Android UI entry point.
 *
 * Hosts ARIAComposeApp (NavHost + bottom nav + all 5 screens).
 *
 * Registration: AndroidManifest.xml lists this as a secondary Activity (NOT launcher).
 *   - Launcher is still ReactActivity (Phase 1/React Native shell) until EAS build is complete.
 *   - Once EAS produces an APK with the Compose launcher, the intent-filter moves here.
 *
 * Edge-to-edge: enabled so the ARIA navy background fills the status bar and nav bar areas.
 * The NavigationBar in ARIAComposeApp uses navigationBarsPadding() to avoid overlap.
 *
 * ViewModel lifecycle: AgentViewModel is scoped to this Activity. Survives
 * configuration changes (screen rotation). Does NOT survive Activity restart —
 * AgentEventBus (SharedFlow replay=1) re-delivers the last event on resubscription
 * so the UI recovers the most recent agent state immediately.
 *
 * Phase: 11 (Jetpack Compose UI)
 */
class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 14: register so AgentLoop can call SustainedPerformanceManager.enable()
        SustainedPerformanceManager.register(this)
        enableEdgeToEdge()
        setContent {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ARIAColors.Background)
            ) {
                ARIAComposeApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SustainedPerformanceManager.unregister()
    }
}
