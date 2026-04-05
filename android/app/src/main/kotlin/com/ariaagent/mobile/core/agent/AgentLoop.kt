package com.ariaagent.mobile.core.agent

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.ariaagent.mobile.core.ai.LlamaEngine
import com.ariaagent.mobile.core.ai.PromptBuilder
import com.ariaagent.mobile.core.ai.VisionEngine
import com.ariaagent.mobile.core.events.AgentEventBus
import com.ariaagent.mobile.core.memory.EmbeddingEngine
import com.ariaagent.mobile.core.memory.ExperienceStore
import com.ariaagent.mobile.core.memory.ObjectLabelStore
import com.ariaagent.mobile.core.monitoring.MonitoringPusher
import com.ariaagent.mobile.core.perception.ObjectDetectorEngine
import com.ariaagent.mobile.core.perception.ScreenObserver
import com.ariaagent.mobile.core.persistence.ProgressPersistence
import com.ariaagent.mobile.core.system.PixelVerifier
import com.ariaagent.mobile.core.system.SustainedPerformanceManager
import com.ariaagent.mobile.core.system.ThermalGuard
import com.ariaagent.mobile.system.actions.GestureEngine
import com.ariaagent.mobile.system.accessibility.AgentAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AgentLoop — The central Observe → Reason → Act engine.
 *
 * This is the core of ARIA. It runs a continuous loop:
 *   1. OBSERVE   — capture screen (accessibility tree + OCR + bitmap)
 *   2. RETRIEVE  — find relevant past experiences via embedding similarity
 *   3. REASON    — call LlamaEngine.infer() with full prompt
 *   4. PARSE     — extract JSON action from LLM output
 *   5. ACT       — dispatch gesture via GestureEngine
 *   6. EVALUATE  — wait for screen to settle, assign reward
 *   7. STORE     — persist ExperienceTuple to SQLite
 *   8. EMIT      — push status event to AgentEventBus (→ AgentViewModel → Compose UI)
 *
 * Loop stops when:
 *   - LLM returns {"tool":"Done"}
 *   - stepCount >= maxSteps (safety cap: prevents infinite loops)
 *   - stop() is called
 *   - An unrecoverable exception is thrown
 *
 * Events emitted to AgentEventBus (consumed by AgentViewModel → Compose screens):
 *   agent_status_changed  { status, currentTask, currentApp, stepCount }
 *   action_performed      { tool, nodeId, success, reward, stepCount }
 *   token_generated       { token, tokensPerSecond }
 *
 * Phase: 3 (Action Layer) — depends on Phase 1 (LLM) + Phase 2 (Perception).
 */
object AgentLoop {

    enum class Status { IDLE, RUNNING, PAUSED, DONE, ERROR }

