package com.ariaagent.mobile.system.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ariaagent.mobile.core.events.AgentEventBus

/**
 * FloatingChatService — draws a live ARIA chat overlay over any app.
 *
 * Uses TYPE_APPLICATION_OVERLAY (requires SYSTEM_ALERT_WINDOW permission).
 * The overlay is a ComposeView attached to the WindowManager — it floats over
 * the current foreground app while ARIA is acting, letting the user:
 *   - Watch live action + reason in real time
 *   - Type instructions that are injected into the AgentLoop
 *   - Draw gestures on screen — captured as frame annotations
 *
 * Lifecycle: started by AgentForegroundService when AgentLoop starts;
 *            stopped when AgentLoop finishes or is paused.
 *
 * Companion helpers start() / stop() called from AgentViewModel.
 */
class FloatingChatService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry      = LifecycleRegistry(this)
    private val savedStateController   = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle  get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView?     = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView == null) setup()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_STICKY
    }

    override fun onDestroy() {
        teardown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setup() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 24
            y = 120
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingChatService)
            setViewTreeSavedStateRegistryOwner(this@FloatingChatService)
            setContent {
                FloatingChatOverlay(
                    onInstruction = { text ->
                        AgentEventBus.emit(
                            "user_instruction",
                            mapOf("text" to text, "source" to "floating_chat")
                        )
                    },
                    onGestureAnnotation = { annotation ->
                        AgentEventBus.emit(
                            "user_gesture_annotation",
                            mapOf("annotation" to annotation, "source" to "floating_chat")
                        )
                    },
                    onDismiss = {
                        teardown(); stopSelf()
                    }
                )
            }
        }

        windowManager?.addView(composeView, params)
        overlayView = composeView
    }

    private fun teardown() {
        overlayView?.let {
            runCatching { windowManager?.removeView(it) }
            overlayView = null
        }
    }

    companion object {
        const val ACTION_STOP = "com.ariaagent.mobile.STOP_FLOATING_CHAT"
    }
}
