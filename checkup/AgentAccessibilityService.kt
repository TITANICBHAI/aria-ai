package com.ariaagent.mobile.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AgentAccessibilityService — two jobs:
 *   1. READ: parse the UI node tree into LLM-friendly semantic text
 *   2. WRITE: dispatch physical gestures (tap, swipe, text, scroll)
 *
 * This is the "hands and eyes" of the agent.
 * Must be enabled by user in Android Accessibility Settings.
 *
 * Node tree format output:
 *   [#1] Button: "Play" (center, clickable)
 *   [#2] EditText: "Search..." (top, editable)
 *   [#3] ImageButton: "Settings" (top-right, clickable)
 *
 * Phase: 2 (Perception) + Phase 3 (Action)
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var isActive = false
            private set

        var currentPackage: String? = null
            private set

        var currentActivity: String? = null
            private set

        private var instance: AgentAccessibilityService? = null

        fun getSemanticTree(): String {
            val svc = instance ?: return "(accessibility service not active)"
            return svc.buildSemanticTree()
        }

        fun dispatchTap(x: Float, y: Float, callback: GestureResultCallback) {
            instance?.dispatchTapAt(x, y, callback)
        }

        fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, callback: GestureResultCallback) {
            instance?.dispatchSwipeGesture(x1, y1, x2, y2, callback)
        }

        fun performBack() {
            instance?.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        // node registry: nodeId → AccessibilityNodeInfo (rebuilt each screen state)
        private val nodeRegistry = mutableMapOf<String, AccessibilityNodeInfo>()

        fun getNodeById(id: String): AccessibilityNodeInfo? = nodeRegistry[id]
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isActive = true
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != null) {
            currentPackage = event.packageName.toString()
        }
        if (event.className != null &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            currentActivity = event.className.toString()
        }
    }

    /**
     * Traverse the UI node tree and build LLM-friendly semantic text.
     * Only includes interactable nodes (clickable, scrollable, editable, focusable).
     *
     * Node copies are recycled after extraction to avoid AccessibilityNodeInfo leaks.
     * The nodeRegistry holds copies (via obtain()) that must be recycled when the
     * registry is cleared. Copies are safe to hold across calls — only the live
     * reference obtained from rootInActiveWindow needs recycling immediately.
     */
    private fun buildSemanticTree(): String {
        // Recycle previously stored node copies before rebuilding
        nodeRegistry.values.forEach { it.recycle() }
        nodeRegistry.clear()

        val root = rootInActiveWindow ?: return "(no active window)"
        val lines = mutableListOf<String>()
        traverseNode(root, lines, 1)
        root.recycle()
        return lines.joinToString("\n")
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, lines: MutableList<String>, counter: Int): Int {
        var id = counter
        if (node == null) return id

        val interactable = node.isClickable || node.isScrollable ||
            node.isEditable || node.isFocusable || node.isLongClickable

        if (interactable) {
            val nodeId = "#$id"
            // Store a copy — the original child reference is owned by the caller loop
            nodeRegistry[nodeId] = AccessibilityNodeInfo.obtain(node)
            id++

            val type = getNodeType(node)
            val text = node.text?.toString()?.trim()
                ?: node.contentDescription?.toString()?.trim()
                ?: node.hintText?.toString()?.trim()
                ?: ""
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val position = describePosition(rect)
            val attrs = buildList {
                if (node.isClickable) add("clickable")
                if (node.isScrollable) add("scrollable")
                if (node.isEditable) add("editable")
                if (node.isEnabled.not()) add("disabled")
            }.joinToString(", ")

            lines.add("[$nodeId] $type: \"$text\" ($position${if (attrs.isNotEmpty()) ", $attrs" else ""})")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            id = traverseNode(child, lines, id)
            child.recycle()
        }
        return id
    }

    private fun getNodeType(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString() ?: ""
        return when {
            className.endsWith("Button") || className.endsWith("ImageButton") -> "Button"
            className.endsWith("EditText") -> "EditText"
            className.endsWith("TextView") -> "Text"
            className.endsWith("ImageView") -> "Image"
            className.endsWith("CheckBox") -> "CheckBox"
            className.endsWith("Switch") -> "Switch"
            className.endsWith("ListView") || className.endsWith("RecyclerView") -> "List"
            className.endsWith("ScrollView") -> "ScrollView"
            else -> "View"
        }
    }

    private fun describePosition(rect: Rect): String {
        val display = resources.displayMetrics
        val cx = rect.centerX().toFloat() / display.widthPixels
        val cy = rect.centerY().toFloat() / display.heightPixels
        val h = when {
            cy < 0.25f -> "top"
            cy > 0.75f -> "bottom"
            else -> "center"
        }
        val v = when {
            cx < 0.33f -> "-left"
            cx > 0.66f -> "-right"
            else -> ""
        }
        return "$h$v"
    }

    // ─── Gesture dispatch ─────────────────────────────────────────────────────

    private fun dispatchTapAt(x: Float, y: Float, callback: GestureResultCallback) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, callback, null)
    }

    private fun dispatchSwipeGesture(
        x1: Float, y1: Float, x2: Float, y2: Float,
        callback: GestureResultCallback
    ) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, callback, null)
    }
}