    data class LoopState(
        val status: Status = Status.IDLE,
        val goal: String = "",
        val appPackage: String = "",
        val stepCount: Int = 0,
        val lastAction: String = "",
        val lastError: String = "",
        val gameMode: String = "none"   // "none" | "arcade" | "puzzle" | "strategy"
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null

    @Volatile
    var state = LoopState()
        private set

    // Event callback — wired by AgentViewModel to forward events into AgentEventBus
    var onEvent: ((name: String, data: Map<String, Any>) -> Unit)? = null

    private const val MAX_STEPS = 50
    private const val STEP_DELAY_MS = 800L
    private const val SCREEN_SETTLE_MS = 600L
    private const val WAIT_RETRY_DELAY_MS = 1200L

    /**
     * Start the agent loop for a given goal.
     * If already running, stops the current run and starts fresh.
     *
     * Phase 14 additions (all integrated here):
     *   14.1 PixelVerifier    — fast action-result verification via pixel diff on the acted node
     *   14.3 SustainedPerf   — sustained performance mode enabled for stable inference throughput
     *   14.4 ProgressPersist — progress.txt + goals.json synced at start and after each step
     *
     * @param context      Android context (needed for ExperienceStore, EmbeddingEngine)
     * @param goal         Natural-language task description from the user
     * @param appPackage   Target app package (e.g. "com.android.settings")
     * @param learnOnly    When true, skip all gesture dispatch (GestureEngine + GameLoop).
     *                     The loop still observes screens, reasons via LLM, and stores
     *                     experience tuples — generating training data without touching the device.
     *                     Reward = 0.6 (neutral-positive) since outcomes cannot be verified.
     *                     LlmRewardEnricher re-scores these during the next training cycle.
     */
    fun start(context: Context, goal: String, appPackage: String, learnOnly: Boolean = false) {
        loopJob?.cancel()
        state = LoopState(status = Status.RUNNING, goal = goal, appPackage = appPackage)
        emitStatus()

        // ── Phase 14.3: Enable Sustained Performance Mode ─────────────────────
        // Stabilises Exynos 9611 clocks during LLM inference — prevents the
        // ramp-overheat-throttle cycle that degrades tok/s mid-reasoning-turn.
        SustainedPerformanceManager.enable()

        // ── Phase 16: Start monitoring pusher ──────────────────────────────────
        MonitoringPusher.start(context)

        loopJob = scope.launch {
            val store = ExperienceStore.getInstance(context)
            val labelStore = ObjectLabelStore.getInstance(context)
            val actionHistory = mutableListOf<String>()
            var previousSnapshot: ScreenObserver.ScreenSnapshot? = null

            // Phase 15: track element names acted on for AppSkillRegistry frequency counting
            val elementsTouched = mutableListOf<String>()
            var lastAppPackage = appPackage

            // ── Phase 14.4: Log task start + inject previous progress into context ─
            ProgressPersistence.logTaskStart(context, goal)

            // Read last N lines of progress.txt — inject as first history entry so
            // Llama 3.2 "syncs" its context and avoids repeating known-failed actions.
            val previousContext = ProgressPersistence.readContext(context)
            if (previousContext.isNotEmpty()) {
                actionHistory.add(
                    """{"tool":"ContextSync","reason":"Previous session log:\n$previousContext"}"""
                )
            }

            // Check goals.json for a partially-completed task and log the resume point
            val resumeSubTask = ProgressPersistence.nextPendingSubTask(context)
            if (resumeSubTask != null) {
                Log.i("AgentLoop", "Resuming from sub-task ${resumeSubTask.id}: ${resumeSubTask.label}")
                ProgressPersistence.logNote(context, "Resuming from sub-task ${resumeSubTask.id}: ${resumeSubTask.label}")
            }

            try {
                while (isActive && state.stepCount < MAX_STEPS) {

                    // ── 0. STEP STARTED ──────────────────────────────────────
                    // Push to AgentEventBus → Compose ViewModel before any work begins.
                    // Lets the UI show "OBSERVE" spinner immediately.
                    run {
                        val stepData = mapOf(
                            "stepNumber" to state.stepCount,
                            "activity"   to "observe"
                        )
                        onEvent?.invoke("step_started", stepData)
                        AgentEventBus.emit("step_started", stepData)
                    }

                    // ── 1. OBSERVE ────────────────────────────────────────────
                    val snapshot = ScreenObserver.capture()

                    // Allow vision-only steps: if the a11y tree and OCR are both
                    // empty (game, Flutter, Unity) but a bitmap exists AND vision
                    // is loaded, let the step proceed — vision is the only signal.
                    val visionAvailableForEmptyScreen =
                        snapshot.bitmap != null && VisionEngine.isVisionModelReady(context)
                    if (snapshot.isEmpty() && !visionAvailableForEmptyScreen) {
                        delay(WAIT_RETRY_DELAY_MS)
                        continue
                    }

                    // In learn-only mode the screen never changes (we never act),
                    // so skip the change-detection gate — process every observed frame.
                    if (!learnOnly && !ScreenObserver.hasChanged(previousSnapshot, snapshot)) {
                        delay(WAIT_RETRY_DELAY_MS)
                        continue
                    }
                    previousSnapshot = snapshot

                    // ── 1b. GAME DETECTION — switch to fast policy loop if game ──
                    // If a game is detected (score, level, game-over patterns / known pkg),
                    // hand off to GameLoop for the current step and skip LLM reasoning.
                    // GameLoop runs its own single step (Observe→Act→Store→Emit) and returns.
                    // We stay in this outer while loop so we re-detect on every tick —
                    // if the user leaves the game, next iteration will see GameType.NONE.
                    // In learn-only mode, game handoff is skipped — we never dispatch gestures.
                    val gameSignal = GameDetector.detect(snapshot)
                    if (!learnOnly &&
                        gameSignal.gameType != GameDetector.GameType.NONE &&
                        gameSignal.confidence >= GameDetector.MIN_CONFIDENCE
                    ) {
                        val newGameMode = gameSignal.gameType.name.lowercase()
                        if (state.gameMode != newGameMode) {
                            Log.i("AgentLoop", "Switching to game mode: $newGameMode (${gameSignal.triggerReason})")
                            state = state.copy(gameMode = newGameMode)
                            emitStatus()
                        }
                        // Start (or continue) GameLoop — it has its own coroutine
                        if (!GameLoop.isActive()) {
                            GameLoop.onEvent = onEvent
                            GameLoop.start(
                                context  = context,
                                goal     = goal,
                                gameType = gameSignal.gameType,
                                store    = store
                            )
                        }
                        delay(STEP_DELAY_MS)  // yield — GameLoop is driving
                        continue
                    }

                    // Left the game — stop GameLoop if it was running
                    if (state.gameMode != "none") {
                        Log.i("AgentLoop", "Leaving game mode → resuming LLM-guided loop")
                        GameLoop.stop()
                        state = state.copy(gameMode = "none")
                        emitStatus()
                    }

                    // ── 1d. VISUAL DETECTION — MediaPipe EfficientDet-Lite0 ───────────
                    // Runs ONLY when model is ready (non-blocking check).
                    // Covers icons, game sprites, Flutter/Unity widgets not in a11y tree.
                    // Producer-Consumer: detector runs here; LLM consumes via PromptBuilder.
                    // ~37ms on Exynos 9611 — within the 800ms STEP_DELAY_MS budget.
                    val detectedObjects = if (
                        ObjectDetectorEngine.isModelReady(context) && snapshot.bitmap != null
                    ) {
                        runCatching {
                            ObjectDetectorEngine.detect(context, snapshot.bitmap!!)
                        }.getOrDefault(emptyList())
                    } else emptyList()

                    // ── 2. RETRIEVE — find relevant past experiences ───────────
                    val memory = EmbeddingEngine.retrieve(
                        context = context,
                        query = "${goal} ${snapshot.ocrText.take(100)}",
                        store = store,
                        topK = 3
                    )

                    // ── 2b. LABEL LOOKUP — human-annotated elements for this screen
                    // Primary: exact screen hash match (same screen state, O(1) lookup)
                    // Fallback: embedding similarity across all enriched labels for the app
                    val screenLabels = labelStore.getByScreen(snapshot.appPackage, snapshot.screenHash())
                        .ifEmpty {
                            EmbeddingEngine.retrieveLabels(
                                context = context,
                                query = "${goal} ${snapshot.ocrText.take(100)}",
                                labelStore = labelStore,
                                appPackage = snapshot.appPackage,
                                topK = 5
                            )
                        }

                    // ── 2c. THERMAL CHECK — pause if device is running hot ─────
                    // Severe heat on M31 (no NPU, CPU-heavy inference) causes system throttle.
                    // Pause instead of OOM-crashing or burning through tok/s at 4 tok/s.
                    if (ThermalGuard.isEmergency()) {
                        state = state.copy(status = Status.PAUSED, lastError = "thermal_pause")
                        onEvent?.invoke("agent_status_changed", mapOf(
                            "status" to "paused",
                            "currentTask" to state.goal,
                            "currentApp" to state.appPackage,
                            "stepCount" to state.stepCount,
                            "lastAction" to state.lastAction,
                            "lastError" to "thermal_pause"
                        ))
                        delay(10_000L)  // wait 10s then re-check
                        if (ThermalGuard.isEmergency()) break  // still hot → abort
                        state = state.copy(status = Status.RUNNING, lastError = "")
                    }

                    // ── 2d. APP KNOWLEDGE — skill registry hint (Phase 15) ────────
                    // Fetch ARIA's accumulated knowledge about the current app.
                    // Injected as [APP KNOWLEDGE] block into the LLM system prompt.
                    val appKnowledge = runCatching {
                        AppSkillRegistry.getInstance(context).getPromptHint(snapshot.appPackage)
                    }.getOrDefault("")

                    // ── 2e. VISION DESCRIPTION — SmolVLM-256M (Phase 17) ──────────
                    // Run multimodal inference on the current frame when the vision
                    // model is loaded. Runs only on even steps to halve the vision
                    // budget (~400 ms on Exynos 9611 CPU).
                    //
                    // Frame caching: pass screenHash so VisionEngine can return the
                    // cached description instantly when the screen hasn't changed
                    // (no inference cost on cache-hit steps).
                    //
                    // Goal-aware: the agent goal is forwarded so SmolVLM answers a
                    // targeted question rather than a generic screen description.
                    //
                    // Vision-only fallback: if a11y+OCR were empty but vision is
                    // available (game/Flutter screen), vision is the only signal —
                    // always run it regardless of step parity in that case.
                    val forceVision = snapshot.isEmpty() && visionAvailableForEmptyScreen
                    val visionDescription: String = if (
                        snapshot.bitmap != null &&
                        VisionEngine.isVisionModelReady(context) &&
                        (forceVision || state.stepCount % 2 == 0)
                    ) {
                        runCatching {
                            VisionEngine.describe(
                                context     = context,
                                bitmap      = snapshot.bitmap!!,
                                goal        = goal,
                                screenHash  = snapshot.screenHash()
                            )
                        }.getOrDefault("").also { desc ->
                            if (desc.isNotBlank()) Log.d("AgentLoop", "Vision[${ snapshot.screenHash()}]: $desc")
                        }
                    } else ""

                    // ── 3. REASON — call LLM ──────────────────────────────────
                    val prompt = PromptBuilder.build(
                        snapshot = snapshot,
                        goal = goal,
                        history = actionHistory,
                        memory = memory,
                        objectLabels = screenLabels,
                        detectedObjects = detectedObjects,
                        appKnowledge = appKnowledge,
                        visionDescription = visionDescription
                    )

                    val rawOutput = LlamaEngine.infer(prompt, maxTokens = 200) { token ->
                        val tokData = mapOf("token" to token, "tokensPerSecond" to LlamaEngine.lastToksPerSec)
                        onEvent?.invoke("token_generated", tokData)
                        AgentEventBus.emit("token_generated", tokData)
                    }

                    // ── 4. PARSE ──────────────────────────────────────────────
                    val actionJson = PromptBuilder.parseAction(rawOutput)
                    actionHistory.add(actionJson)
                    if (actionHistory.size > 10) actionHistory.removeAt(0)

                    // ── 5. ACT ────────────────────────────────────────────────
                    val isDone = actionJson.contains("\"tool\":\"Done\"")
                    val isWait = actionJson.contains("\"tool\":\"Wait\"")

                    var actionSuccess = false
                    if (isDone) {
                        state = state.copy(status = Status.DONE, lastAction = actionJson)
                        ProgressPersistence.logTaskEnd(context, goal, succeeded = true)
                        SustainedPerformanceManager.disable()
                        emitStatus()
                        // Phase 15: record skill outcome, then auto-chain next queued task
                        recordAndChain(context, goal, lastAppPackage, succeeded = true,
                            stepsTaken = state.stepCount, elementsTouched = elementsTouched)
                        break
                    } else if (isWait) {
                        val duration = extractWaitDuration(actionJson)
                        delay(duration)
                        actionSuccess = true
                    } else if (learnOnly) {
                        // ── LEARN-ONLY: skip gesture, store reasoning as training data ──
                        // The LLM observed a real screen and produced a valid action — this
                        // (screen, goal) → action pair is valuable LoRA training data.
                        // We never dispatch the gesture so the device is not disturbed.
                        // Reward = 0.6 (neutral-positive). LlmRewardEnricher will rescore
                        // this during the next idle training cycle.
                        Log.d("AgentLoop", "Learn-only: storing reasoning without gesture dispatch")
                        actionSuccess = true
                    } else {
                        // ── Phase 14.1: PixelVerifier — fast action-result verification ──
                        // Capture the pre-action pixel state for the targeted node region.
                        // This avoids a full-screen capture; we only diff the acted element.
                        val actedNodeId = extractNodeId(actionJson)
                        val nodeRect = actedNodeId?.let { nid ->
                            runCatching {
                                val node = AgentAccessibilityService.getNodeById(nid)
                                if (node != null) {
                                    val r = android.graphics.Rect()
                                    node.getBoundsInScreen(r)
                                    r
                                } else null
                            }.getOrNull()
                        }

                        val preBitmap = nodeRect?.let { PixelVerifier.capturePre(it) }

                        val gestureSuccess = GestureEngine.executeFromJson(actionJson)

                        // Verify via pixel diff — faster and more precise than full-screen diff.
                        // If we have a valid region pre-bitmap AND the gesture was dispatched,
                        // use PixelVerifier result. Otherwise fall back to gesture result.
                        actionSuccess = if (preBitmap != null && nodeRect != null) {
                            val verification = PixelVerifier.verify(
                                preBitmap   = preBitmap,
                                screenRect  = nodeRect,
                                textVerify  = false   // text verify adds OCR cost — off by default
                            )
                            Log.d("AgentLoop", "PixelVerifier: diff=%.4f changed=${verification.changed}".format(verification.pixelDiff))
                            // Trust pixel diff if gesture was dispatched; AND-combine with gesture success
                            gestureSuccess && verification.changed
                        } else {
                            // No region available (e.g. Back action, or a11y node not found)
                            delay(SCREEN_SETTLE_MS)
                            gestureSuccess
                        }
                    }

                    // ── 6. EVALUATE — assign reward ───────────────────────────
                    // Base reward:
                    //   learn-only → 0.6 (unverified; LlmRewardEnricher rescores at training)
                    //   verified success → +1.0
                    //   verified failure → -0.5
                    // Label boost: if the action targeted a known labeled element,
                    //   add importanceScore/10 (0.0–1.0) to the reward.
                    //   Human-verified labels are ground truth — reward correct use.
                    val baseReward = when {
                        learnOnly     -> 0.6
                        actionSuccess -> 1.0
                        else          -> -0.5
                    }
                    val actedNodeId = extractNodeId(actionJson)
                    val labelBoost = if (actionSuccess && screenLabels.isNotEmpty() && actedNodeId != null) {
                        screenLabels.firstOrNull { label ->
                            actionJson.contains(label.name, ignoreCase = true) ||
                            actionJson.contains(label.ocrText.take(20), ignoreCase = true)
                        }?.let { it.importanceScore / 10.0 } ?: 0.0
                    } else 0.0
                    val reward = baseReward + labelBoost
                    val result = if (actionSuccess) "success" else "failure"

                    // ── 7. STORE — persist experience ────────────────────────
                    store.save(ExperienceStore.ExperienceTuple(
                        appPackage    = snapshot.appPackage,
                        taskType      = goal.take(60),
                        screenSummary = snapshot.toLlmString().take(500),
                        actionJson    = actionJson,
                        result        = result,
                        reward        = reward,
                        isEdgeCase    = !actionSuccess
                    ))

                    // ── Phase 14.4: Log step to progress.txt ──────────────────
                    // Appended immediately after SQLite write so even a crash between
                    // steps leaves a complete record for the next session to resume from.
                    ProgressPersistence.logStep(
                        context    = context,
                        stepNum    = state.stepCount + 1,
                        actionJson = actionJson,
                        result     = result
                    )

                    // ── Phase 15: Track elements for AppSkillRegistry ──────────
                    // Collect element names the agent acted on for frequency stats.
                    extractNodeId(actionJson)?.let { nid ->
                        if (nid.isNotBlank() && nid != "null") elementsTouched.add(nid)
                    }
                    // Track the app we were interacting with across game-mode switches
                    lastAppPackage = snapshot.appPackage

                    // ── 8. EMIT ───────────────────────────────────────────────
                    val newStep = state.stepCount + 1
                    state = state.copy(
                        stepCount  = newStep,
                        lastAction = actionJson
                    )

                    val actionData = mapOf(
                        "tool"        to (extractTool(actionJson) ?: "unknown"),
                        "nodeId"      to (extractNodeId(actionJson) ?: ""),
                        "success"     to actionSuccess,
                        "reward"      to reward,
                        "stepCount"   to newStep,
                        "appPackage"  to snapshot.appPackage,
                        "timestamp"   to System.currentTimeMillis()
                    )
                    onEvent?.invoke("action_performed", actionData)
                    AgentEventBus.emit("action_performed", actionData)
                    emitStatus()

                    delay(STEP_DELAY_MS)
                }

                if (state.stepCount >= MAX_STEPS) {
                    state = state.copy(status = Status.DONE, lastError = "max_steps_reached")
                    ProgressPersistence.logNote(context, "Max steps ($MAX_STEPS) reached — task abandoned")
                    ProgressPersistence.logTaskEnd(context, goal, succeeded = false)
                    SustainedPerformanceManager.disable()
                    emitStatus()
                    // Phase 15: record failure, then chain next task if queued
                    recordAndChain(context, goal, lastAppPackage, succeeded = false,
                        stepsTaken = state.stepCount, elementsTouched = elementsTouched)
                }

            } catch (e: Exception) {
                state = state.copy(status = Status.ERROR, lastError = e.message ?: "unknown error")
                ProgressPersistence.logNote(context, "EXCEPTION: ${e.message ?: "unknown error"}")
                ProgressPersistence.logTaskEnd(context, goal, succeeded = false)
                SustainedPerformanceManager.disable()
                emitStatus()
                // Phase 15: record failure, then chain next task if queued
                recordAndChain(context, goal, lastAppPackage, succeeded = false,
                    stepsTaken = state.stepCount, elementsTouched = elementsTouched)
            }
        }
    }

    fun stop() {
        val currentGoal = state.goal
        loopJob?.cancel()
        GameLoop.stop()
        state = state.copy(status = Status.IDLE, gameMode = "none")
        SustainedPerformanceManager.disable()
        MonitoringPusher.stop()
        emitStatus()
    }

    fun pause() {
        loopJob?.cancel()
        state = state.copy(status = Status.PAUSED)
        emitStatus()
    }

    fun isRunning(): Boolean = state.status == Status.RUNNING

    private fun emitStatus() {
        val data = mapOf(
            "status"      to state.status.name.lowercase(),
            "currentTask" to state.goal,
            "currentApp"  to state.appPackage,
            "stepCount"   to state.stepCount,
            "lastAction"  to state.lastAction,
            "lastError"   to state.lastError,
            "gameMode"    to state.gameMode
        )
        onEvent?.invoke("agent_status_changed", data)
        AgentEventBus.emit("agent_status_changed", data)
    }

    private fun extractTool(json: String): String? =
        Regex("\"tool\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    private fun extractNodeId(json: String): String? =
        Regex("\"node_id\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    private fun extractWaitDuration(json: String): Long {
        val ms = Regex("\"duration_ms\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()
        return (ms ?: 500L).coerceIn(100L, 5000L)
    }

    // ─── Phase 15 helpers ─────────────────────────────────────────────────────

    /**
     * Record this task's outcome in AppSkillRegistry, then check TaskQueueManager for
     * the next queued task and auto-start it if the engine is still available.
     *
     * This is the task-chaining entry point: called at all three loop exits
     * (Done, max-steps, exception). Runs in the existing coroutine scope.
     */
    private fun recordAndChain(
        context: Context,
        goal: String,
        appPackage: String,
        succeeded: Boolean,
        stepsTaken: Int,
        elementsTouched: List<String>,
    ) {
        scope.launch(Dispatchers.IO) {
            // 1. Update the skill registry for this app
            runCatching {
                AppSkillRegistry.getInstance(context).recordTaskOutcome(
                    appPackage      = appPackage,
                    appName         = "",       // no appName field on ScreenSnapshot; registry uses pkg suffix
                    goal            = goal,
                    succeeded       = succeeded,
                    stepsTaken      = stepsTaken,
                    elementsTouched = elementsTouched,
                )
            }.onFailure { e ->
                Log.w("AgentLoop", "AppSkillRegistry update failed: ${e.message}")
            }

            // 2. Emit skill_updated event so JS Modules screen refreshes
            val skill = runCatching { AppSkillRegistry.getInstance(context).get(appPackage) }.getOrNull()
            if (skill != null) {
                val data = mapOf(
                    "appPackage"  to skill.appPackage,
                    "taskSuccess" to skill.taskSuccess,
                    "taskFailure" to skill.taskFailure,
                    "successRate" to skill.successRate,
                )
                onEvent?.invoke("skill_updated", data)
                AgentEventBus.emit("skill_updated", data)
            }

            // 3. Auto-dequeue and start the next queued task — task chaining
            val next = runCatching { TaskQueueManager.dequeue(context) }.getOrNull()
            if (next != null) {
                Log.i("AgentLoop", "Task chaining: starting next queued task \"${next.goal}\" (${next.appPackage})")

                // Notify JS that task chaining is advancing
                val chainData = mapOf(
                    "taskId"     to next.id,
                    "goal"       to next.goal,
                    "appPackage" to next.appPackage,
                    "queueSize"  to TaskQueueManager.size(context)
                )
                onEvent?.invoke("task_chain_advanced", chainData)
                AgentEventBus.emit("task_chain_advanced", chainData)

                // Small delay so the previous task's UI updates settle before starting
                delay(1500L)

                // Start the next task on the main scope
                start(context, next.goal, next.appPackage)
            }
        }
    }
}
