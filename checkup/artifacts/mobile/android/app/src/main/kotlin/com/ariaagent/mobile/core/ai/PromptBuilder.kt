package com.ariaagent.mobile.core.ai

import com.ariaagent.mobile.core.memory.ObjectLabelStore
import com.ariaagent.mobile.core.perception.ObjectDetectorEngine
import com.ariaagent.mobile.core.perception.ScreenObserver

/**
 * PromptBuilder — Assembles the full LLM prompt for each reasoning step.
 *
 * The LLM (Llama 3.2-1B Instruct) expects a specific chat template:
 *   <|begin_of_text|>
 *   <|start_header_id|>system<|end_header_id|>
 *   {system prompt}
 *   <|eot_id|>
 *   <|start_header_id|>user<|end_header_id|>
 *   {current observation}
 *   <|eot_id|>
 *   <|start_header_id|>assistant<|end_header_id|>
 *
 * The model is expected to reply with a single JSON action:
 *   {"tool":"Click","node_id":"#3","reason":"..."}
 *   {"tool":"Type","node_id":"#2","text":"hello","reason":"..."}
 *   {"tool":"Swipe","direction":"up","reason":"..."}
 *   {"tool":"Back","reason":"..."}
 *   {"tool":"Wait","duration_ms":1000,"reason":"..."}
 *   {"tool":"Done","reason":"..."}
 *
 * Object Label injection:
 *   When the user has annotated elements on this screen via the Object Labeler,
 *   those annotations are injected as a [KNOWN ELEMENTS] section.
 *   This is the highest-quality context available — human-verified, LLM-enriched.
 *   Format:
 *     ★ "Checkout Button" (button, importance 9/10): Tap to begin payment flow | Agent note: Use when goal involves purchasing
 *     ★ "Email Field" (input, importance 8/10): Type user's email address here
 *
 * Visual Detection injection (Phase 13):
 *   When ObjectDetectorEngine has detected elements (icons, game sprites, custom views
 *   not present in the accessibility tree), they appear as a [VISUAL DETECTIONS] block.
 *   These cover elements that ML Kit OCR cannot read and the a11y tree does not expose.
 *   Format:
 *     det-1: person (87%, center 45%×60%)
 *     det-2: cell phone (72%, center 20%×15%)
 *
 * Phase: 1 (LLM) — used by AgentLoop from Phase 3 onward.
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are ARIA — an autonomous Android UI agent running on-device.

Your job: given a screen description and a goal, output exactly ONE JSON action.

AVAILABLE ACTIONS:
  {"tool":"Click","node_id":"#N","reason":"..."}
  {"tool":"Type","node_id":"#N","text":"...","reason":"..."}
  {"tool":"Swipe","direction":"up|down|left|right","reason":"..."}
  {"tool":"Back","reason":"..."}
  {"tool":"Wait","duration_ms":500,"reason":"..."}
  {"tool":"Done","reason":"Goal achieved"}

RULES:
- Output ONLY valid JSON. No explanation outside the JSON object.
- Use node_id from [NODES] section. Never guess coordinates.
- If [KNOWN ELEMENTS] section exists, prefer those elements — they are human-verified.
- If [VISUAL DETECTIONS] section exists, use det-N labels to reference detected visual elements that have no accessibility node (icons, sprites, custom views).
- If the screen is loading, use Wait.
- If the goal is complete, use Done.
- Think step by step inside the "reason" field (max 30 words).
- Never use a node marked "disabled"."""

    /**
     * Build a full inference prompt for one Observe→Reason step.
     *
     * @param snapshot         The current screen observation (a11y tree + OCR)
     * @param goal             The user's task description
     * @param history          Last N actions taken (prevents repetition loops)
     * @param memory           Relevant past experience snippets (from EmbeddingEngine retrieval)
     * @param objectLabels     Human-annotated UI elements for this screen (highest-quality context)
     * @param detectedObjects  MediaPipe detections for visual elements not in the a11y tree (Phase 13)
     * @param appKnowledge     Compact one-liner from AppSkillRegistry for the current app (Phase 15)
     */
    fun build(
        snapshot: ScreenObserver.ScreenSnapshot,
        goal: String,
        history: List<String> = emptyList(),
        memory: List<String> = emptyList(),
        objectLabels: List<ObjectLabelStore.ObjectLabel> = emptyList(),
        detectedObjects: List<ObjectDetectorEngine.DetectedObject> = emptyList(),
        appKnowledge: String = ""
    ): String {
        val sb = StringBuilder()

        sb.append("<|begin_of_text|>")
        sb.append("<|start_header_id|>system<|end_header_id|>\n")
        sb.append(SYSTEM_PROMPT)

        if (memory.isNotEmpty()) {
            sb.append("\n\nRELEVANT MEMORY (past experiences on similar screens):\n")
            memory.take(3).forEach { sb.appendLine("- $it") }
        }

        // ── App Skill Registry hint (Phase 15) ────────────────────────────────
        // Injected after memory so the LLM has app-specific context when reasoning.
        // Only shown when ARIA has prior experience with this app.
        if (appKnowledge.isNotEmpty()) {
            sb.append("\n\n[APP KNOWLEDGE] (ARIA's prior experience with this app)\n")
            sb.appendLine(appKnowledge)
        }

        sb.append("\n<|eot_id|>\n")

        sb.append("<|start_header_id|>user<|end_header_id|>\n")
        sb.append("GOAL: $goal\n\n")

        // ── Object labels injected BEFORE the raw node tree ───────────────────
        // These are the highest-quality signals: human-verified, LLM-enriched.
        // The model should prefer these over the raw accessibility nodes.
        if (objectLabels.isNotEmpty()) {
            sb.appendLine("[KNOWN ELEMENTS] (human-annotated — use these when relevant to goal)")
            objectLabels
                .sortedByDescending { it.importanceScore }
                .take(8)
                .forEach { label -> sb.appendLine(label.toPromptLine()) }
            sb.appendLine()
        }

        // ── Visual detections injected after KNOWN ELEMENTS, before raw nodes ──
        // Covers icons, game sprites, Flutter/Unity widgets not in the a11y tree.
        // Limited to top-8 by confidence to avoid bloating the 4096-token context.
        if (detectedObjects.isNotEmpty()) {
            sb.appendLine("[VISUAL DETECTIONS] (MediaPipe — elements not in accessibility tree)")
            detectedObjects
                .sortedByDescending { it.confidence }
                .take(8)
                .forEachIndexed { i, obj -> sb.appendLine(obj.toPromptLine(i)) }
            sb.appendLine()
        }

        sb.append(snapshot.toLlmString())

        if (history.isNotEmpty()) {
            sb.append("\n\nRECENT ACTIONS (avoid repeating these):\n")
            history.takeLast(5).forEachIndexed { i, action ->
                sb.appendLine("${i + 1}. $action")
            }
        }

        sb.append("\n<|eot_id|>\n")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n")

        return sb.toString()
    }

    /**
     * Extract a valid JSON action from the raw LLM output.
     * Handles common formatting noise (markdown code fences, leading text).
     */
    fun parseAction(rawOutput: String): String {
        val cleaned = rawOutput.trim()

        val jsonStart = cleaned.indexOfFirst { it == '{' }
        if (jsonStart == -1) return """{"tool":"Wait","duration_ms":500,"reason":"no action parsed"}"""

        val jsonEnd = cleaned.lastIndexOf('}')
        if (jsonEnd <= jsonStart) return """{"tool":"Wait","duration_ms":500,"reason":"malformed json"}"""

        return cleaned.substring(jsonStart, jsonEnd + 1).trim()
    }
}
