package com.ariaagent.mobile.system.actions

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ariaagent.mobile.system.accessibility.AgentAccessibilityService
import org.json.JSONObject

/**
 * GestureEngine — executes LLM action decisions as physical gestures.
 *
 * The LLM outputs JSON: {"tool":"Click","node_id":"#3","reason":"..."}
 * GestureEngine resolves the node_id → screen coordinates → gesture dispatch.
 *
 * Why coordinates from node IDs?
 *   The LLM doesn't know exact pixels. It knows semantic IDs assigned to
 *   accessibility nodes. GestureEngine looks up the node's bounding box
 *   and computes the center for tap, or edge-to-edge path for swipe.
 *
 * After each action: wait for AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
 * to confirm the screen updated (action was received by the OS).
 *
 * Phase: 3 (Action Layer)
 */
object GestureEngine {

    /**
     * Parse and execute an action from the LLM's JSON output.
     * @return true if action was dispatched successfully
     */
    suspend fun executeFromJson(actionJson: String): Boolean {
        return try {
            val json = JSONObject(actionJson)
            val tool = json.optString("tool", "")
            val nodeId = json.optString("node_id", "")
            val direction = json.optString("direction", "")
            val text = json.optString("text", "")

            when (tool.lowercase()) {
                "click", "tap" -> tap(nodeId)
                "swipe" -> swipe(nodeId, direction)
                "type", "typetext" -> type(nodeId, text)
                "scroll" -> scroll(nodeId, direction)
                "longpress" -> longPress(nodeId)
                "back" -> { AgentAccessibilityService.performBack(); true }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Tap the center of the element with the given semantic ID.
     */
    fun tap(nodeId: String): Boolean {
        val node = AgentAccessibilityService.getNodeById(nodeId) ?: return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()

        var dispatched = false
        AgentAccessibilityService.dispatchTap(cx, cy, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                dispatched = true
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {}
        })
        return dispatched
    }

    /**
     * Swipe within a scrollable node.
     */
    fun swipe(nodeId: String, direction: String): Boolean {
        val node = AgentAccessibilityService.getNodeById(nodeId) ?: return false
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up" -> floatArrayOf(rect.centerX().toFloat(), rect.bottom.toFloat() * 0.8f,
                rect.centerX().toFloat(), rect.top.toFloat() * 1.2f)
            "down" -> floatArrayOf(rect.centerX().toFloat(), rect.top.toFloat() * 1.2f,
                rect.centerX().toFloat(), rect.bottom.toFloat() * 0.8f)
            "left" -> floatArrayOf(rect.right.toFloat() * 0.8f, rect.centerY().toFloat(),
                rect.left.toFloat() * 1.2f, rect.centerY().toFloat())
            "right" -> floatArrayOf(rect.left.toFloat() * 1.2f, rect.centerY().toFloat(),
                rect.right.toFloat() * 0.8f, rect.centerY().toFloat())
            else -> return false
        }

        var dispatched = false
        AgentAccessibilityService.dispatchSwipe(x1, y1, x2, y2, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                dispatched = true
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {}
        })
        return dispatched
    }

    /**
     * Type text into an editable node using ACTION_SET_TEXT.
     */
    fun type(nodeId: String, text: String): Boolean {
        val node = AgentAccessibilityService.getNodeById(nodeId) ?: return false
        if (!node.isEditable) return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Scroll a node using accessibility actions (no gesture needed).
     */
    fun scroll(nodeId: String, direction: String): Boolean {
        val node = AgentAccessibilityService.getNodeById(nodeId) ?: return false
        return when (direction.lowercase()) {
            "up" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "down" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            else -> false
        }
    }

    /**
     * Long press to trigger context menus.
     */
    fun longPress(nodeId: String): Boolean {
        val node = AgentAccessibilityService.getNodeById(nodeId) ?: return false
        val rect = Rect()
        node.getBoundsInScreen(rect)

        var dispatched = false
        AgentAccessibilityService.dispatchTap(
            rect.centerX().toFloat(), rect.centerY().toFloat(),
            object : GestureResultCallback() {
                override fun onCompleted(g: android.accessibilityservice.GestureDescription) { dispatched = true }
                override fun onCancelled(g: android.accessibilityservice.GestureDescription) {}
            }
        )
        return dispatched
    }

    private operator fun FloatArray.component1() = this[0]
    private operator fun FloatArray.component2() = this[1]
    private operator fun FloatArray.component3() = this[2]
    private operator fun FloatArray.component4() = this[3]
}
