package com.ariaagent.mobile.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ariaagent.mobile.core.agent.AgentLoop
import com.ariaagent.mobile.core.agent.AppSkillRegistry
import com.ariaagent.mobile.core.agent.TaskQueueManager
import com.ariaagent.mobile.core.ai.ChatContextBuilder
import com.ariaagent.mobile.core.ai.LlamaEngine
import com.ariaagent.mobile.core.ai.ModelDownloadService
import com.ariaagent.mobile.core.ai.ModelManager
import com.ariaagent.mobile.core.ai.Sam2Engine
import com.ariaagent.mobile.core.ai.VisionEngine
import com.ariaagent.mobile.core.config.AriaConfig
import com.ariaagent.mobile.core.config.ConfigStore
import com.ariaagent.mobile.core.config.SafetyConfigStore
import com.ariaagent.mobile.core.events.AgentEventBus
import com.ariaagent.mobile.core.memory.EmbeddingModelManager
import com.ariaagent.mobile.core.memory.ExperienceStore
import com.ariaagent.mobile.core.memory.ObjectLabelStore
import com.ariaagent.mobile.core.monitoring.LocalDeviceServer
import com.ariaagent.mobile.core.monitoring.MonitoringPusher
import com.ariaagent.mobile.core.ocr.OcrEngine
import com.ariaagent.mobile.core.perception.ObjectDetectorEngine
import com.ariaagent.mobile.core.perception.ScreenObserver
import com.ariaagent.mobile.core.persistence.ProgressPersistence
import com.ariaagent.mobile.core.rl.IrlModule
import com.ariaagent.mobile.core.rl.LoraTrainer
import com.ariaagent.mobile.core.rl.PolicyNetwork
import com.ariaagent.mobile.system.AgentForegroundService
import com.ariaagent.mobile.system.accessibility.AgentAccessibilityService
import com.ariaagent.mobile.system.screen.ScreenCaptureService
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── UI state data classes ────────────────────────────────────────────────────

data class AgentUiState(
    val status: String               = "idle",
    val currentTask: String          = "",
    val currentApp: String           = "",
    val stepCount: Int               = 0,
    val lastAction: String           = "",
    val lastError: String            = "",
    val gameMode: String             = "none",
    val tokenRate: Double            = 0.0,
    val modelReady: Boolean          = false,
    val modelLoaded: Boolean         = false,
    val accessibilityActive: Boolean = false,
    val screenCaptureActive: Boolean = false,
)

data class ActionLogEntry(
    val id: Long,
    val tool: String,
    val nodeId: String,
    val success: Boolean,
    val reward: Double,
    val stepCount: Int,
    val appPackage: String,
    val timestamp: Long,
)

data class ThermalUiState(
    val level: String          = "safe",
    val inferenceSafe: Boolean = true,
    val trainingSafe: Boolean  = true,
    val emergency: Boolean     = false,
)

data class LearningUiState(
    val loraVersion: Int      = 0,
    val policyVersion: Int    = 0,
    val adamStep: Int         = 0,
    val lastPolicyLoss: Double = 0.0,
    val untrainedSamples: Int = 0,
)

data class StepUiState(
    val stepNumber: Int  = 0,
    val activity: String = "idle",
)

data class ModuleUiState(
    val modelReady: Boolean           = false,
    val modelLoaded: Boolean          = false,
    val tokensPerSecond: Double       = 0.0,
    val ocrReady: Boolean             = false,
    val detectorReady: Boolean        = false,
    val detectorSizeMb: Float         = 0f,
    val embeddingCount: Int           = 0,
    val labelCount: Int               = 0,
    val accessibilityGranted: Boolean = false,
    val screenCaptureGranted: Boolean = false,
    val episodesRun: Int              = 0,
    val adapterLoaded: Boolean        = false,
    val loraVersion: Int              = 0,
    // LLM download progress (driven by ModelDownloadService via AgentEventBus)
    val llmDownloadPercent: Int       = 0,
    val llmDownloadMb: Double         = 0.0,
    val llmDownloadTotalMb: Double    = 0.0,
    val llmDownloadSpeedMbps: Double  = 0.0,
    val llmDownloadError: String?     = null,
    // Embedding model (MiniLM ONNX ~23 MB)
    val embeddingReady: Boolean       = false,
    val embeddingVocabReady: Boolean  = false,
    val embeddingDownloadedMb: Float  = 0f,
    // Local monitoring server
    val localServerRunning: Boolean   = false,
    val localServerUrl: String        = "",
    // Vision model — SmolVLM-256M + mmproj (Phase 17)
    val visionReady: Boolean          = false,
    val visionLoaded: Boolean         = false,
    val visionModelDownloadedMb: Float = 0f,
    val mmProjDownloadedMb: Float     = 0f,
    val visionDownloadPercent: Int    = 0,
    val visionDownloadError: String?  = null,
    // SAM2 / MobileSAM pixel segmentation encoder (Phase 18)
    val sam2Ready: Boolean            = false,
    val sam2Loaded: Boolean           = false,
    val sam2DownloadedMb: Float       = 0f,
    val sam2DownloadPercent: Int      = 0,
    val sam2DownloadError: String?    = null,
)

/** ExperienceStore breakdown for ActivityScreen. */
data class MemoryStatsUi(
    val total: Int       = 0,
    val success: Int     = 0,
    val failure: Int     = 0,
    val edgeCase: Int    = 0,
    val untrained: Int   = 0,
    val enrichedLabels: Int = 0,
    val totalLabels: Int    = 0,
)

/** Phase 15: mirrors TaskQueueManager.QueuedTask for Compose UI. */
data class QueuedTaskItem(
    val id: String,
    val goal: String,
    val appPackage: String,
    val priority: Int,
    val enqueuedAt: Long,
)

/** Phase 15: mirrors AppSkillRegistry.AppSkill for Compose UI. */
data class AppSkillItem(
    val appPackage: String,
    val appName: String,
    val taskSuccess: Int,
    val taskFailure: Int,
    val totalSteps: Int,
    val successRate: Float,
    val avgSteps: Float,
    val learnedElements: List<String>,
    val promptHint: String,
    val lastSeen: Long,
)

/** Phase 6/8: game loop metrics for Dashboard. */
data class GameLoopUiState(
    val isActive: Boolean    = false,
    val gameType: String     = "none",
    val episodeCount: Int    = 0,
    val stepCount: Int       = 0,
    val currentScore: Double = 0.0,
    val highScore: Double    = 0.0,
    val totalReward: Double  = 0.0,
    val lastAction: String   = "",
    val isGameOver: Boolean  = false,
)

/** Memory entry shown in ActivityScreen Memory tab. Mapped from ExperienceStore.ExperienceTuple. */
data class MemoryEntry(
    val id         : String,
    val summary    : String,
    val app        : String,
    val taskType   : String,
    val result     : String,   // "success" | "failure"
    val reward     : Double,
    val isEdgeCase : Boolean,
    val timestamp  : Long,
)

// ── Migration Phase 5: Chat ───────────────────────────────────────────────────

/** A single message in the chat conversation. role: "user" | "aria" | "system" */
data class ChatMessageItem(
    val id: String,
    val role: String,
    val text: String,
    val tps: Double = 0.0,
    val ts: Long = System.currentTimeMillis(),
)

// ── Migration Phase 6: Train ──────────────────────────────────────────────────

data class LearningStatusUi(
    val loraVersion: Int,
    val latestAdapterPath: String,
    val adapterExists: Boolean,
    val untrainedSamples: Int,
    val policyReady: Boolean,
    val adamStep: Int,
    val lastPolicyLoss: Double,
    val lastTrainedAt: Long,
)

data class RlResultUi(
    val success: Boolean,
    val samplesUsed: Int,
    val adapterPath: String,
    val loraVersion: Int,
    val errorMessage: String,
)

data class IrlResultUi(
    val framesProcessed: Int,
    val tuplesExtracted: Int,
    val llmAssistedCount: Int,
    val errorMessage: String,
)

// ── Migration Phase 7: Labeler ────────────────────────────────────────────────

data class ScreenCaptureUi(
    val imagePath: String,
    val appPackage: String,
    val screenHash: String,
    val ocrText: String,
    val a11yTree: String,
)

/** Safety configuration — persisted via SafetyConfigStore. */
data class SafetyConfig(
    val globalKillActive: Boolean  = false,
    val confirmMode: Boolean       = false,
    val blockedPackages: Set<String> = emptySet(),
    val allowlistMode: Boolean     = false,
    val allowedPackages: Set<String> = emptySet(),
)

/** One LoRA adapter checkpoint found on-disk. */
data class LoraCheckpointItem(
    val version: Int,
    val adapterPath: String,
    val sizeKb: Long,
    val createdAt: Long,
    val isLatest: Boolean,
)

/** Live progress during a LoRA training run. */
data class LoraTrainingProgress(
    val percentComplete: Int = 0,
    val currentLoss: Double  = 0.0,
    val samplesUsed: Int     = 0,
    val phase: String        = "preparing",
)

/** Phase 15: notification shown when ARIA auto-chains to the next queued task. */
data class ChainedTaskItem(
    val taskId: String,
    val goal: String,
    val appPackage: String,
    val queueSize: Int,
    val timestamp: Long,
)

/**
 * AgentViewModel — primary state holder for the Jetpack Compose UI.
 *
 * Phase 11: initial implementation.
 * Phase 15 update: adds task queue, app skills, game loop, and chained-task state.
 *
 * Subscribes to AgentEventBus (Kotlin SharedFlow) — NO React Native bridge, NO JS.
 *
 * Event wiring:
 *   agent_status_changed    → agentState
 *   action_performed        → actionLogs (prepend, max 200)
 *   token_generated         → agentState.tokenRate + streamBuffer
 *   step_started            → stepState
 *   thermal_status_changed  → thermalState
 *   learning_cycle_complete → learningState + refreshModuleState
 *   model_download_complete → refreshModuleState
 *   game_loop_status        → gameLoopState          [Phase 15]
 *   skill_updated           → appSkills merge        [Phase 15]
 *   task_chain_advanced     → chainedTask + refresh  [Phase 15]
 *   config_updated          → config (via ConfigStore flow)
 */
class AgentViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    // ─── Observable state ─────────────────────────────────────────────────────

    private val _agentState   = MutableStateFlow(AgentUiState())
    val agentState: StateFlow<AgentUiState> = _agentState.asStateFlow()

    private val _actionLogs   = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    val actionLogs: StateFlow<List<ActionLogEntry>> = _actionLogs.asStateFlow()

    private val _thermalState = MutableStateFlow(ThermalUiState())
    val thermalState: StateFlow<ThermalUiState> = _thermalState.asStateFlow()

    private val _learningState = MutableStateFlow(LearningUiState())
    val learningState: StateFlow<LearningUiState> = _learningState.asStateFlow()

    private val _stepState    = MutableStateFlow(StepUiState())
    val stepState: StateFlow<StepUiState> = _stepState.asStateFlow()

    private val _moduleState  = MutableStateFlow(ModuleUiState())
    val moduleState: StateFlow<ModuleUiState> = _moduleState.asStateFlow()

    private val _streamBuffer = MutableStateFlow("")
    val streamBuffer: StateFlow<String> = _streamBuffer.asStateFlow()

    // Phase 15: task queue
    private val _taskQueue = MutableStateFlow<List<QueuedTaskItem>>(emptyList())
    val taskQueue: StateFlow<List<QueuedTaskItem>> = _taskQueue.asStateFlow()

    // Phase 15: app skills
    private val _appSkills = MutableStateFlow<List<AppSkillItem>>(emptyList())
    val appSkills: StateFlow<List<AppSkillItem>> = _appSkills.asStateFlow()

    // Phase 6/8: game loop
    private val _gameLoopState = MutableStateFlow<GameLoopUiState?>(null)
    val gameLoopState: StateFlow<GameLoopUiState?> = _gameLoopState.asStateFlow()

    // Phase 15: chained task notification
    private val _chainedTask = MutableStateFlow<ChainedTaskItem?>(null)
    val chainedTask: StateFlow<ChainedTaskItem?> = _chainedTask.asStateFlow()

    // Migration Phase 3: memory entries for ActivityScreen Memory tab
    private val _memoryEntries = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memoryEntries: StateFlow<List<MemoryEntry>> = _memoryEntries.asStateFlow()

    // ── Migration Phase 5: Chat ───────────────────────────────────────────────

    private val welcomeChatMsg = ChatMessageItem(
        id   = "welcome",
        role = "system",
        text = "Chat with ARIA — your messages are sent to the on-device LLM with full context: agent state, memory, task queue and app skills injected automatically.",
        ts   = 0L,
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessageItem>>(listOf(welcomeChatMsg))
    val chatMessages: StateFlow<List<ChatMessageItem>> = _chatMessages.asStateFlow()

    private val _chatThinking = MutableStateFlow(false)
    val chatThinking: StateFlow<Boolean> = _chatThinking.asStateFlow()

    // ── Migration Phase 6: Train ──────────────────────────────────────────────

    private val _learningStatusUi = MutableStateFlow<LearningStatusUi?>(null)
    val learningStatusUi: StateFlow<LearningStatusUi?> = _learningStatusUi.asStateFlow()

    private val _rlRunning = MutableStateFlow(false)
    val rlRunning: StateFlow<Boolean> = _rlRunning.asStateFlow()

    private val _rlResult = MutableStateFlow<RlResultUi?>(null)
    val rlResult: StateFlow<RlResultUi?> = _rlResult.asStateFlow()

    private val _irlRunning = MutableStateFlow(false)
    val irlRunning: StateFlow<Boolean> = _irlRunning.asStateFlow()

    private val _irlResult = MutableStateFlow<IrlResultUi?>(null)
    val irlResult: StateFlow<IrlResultUi?> = _irlResult.asStateFlow()

    private val _autoScheduleRl = MutableStateFlow(false)
    val autoScheduleRl: StateFlow<Boolean> = _autoScheduleRl.asStateFlow()

    // ── Migration Phase 7: Labeler ────────────────────────────────────────────

    private val _labelerCapture = MutableStateFlow<ScreenCaptureUi?>(null)
    val labelerCapture: StateFlow<ScreenCaptureUi?> = _labelerCapture.asStateFlow()

    private val _labelerLabels = MutableStateFlow<List<ObjectLabelStore.ObjectLabel>>(emptyList())
    val labelerLabels: StateFlow<List<ObjectLabelStore.ObjectLabel>> = _labelerLabels.asStateFlow()

    private val _labelerCapturing = MutableStateFlow(false)
    val labelerCapturing: StateFlow<Boolean> = _labelerCapturing.asStateFlow()

    private val _labelerDetecting = MutableStateFlow(false)
    val labelerDetecting: StateFlow<Boolean> = _labelerDetecting.asStateFlow()

    private val _labelerEnriching = MutableStateFlow(false)
    val labelerEnriching: StateFlow<Boolean> = _labelerEnriching.asStateFlow()

    private val _labelerSaving = MutableStateFlow(false)
    val labelerSaving: StateFlow<Boolean> = _labelerSaving.asStateFlow()

    private val _labelerError = MutableStateFlow<String?>(null)
    val labelerError: StateFlow<String?> = _labelerError.asStateFlow()

    private val _labelerSaveSuccess = MutableStateFlow(false)
    val labelerSaveSuccess: StateFlow<Boolean> = _labelerSaveSuccess.asStateFlow()

    // ── Screen capture permission request (Fix 1) ─────────────────────────────

    private val _screenCaptureRequestFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val screenCaptureRequestFlow: SharedFlow<Unit> = _screenCaptureRequestFlow.asSharedFlow()

    // ── Model download state (Fix 2) ──────────────────────────────────────────

    private val _llmDownloading = MutableStateFlow(false)
    val llmDownloading: StateFlow<Boolean> = _llmDownloading.asStateFlow()

    private val _detectorDownloading = MutableStateFlow(false)
    val detectorDownloading: StateFlow<Boolean> = _detectorDownloading.asStateFlow()

    private val _embeddingDownloading = MutableStateFlow(false)
    val embeddingDownloading: StateFlow<Boolean> = _embeddingDownloading.asStateFlow()

    private val _visionDownloading = MutableStateFlow(false)
    val visionDownloading: StateFlow<Boolean> = _visionDownloading.asStateFlow()

    private val _visionLoading = MutableStateFlow(false)
    val visionLoading: StateFlow<Boolean> = _visionLoading.asStateFlow()

    private val _sam2Downloading = MutableStateFlow(false)
    val sam2Downloading: StateFlow<Boolean> = _sam2Downloading.asStateFlow()

    private val _sam2Loading = MutableStateFlow(false)
    val sam2Loading: StateFlow<Boolean> = _sam2Loading.asStateFlow()

    private val _memoryStats = MutableStateFlow(MemoryStatsUi())
    val memoryStats: StateFlow<MemoryStatsUi> = _memoryStats.asStateFlow()

    private val _allLabels = MutableStateFlow<List<ObjectLabelStore.ObjectLabel>>(emptyList())
    val allLabels: StateFlow<List<ObjectLabelStore.ObjectLabel>> = _allLabels.asStateFlow()

    // ── Onboarding ────────────────────────────────────────────────────────────
    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    // ── Safety config ─────────────────────────────────────────────────────────
    private val _safetyConfig = MutableStateFlow(SafetyConfig())
    val safetyConfig: StateFlow<SafetyConfig> = _safetyConfig.asStateFlow()

    // ── LoRA training history + live progress ─────────────────────────────────
    private val _loraHistory = MutableStateFlow<List<LoraCheckpointItem>>(emptyList())
    val loraHistory: StateFlow<List<LoraCheckpointItem>> = _loraHistory.asStateFlow()

    private val _loraTrainingProgress = MutableStateFlow<LoraTrainingProgress?>(null)
    val loraTrainingProgress: StateFlow<LoraTrainingProgress?> = _loraTrainingProgress.asStateFlow()

    /** Config — reactive DataStore flow, auto-updates on any config change. */
    val config: StateFlow<AriaConfig> = ConfigStore.flow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AriaConfig())

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        refreshModuleState()
        refreshTaskQueue()
        refreshAppSkills()
        checkOnboardingComplete()
        viewModelScope.launch(Dispatchers.IO) {
            _safetyConfig.value = SafetyConfigStore.load(context)
        }
        refreshLoraHistory()

        // Auto-load vision model on startup if files are already on disk.
        // This pre-warms the ~200 MB model so the first agent step does not
        // pay the cold-start latency cost (~2–4 s on Exynos 9611).
        viewModelScope.launch(Dispatchers.IO) {
            val visionEngine = com.ariaagent.mobile.core.ai.VisionEngine
            if (visionEngine.isVisionModelReady(context) && !LlamaEngine.isVisionLoaded()) {
                android.util.Log.i("AgentViewModel", "Auto-loading vision model (files present on disk)…")
                _visionLoading.value = true
                runCatching { visionEngine.ensureLoaded(context) }
                _visionLoading.value = false
                refreshModuleState()
            }
        }

        viewModelScope.launch {
            AgentEventBus.flow.collect { (name, data) ->
                when (name) {
                    "agent_status_changed"    -> handleStatusChanged(data)
                    "action_performed"        -> handleActionPerformed(data)
                    "token_generated"         -> handleTokenGenerated(data)
                    "step_started"            -> handleStepStarted(data)
                    "thermal_status_changed"  -> handleThermalChanged(data)
                    "learning_cycle_complete"  -> handleLearningComplete(data)
                    "model_download_progress"  -> handleLlmDownloadProgress(data)
                    "model_download_complete"  -> {
                        _llmDownloading.value = false
                        _moduleState.update { it.copy(llmDownloadPercent = 100, llmDownloadError = null) }
                        refreshModuleState()
                    }
                    "model_download_error"     -> {
                        _llmDownloading.value = false
                        _moduleState.update { it.copy(llmDownloadError = data["error"] as? String ?: "Download failed") }
                    }
                    "game_loop_status"         -> handleGameLoopStatus(data)
                    "skill_updated"           -> handleSkillUpdated(data)
                    "task_chain_advanced"     -> handleTaskChainAdvanced(data)
                    "config_updated"          -> { /* handled by ConfigStore.flow() above */ }
                }
            }
        }
    }

    // ─── Event handlers ───────────────────────────────────────────────────────

    private fun handleStatusChanged(data: Map<String, Any>) {
        val status = data["status"] as? String ?: return
        _agentState.update { prev -> prev.copy(
            status      = status,
            currentTask = data["currentTask"] as? String ?: prev.currentTask,
            currentApp  = data["currentApp"]  as? String ?: prev.currentApp,
            stepCount   = (data["stepCount"] as? Int) ?: prev.stepCount,
            lastAction  = data["lastAction"]  as? String ?: prev.lastAction,
            lastError   = data["lastError"]   as? String ?: prev.lastError,
            gameMode    = data["gameMode"]    as? String ?: prev.gameMode,
        )}
        if (status == "idle" || status == "done" || status == "error") {
            _streamBuffer.value = ""
            _stepState.value = StepUiState()
        }
    }

    private fun handleActionPerformed(data: Map<String, Any>) {
        val entry = ActionLogEntry(
            id         = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
            tool       = data["tool"]       as? String  ?: "unknown",
            nodeId     = data["nodeId"]     as? String  ?: "",
            success    = data["success"]    as? Boolean ?: false,
            reward     = (data["reward"]    as? Double) ?: 0.0,
            stepCount  = (data["stepCount"] as? Int)    ?: 0,
            appPackage = data["appPackage"] as? String  ?: "",
            timestamp  = (data["timestamp"] as? Long)   ?: System.currentTimeMillis(),
        )
        _actionLogs.update { prev -> listOf(entry) + prev.take(199) }
        _streamBuffer.value = ""
    }

    private fun handleTokenGenerated(data: Map<String, Any>) {
        val token = data["token"] as? String ?: return
        val tps   = (data["tokensPerSecond"] as? Double) ?: 0.0
        _agentState.update { it.copy(tokenRate = tps) }
        _streamBuffer.update { it + token }
    }

    private fun handleStepStarted(data: Map<String, Any>) {
        _stepState.value = StepUiState(
            stepNumber = (data["stepNumber"] as? Int) ?: 0,
            activity   = data["activity"] as? String ?: "observe",
        )
        _streamBuffer.value = ""
    }

    private fun handleThermalChanged(data: Map<String, Any>) {
        _thermalState.value = ThermalUiState(
            level         = data["level"]         as? String  ?: "safe",
            inferenceSafe = data["inferenceSafe"] as? Boolean ?: true,
            trainingSafe  = data["trainingSafe"]  as? Boolean ?: true,
            emergency     = data["emergency"]     as? Boolean ?: false,
        )
    }

    private fun handleLearningComplete(data: Map<String, Any>) {
        _learningState.update { prev -> prev.copy(
            loraVersion   = (data["loraVersion"]   as? Int) ?: prev.loraVersion,
            policyVersion = (data["policyVersion"] as? Int) ?: prev.policyVersion,
            adamStep      = PolicyNetwork.adamStepCount,
            lastPolicyLoss = PolicyNetwork.lastPolicyLoss,
        )}
        refreshModuleState()
    }

    /** Phase 6/8: game loop event from GameLoop.kt via AgentEventBus. */
    private fun handleGameLoopStatus(data: Map<String, Any>) {
        _gameLoopState.value = GameLoopUiState(
            isActive     = data["isActive"]     as? Boolean ?: false,
            gameType     = data["gameType"]     as? String  ?: "none",
            episodeCount = (data["episodeCount"] as? Int)   ?: 0,
            stepCount    = (data["stepCount"]   as? Int)    ?: 0,
            currentScore = (data["currentScore"] as? Double) ?: 0.0,
            highScore    = (data["highScore"]   as? Double)  ?: 0.0,
            totalReward  = (data["totalReward"] as? Double)  ?: 0.0,
            lastAction   = data["lastAction"]   as? String  ?: "",
            isGameOver   = data["isGameOver"]   as? Boolean ?: false,
        )
    }

    /** Phase 15: skill_updated — merge changed entry into list without full reload. */
    private fun handleSkillUpdated(data: Map<String, Any>) {
        val pkg     = data["appPackage"]  as? String ?: return
        val success = (data["taskSuccess"] as? Int)  ?: 0
        val failure = (data["taskFailure"] as? Int)  ?: 0
        val rate    = (data["successRate"] as? Double)?.toFloat() ?: 0f
        _appSkills.update { prev ->
            if (prev.any { it.appPackage == pkg }) {
                prev.map { s ->
                    if (s.appPackage == pkg) s.copy(
                        taskSuccess = success,
                        taskFailure = failure,
                        successRate = rate,
                    ) else s
                }
            } else prev
        }
        refreshAppSkills()
    }

    /** Phase 15: task_chain_advanced — show banner and refresh task queue. */
    private fun handleTaskChainAdvanced(data: Map<String, Any>) {
        _chainedTask.value = ChainedTaskItem(
            taskId     = data["taskId"]     as? String ?: "",
            goal       = data["goal"]       as? String ?: "",
            appPackage = data["appPackage"] as? String ?: "",
            queueSize  = (data["queueSize"] as? Int)   ?: 0,
            timestamp  = System.currentTimeMillis(),
        )
        refreshTaskQueue()
    }

    // ─── Module state refresh ─────────────────────────────────────────────────

    private fun handleLlmDownloadProgress(data: Map<String, Any>) {
        val pct     = (data["percent"]      as? Int)    ?: 0
        val dlMb    = (data["downloadedMb"] as? Double) ?: 0.0
        val totalMb = (data["totalMb"]      as? Double) ?: 0.0
        val speed   = (data["speedMbps"]    as? Double) ?: 0.0
        _moduleState.update { it.copy(
            llmDownloadPercent   = pct,
            llmDownloadMb        = dlMb,
            llmDownloadTotalMb   = totalMb,
            llmDownloadSpeedMbps = speed,
            llmDownloadError     = null,
        )}
        _llmDownloading.value = true
    }

    fun refreshModuleState() {
        viewModelScope.launch(Dispatchers.IO) {
            val store = ExperienceStore.getInstance(context)
            val loraVer = LoraTrainer.currentVersion(context)
            val ocrAvailable = runCatching { OcrEngine.isAvailable(context) }.getOrDefault(true)
            val embReady     = EmbeddingModelManager.isModelReady(context)
            val embVocab     = EmbeddingModelManager.isVocabReady(context)
            val embMb        = EmbeddingModelManager.downloadedBytes(context).toFloat() / 1_048_576f
            _moduleState.update { prev -> prev.copy(
                modelReady           = ModelManager.isModelReady(context),
                modelLoaded          = LlamaEngine.isLoaded(),
                tokensPerSecond      = LlamaEngine.lastToksPerSec,
                ocrReady             = ocrAvailable,
                detectorReady        = ObjectDetectorEngine.isModelReady(context),
                detectorSizeMb       = ObjectDetectorEngine.downloadedBytes(context).toFloat() / 1_048_576f,
                embeddingCount       = store.count(),
                labelCount           = ObjectLabelStore.getInstance(context).count(),
                accessibilityGranted = AgentAccessibilityService.isActive,
                screenCaptureGranted = ScreenCaptureService.isActive,
                episodesRun          = store.countByResult("success") + store.countByResult("failure"),
                adapterLoaded        = LoraTrainer.latestAdapterPath(context) != null,
                loraVersion          = loraVer,
                embeddingReady       = embReady,
                embeddingVocabReady  = embVocab,
                embeddingDownloadedMb = embMb,
                localServerRunning    = LocalDeviceServer.running,
                localServerUrl       = if (LocalDeviceServer.running) LocalDeviceServer.serverUrl() else "",
                visionReady          = com.ariaagent.mobile.core.ai.VisionEngine.isVisionModelReady(context),
                visionLoaded         = LlamaEngine.isVisionLoaded(),
                visionModelDownloadedMb = com.ariaagent.mobile.core.ai.VisionEngine.visionModelDownloadedBytes(context).toFloat() / 1_048_576f,
                mmProjDownloadedMb   = com.ariaagent.mobile.core.ai.VisionEngine.mmProjDownloadedBytes(context).toFloat() / 1_048_576f,
                sam2Ready            = com.ariaagent.mobile.core.ai.Sam2Engine.isModelReady(context),
                sam2Loaded           = com.ariaagent.mobile.core.ai.Sam2Engine.isLoaded(),
                sam2DownloadedMb     = com.ariaagent.mobile.core.ai.Sam2Engine.downloadedBytes(context).toFloat() / 1_048_576f,
            )}
            _agentState.update { it.copy(
                modelReady          = ModelManager.isModelReady(context),
                modelLoaded         = LlamaEngine.isLoaded(),
                accessibilityActive = AgentAccessibilityService.isActive,
                screenCaptureActive = ScreenCaptureService.isActive,
            )}
            _learningState.update { it.copy(
                adamStep       = PolicyNetwork.adamStepCount,
                lastPolicyLoss = PolicyNetwork.lastPolicyLoss,
                untrainedSamples = store.getUntrainedSuccesses(1000).size,
                loraVersion    = loraVer,
            )}
        }
    }

    // ─── Fix 1: Screen capture permission ────────────────────────────────────

    /** Signal the Activity-level composable to launch the MediaProjection chooser. */
    fun requestScreenCapturePermission() {
        _screenCaptureRequestFlow.tryEmit(Unit)
    }

    /**
     * Called by the Activity after the user grants the MediaProjection permission.
     * Starts ScreenCaptureService with the resultCode + projection Intent.
     */
    fun onScreenCaptureResult(resultCode: Int, projectionData: Intent) {
        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("projectionData", projectionData)
        }
        context.startForegroundService(serviceIntent)
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            refreshModuleState()
        }
    }

    // ─── Fix 2: Model downloads ───────────────────────────────────────────────

    /** Start the LLM foreground download service. Safe to call when already downloaded. */
    fun downloadLlmModel() {
        if (_llmDownloading.value) return
        if (ModelManager.isModelReady(context)) {
            refreshModuleState()
            return
        }
        _llmDownloading.value = true
        _moduleState.update { it.copy(llmDownloadPercent = 0, llmDownloadError = null) }
        val intent = Intent(context, ModelDownloadService::class.java)
        context.startForegroundService(intent)
        // State is now driven entirely by model_download_progress / model_download_complete /
        // model_download_error events from ModelDownloadService via AgentEventBus.
    }

    /** Download the EfficientDet-Lite0 INT8 model (~4.4 MB) in the background. */
    fun downloadDetectorModel() {
        if (_detectorDownloading.value) return
        if (ObjectDetectorEngine.isModelReady(context)) {
            refreshModuleState()
            return
        }
        _detectorDownloading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ObjectDetectorEngine.ensureModel(context)
                refreshModuleState()
            } finally {
                _detectorDownloading.value = false
            }
        }
    }

    // ─── Gap 4: Embedding model download ─────────────────────────────────────

    /** Download the MiniLM ONNX embedding model + vocab (~23 MB total). */
    fun downloadEmbeddingModel() {
        if (_embeddingDownloading.value) return
        if (EmbeddingModelManager.isModelReady(context) && EmbeddingModelManager.isVocabReady(context)) {
            refreshModuleState()
            return
        }
        _embeddingDownloading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                EmbeddingModelManager.download(context)
                refreshModuleState()
            } finally {
                _embeddingDownloading.value = false
            }
        }
    }

    // ─── Phase 17: Vision model download ────────────────────────────────────

    /**
     * Download SmolVLM-256M-Instruct-Q4_K_M.gguf (~150 MB) and its mmproj
     * (~50 MB) to internal storage. Reports progress via moduleState.visionDownloadPercent.
     * Safe to call when already downloaded — exits immediately.
     */
    fun downloadVisionModel() {
        if (_visionDownloading.value) return
        val visionEngine = com.ariaagent.mobile.core.ai.VisionEngine
        if (visionEngine.isVisionModelReady(context)) {
            refreshModuleState()
            return
        }
        _visionDownloading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalBytes =
                    visionEngine.VISION_MODEL_MIN_BYTES + visionEngine.MMPROJ_MIN_BYTES

                // Download vision base model
                if (!visionEngine.isVisionModelFileReady(context)) {
                    var lastPercent = 0
                    visionEngine.downloadFile(
                        url     = visionEngine.VISION_MODEL_URL,
                        dest    = visionEngine.visionModelPath(context),
                        partial = visionEngine.visionModelPartial(context),
                        onProgress = { dl, _ ->
                            val pct = ((dl.toDouble() / totalBytes) * 50).toInt().coerceIn(0, 50)
                            if (pct != lastPercent) {
                                lastPercent = pct
                                _moduleState.update { it.copy(visionDownloadPercent = pct) }
                            }
                        }
                    )
                }

                // Download mmproj
                if (!visionEngine.isMmProjReady(context)) {
                    var lastPercent = 50
                    visionEngine.downloadFile(
                        url     = visionEngine.MMPROJ_URL,
                        dest    = visionEngine.mmProjPath(context),
                        partial = visionEngine.mmProjPartial(context),
                        onProgress = { dl, _ ->
                            val pct = (50 + (dl.toDouble() / totalBytes) * 50).toInt().coerceIn(50, 100)
                            if (pct != lastPercent) {
                                lastPercent = pct
                                _moduleState.update { it.copy(visionDownloadPercent = pct) }
                            }
                        }
                    )
                }

                _moduleState.update { it.copy(visionDownloadPercent = 100, visionDownloadError = null) }
                refreshModuleState()
            } catch (e: Exception) {
                _moduleState.update { it.copy(visionDownloadError = e.message ?: "Vision download failed") }
            } finally {
                _visionDownloading.value = false
            }
        }
    }

    /** Free the ~200 MB of RAM used by the loaded vision model + mmproj. */
    fun unloadVisionModel() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.ariaagent.mobile.core.ai.VisionEngine.unload() }
            refreshModuleState()
        }
    }

    /**
     * Explicitly load the vision model files into RAM.
     * Called when the user taps "Load Vision" on the Modules screen.
     * The model is also loaded lazily by VisionEngine.describe() during the
     * agent loop, but this lets users pre-warm it before starting a task.
     */
    fun loadVisionModel() {
        if (_visionLoading.value || LlamaEngine.isVisionLoaded()) return
        val visionEngine = com.ariaagent.mobile.core.ai.VisionEngine
        if (!visionEngine.isVisionModelReady(context)) return
        _visionLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { visionEngine.ensureLoaded(context) }
            _visionLoading.value = false
            refreshModuleState()
        }
    }

    // ─── Phase 18: SAM2 / MobileSAM pixel segmentation ──────────────────────

    /**
     * Download the MobileSAM ViT-Tiny encoder ONNX (~22 MB) to internal storage.
     * Reports download progress via moduleState.sam2DownloadPercent.
     * Safe to call when already downloaded — exits immediately.
     */
    fun downloadSam2Model() {
        if (_sam2Downloading.value) return
        val sam2 = com.ariaagent.mobile.core.ai.Sam2Engine
        if (sam2.isModelReady(context)) {
            refreshModuleState()
            return
        }
        _sam2Downloading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var lastPct = 0
                sam2.downloadModel(context) { downloaded, total ->
                    if (total > 0) {
                        val pct = ((downloaded.toDouble() / total) * 100).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            _moduleState.update { it.copy(sam2DownloadPercent = pct, sam2DownloadError = null) }
                        }
                    }
                }
                _moduleState.update { it.copy(sam2DownloadPercent = 100, sam2DownloadError = null) }
                refreshModuleState()
            } catch (e: Exception) {
                _moduleState.update { it.copy(sam2DownloadError = e.message ?: "SAM2 download failed") }
            } finally {
                _sam2Downloading.value = false
            }
        }
    }

    /** Load MobileSAM encoder ONNX session into RAM (~22 MB). */
    fun loadSam2Model() {
        if (_sam2Loading.value || com.ariaagent.mobile.core.ai.Sam2Engine.isLoaded()) return
        if (!com.ariaagent.mobile.core.ai.Sam2Engine.isModelReady(context)) return
        _sam2Loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.ariaagent.mobile.core.ai.Sam2Engine.ensureLoaded(context) }
            _sam2Loading.value = false
            refreshModuleState()
        }
    }

    /** Release MobileSAM encoder ONNX session and free ~22 MB of RAM. */
    fun unloadSam2Model() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.ariaagent.mobile.core.ai.Sam2Engine.unload() }
            refreshModuleState()
        }
    }

    // ─── Gap 5: Unload LLM from RAM ──────────────────────────────────────────

    /** Free the ~800 MB of RAM used by the loaded LLM when ARIA is not in use. */
    fun unloadLlmModel() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { LlamaEngine.unload() }
            refreshModuleState()
        }
    }

    // ─── Gap 6: Local monitoring server ──────────────────────────────────────

    /** Start or stop the embedded HTTP monitoring server + live pusher. */
    fun toggleLocalServer() {
        viewModelScope.launch(Dispatchers.IO) {
            if (LocalDeviceServer.running) {
                MonitoringPusher.stop()
                LocalDeviceServer.stop()
            } else {
                LocalDeviceServer.start()
                MonitoringPusher.start(context)
            }
            refreshModuleState()
        }
    }

    // ─── Gap 7: Object label management ──────────────────────────────────────

    /** Load all enriched labels for the ActivityScreen labels tab. */
    fun refreshAllLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            _allLabels.value = ObjectLabelStore.getInstance(context).getAllEnriched()
        }
    }

    /** Wipe every stored UI element label across all apps. */
    fun clearAllLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            ObjectLabelStore.getInstance(context).clearAll()
            _allLabels.value = emptyList()
            refreshModuleState()
        }
    }

    // ─── Gap 8: ExperienceStore breakdown stats ───────────────────────────────

    /** Compute success / failure / edge-case counts for the ActivityScreen stats bar. */
    fun refreshMemoryStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val store = ExperienceStore.getInstance(context)
            val labelStore = ObjectLabelStore.getInstance(context)
            _memoryStats.value = MemoryStatsUi(
                total       = store.count(),
                success     = store.countByResult("success"),
                failure     = store.countByResult("failure"),
                edgeCase    = store.edgeCaseCount(),
                untrained   = store.getUntrainedSuccesses(1000).size,
                enrichedLabels = labelStore.countEnriched(),
                totalLabels    = labelStore.count(),
            )
        }
    }

    // ─── Fix 3: Gallery fallback for Object Labeler ───────────────────────────

    /**
     * Load a gallery image URI as the labeler capture.
     * Copies the content:// URI to cache, runs OCR, and populates labelerCapture.
     * Called when screen capture is denied and user picks from gallery instead.
     */
    fun loadImageFromGallery(uri: android.net.Uri) {
        if (_labelerCapturing.value) return
        _labelerCapturing.value = true
        _labelerError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(context.cacheDir, "labeler_gallery_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(file).use { out -> input.copyTo(out) }
                }
                if (!file.exists() || file.length() == 0L) {
                    _labelerError.value = "Failed to read image from gallery."
                    return@launch
                }
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                val ocrText = if (bitmap != null) OcrEngine.run(bitmap) else ""
                val a11yTree = AgentAccessibilityService.getSemanticTree()
                val captureUi = ScreenCaptureUi(
                    imagePath  = file.absolutePath,
                    appPackage = "gallery",
                    screenHash = file.name,
                    ocrText    = ocrText.take(2000),
                    a11yTree   = a11yTree.take(2000),
                )
                _labelerCapture.value = captureUi
                _labelerLabels.value  = emptyList()
            } catch (e: Exception) {
                _labelerError.value = e.message ?: "Gallery import failed"
            } finally {
                _labelerCapturing.value = false
            }
        }
    }

    // ─── Phase 15: task queue ─────────────────────────────────────────────────

    fun refreshTaskQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = TaskQueueManager.getAll(context)
            _taskQueue.value = tasks.map { t ->
                QueuedTaskItem(
                    id         = t.id,
                    goal       = t.goal,
                    appPackage = t.appPackage,
                    priority   = t.priority,
                    enqueuedAt = t.enqueuedAt,
                )
            }
        }
    }

    fun enqueueTask(goal: String, appPackage: String = "", priority: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            TaskQueueManager.enqueue(context, goal, appPackage, priority)
            refreshTaskQueue()
        }
    }

    fun removeQueuedTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            TaskQueueManager.remove(context, taskId)
            _taskQueue.update { prev -> prev.filter { it.id != taskId } }
        }
    }

    fun clearTaskQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            TaskQueueManager.clear(context)
            _taskQueue.value = emptyList()
        }
    }

    // ─── Phase 15: app skills ─────────────────────────────────────────────────

    fun refreshAppSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            val skills = AppSkillRegistry.getInstance(context).getAll()
            _appSkills.value = skills.map { s ->
                AppSkillItem(
                    appPackage      = s.appPackage,
                    appName         = s.appName.ifBlank { s.appPackage.substringAfterLast('.') },
                    taskSuccess     = s.taskSuccess,
                    taskFailure     = s.taskFailure,
                    totalSteps      = s.totalSteps,
                    successRate     = s.successRate,
                    avgSteps        = s.avgStepsPerTask,
                    learnedElements = s.learnedElements.take(5),
                    promptHint      = s.promptHint,
                    lastSeen        = s.lastSeen,
                )
            }.sortedByDescending { it.lastSeen }
        }
    }

    fun clearAppSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            AppSkillRegistry.getInstance(context).clear()
            _appSkills.value = emptyList()
        }
    }

    /** Dismiss the chained task notification banner. */
    fun dismissChainNotification() {
        _chainedTask.value = null
    }

    // ─── Migration Phase 3: memory entries ───────────────────────────────────

    /** Load recent ExperienceStore tuples for the ActivityScreen Memory tab. */
    fun refreshMemoryEntries() {
        viewModelScope.launch(Dispatchers.IO) {
            val store = ExperienceStore.getInstance(context)
            _memoryEntries.value = store.getRecent(200).map { t ->
                MemoryEntry(
                    id         = t.id,
                    summary    = t.screenSummary.ifBlank { t.taskType },
                    app        = t.appPackage.substringAfterLast('.'),
                    taskType   = t.taskType,
                    result     = t.result,
                    reward     = t.reward,
                    isEdgeCase = t.isEdgeCase,
                    timestamp  = t.timestamp,
                )
            }
        }
    }

    // ─── Migration Phase 2/3: danger zone actions (added beyond RN) ──────────

    /**
     * Clear all experience store entries and embeddings.
     * Triggered by "Clear Memory" button in SettingsScreen and ActivityScreen.
     */
    fun clearMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            ExperienceStore.getInstance(context).clearAll()
            _memoryEntries.value = emptyList()
            refreshModuleState()
        }
    }

    /**
     * Full agent reset — clears experience store, progress persistence, task queue,
     * and app skill registry. LoRA adapter files on disk are preserved.
     * Triggered by "Reset Agent" button in SettingsScreen.
     */
    fun resetAgent() {
        viewModelScope.launch(Dispatchers.IO) {
            ExperienceStore.getInstance(context).clearAll()
            ProgressPersistence.clear(context)
            AppSkillRegistry.getInstance(context).clear()
            TaskQueueManager.clear(context)
            _memoryEntries.value = emptyList()
            _appSkills.value     = emptyList()
            _taskQueue.value     = emptyList()
            refreshModuleState()
        }
    }

    // ─── Agent control ────────────────────────────────────────────────────────

    /**
     * Load the LLM engine into memory using the current stored config.
     * Matches `loadModel()` in control.tsx.  Safe to call when already loaded.
     */
    fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = ConfigStore.getBlocking(context)
            runCatching {
                LlamaEngine.load(
                    path         = cfg.modelPath,
                    contextSize  = cfg.contextWindow,
                    nGpuLayers   = cfg.nGpuLayers,
                )
            }
            refreshModuleState()
        }
    }

    fun startAgent(goal: String, appPackage: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            AgentLoop.start(context, goal, appPackage)
        }
    }

    /**
     * Start the agent in learn-only mode: observe + reason, but dispatch NO gestures.
     * Matches `startLearnOnly()` in control.tsx / AgentForegroundService.
     */
    fun startLearnOnly(goal: String, appPackage: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            AgentForegroundService.startLearnOnly(context, goal, appPackage)
        }
    }

    fun stopAgent() {
        AgentLoop.stop()
        _streamBuffer.value = ""
        _stepState.value = StepUiState()
    }

    fun pauseAgent() {
        AgentLoop.pause()
    }

    fun saveConfig(newConfig: AriaConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ConfigStore.save(context, newConfig) }
            AgentEventBus.emit("config_updated", mapOf(
                "modelPath"        to newConfig.modelPath,
                "quantization"     to newConfig.quantization,
                "contextWindow"    to newConfig.contextWindow,
                "maxTokensPerTurn" to newConfig.maxTokensPerTurn,
                "temperatureX100"  to newConfig.temperatureX100,
                "nGpuLayers"       to newConfig.nGpuLayers,
                "rlEnabled"        to newConfig.rlEnabled,
                "loraAdapterPath"  to (newConfig.loraAdapterPath ?: ""),
            ))
        }
    }

    // ─── Migration Phase 5: Chat ──────────────────────────────────────────────

    /**
     * Send a user message through the on-device LLM.
     * Builds full context via ChatContextBuilder, then calls LlamaEngine.infer().
     * No bridge. No JS. Pure Kotlin.
     */
    fun sendChatMessage(text: String) {
        if (_chatThinking.value) return
        if (!LlamaEngine.isLoaded()) {
            _chatMessages.update { prev ->
                prev + ChatMessageItem(
                    id   = "sys-${System.currentTimeMillis()}",
                    role = "system",
                    text = "LLM is not loaded. Go to Control → Load LLM Engine first, then come back here.",
                )
            }
            return
        }

        val userMsg = ChatMessageItem(
            id   = "u-${System.currentTimeMillis()}",
            role = "user",
            text = text,
        )
        _chatMessages.update { prev -> prev + userMsg }
        _chatThinking.value = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val history = _chatMessages.value
                    .filter { it.role != "system" }
                    .takeLast(8)
                val historyJson = history.joinToString(",", "[", "]") {
                    """{"role":"${it.role}","text":"${it.text.replace("\"","'")}"}"""
                }
                val systemCtx = ChatContextBuilder.build(context, text, historyJson)
                val historyBlock = if (history.isNotEmpty()) {
                    "\n[CONVERSATION]\n" + history.joinToString("\n") {
                        "${if (it.role == "user") "User" else "ARIA"}: ${it.text}"
                    } + "\n"
                } else ""
                val prompt = "$systemCtx${historyBlock}\nUser: $text\nARIA:"

                val t0  = System.currentTimeMillis()
                val raw = LlamaEngine.infer(prompt, maxTokens = 512)
                val elapsed = (System.currentTimeMillis() - t0) / 1000.0
                val wordCount = raw.trim().split(Regex("\\s+")).size
                val tps = if (elapsed > 0) wordCount / elapsed else 0.0

                val response = raw
                    .replace(Regex("^ARIA:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^Assistant:\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { "(no response — try rephrasing)" }

                _chatMessages.update { prev ->
                    prev + ChatMessageItem(
                        id   = "a-${System.currentTimeMillis()}",
                        role = "aria",
                        text = response,
                        tps  = tps,
                    )
                }
            } catch (e: Exception) {
                _chatMessages.update { prev ->
                    prev + ChatMessageItem(
                        id   = "err-${System.currentTimeMillis()}",
                        role = "system",
                        text = "Inference error: ${e.message ?: "unknown"}",
                    )
                }
            } finally {
                _chatThinking.value = false
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(welcomeChatMsg)
        _chatThinking.value = false
    }

    // ─── Migration Phase 6: Train ─────────────────────────────────────────────

    fun refreshLearningStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val ver       = LoraTrainer.currentVersion(context)
            val path      = LoraTrainer.latestAdapterPath(context)
            val store     = ExperienceStore.getInstance(context)
            _learningStatusUi.value = LearningStatusUi(
                loraVersion        = ver,
                latestAdapterPath  = path ?: "",
                adapterExists      = path != null,
                untrainedSamples   = store.getUntrainedSuccesses(1000).size,
                policyReady        = PolicyNetwork.adamStepCount > 0,
                adamStep           = PolicyNetwork.adamStepCount,
                lastPolicyLoss     = PolicyNetwork.lastPolicyLoss,
                lastTrainedAt      = if (path != null) java.io.File(path).lastModified() else 0L,
            )
        }
    }

    fun runRlCycle() {
        if (_rlRunning.value) return
        _rlRunning.value = true
        _rlResult.value  = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val store  = ExperienceStore.getInstance(context)
                val cfg    = ConfigStore.getBlocking(context)
                val result = LoraTrainer.train(context, store, cfg.modelPath)
                _rlResult.value = RlResultUi(
                    success      = result.success,
                    samplesUsed  = result.samplesUsed,
                    adapterPath  = result.adapterPath,
                    loraVersion  = result.loraVersion,
                    errorMessage = result.errorMessage,
                )
                refreshLearningStatus()
                refreshModuleState()
            } catch (e: Exception) {
                _rlResult.value = RlResultUi(false, 0, "", 0, e.message ?: "unknown")
            } finally {
                _rlRunning.value = false
            }
        }
    }

    fun processIrlVideo(videoUri: String, goal: String, appPackage: String) {
        if (_irlRunning.value) return
        _irlRunning.value = true
        _irlResult.value  = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val resolvedPath = resolveContentUri(videoUri)
                val store  = ExperienceStore.getInstance(context)
                val result = IrlModule.processVideo(context, resolvedPath, goal, appPackage, store)
                _irlResult.value = IrlResultUi(
                    framesProcessed  = result.framesProcessed,
                    tuplesExtracted  = result.tuplesExtracted,
                    llmAssistedCount = result.llmAssistedCount,
                    errorMessage     = result.errorMessage,
                )
                refreshLearningStatus()
            } catch (e: Exception) {
                _irlResult.value = IrlResultUi(0, 0, 0, e.message ?: "unknown")
            } finally {
                _irlRunning.value = false
            }
        }
    }

    fun setAutoScheduleRl(enabled: Boolean) {
        _autoScheduleRl.value = enabled
        if (enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val store = ExperienceStore.getInstance(context)
                val untrained = store.getUntrainedSuccesses(1000).size
                if (untrained > 50 && !_rlRunning.value) {
                    runRlCycle()
                }
            }
        }
    }

    // ─── Migration Phase 7: Labeler ───────────────────────────────────────────

    fun captureScreenForLabeling() {
        if (_labelerCapturing.value) return
        _labelerCapturing.value = true
        _labelerError.value     = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val screenshot = ScreenCaptureService.captureLatest()
                    ?: run {
                        _labelerError.value = "Screen capture service not active — grant Media Projection permission first."
                        return@launch
                    }
                val snapshot = ScreenObserver.capture()
                val ocrText  = OcrEngine.run(screenshot)
                val a11yTree = AgentAccessibilityService.getSemanticTree()

                val file = java.io.File(context.cacheDir, "labeler_capture_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                }

                val captureUi = ScreenCaptureUi(
                    imagePath  = file.absolutePath,
                    appPackage = snapshot.appPackage,
                    screenHash = snapshot.screenHash(),
                    ocrText    = ocrText.take(2000),
                    a11yTree   = a11yTree.take(2000),
                )
                _labelerCapture.value = captureUi

                val store  = ObjectLabelStore.getInstance(context)
                val existing = store.getByScreen(captureUi.appPackage, captureUi.screenHash)
                _labelerLabels.value = existing
            } catch (e: Exception) {
                _labelerError.value = e.message ?: "Capture failed"
            } finally {
                _labelerCapturing.value = false
            }
        }
    }

    fun addLabelerPin(x: Float, y: Float) {
        val cap = _labelerCapture.value ?: return
        val newLabel = ObjectLabelStore.ObjectLabel(
            id          = "${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}",
            appPackage  = cap.appPackage,
            screenHash  = cap.screenHash,
            x           = x,
            y           = y,
            name        = "",
            context     = "",
            elementType = ObjectLabelStore.ElementType.BUTTON,
        )
        _labelerLabels.update { prev -> prev + newLabel }
    }

    fun updateLabelerLabel(updated: ObjectLabelStore.ObjectLabel) {
        _labelerLabels.update { prev ->
            prev.map { if (it.id == updated.id) updated else it }
        }
    }

    fun deleteLabelerLabel(id: String) {
        _labelerLabels.update { prev -> prev.filter { it.id != id } }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ObjectLabelStore.getInstance(context).delete(id) }
        }
    }

    fun autoDetectLabelerPins() {
        val cap = _labelerCapture.value ?: return
        if (_labelerDetecting.value) return
        _labelerDetecting.value = true
        _labelerError.value     = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val newLabels = mutableListOf<ObjectLabelStore.ObjectLabel>()

                val detections = runCatching {
                    ObjectDetectorEngine.detectFromPath(context, cap.imagePath)
                }.getOrDefault(emptyList())
                detections.forEach { det ->
                    newLabels.add(ObjectLabelStore.ObjectLabel(
                        id             = "${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}",
                        appPackage     = cap.appPackage,
                        screenHash     = cap.screenHash,
                        x              = det.normX,
                        y              = det.normY,
                        name           = det.label.replace("_", " ")
                            .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } },
                        context        = "EfficientDet detection",
                        elementType    = inferElementType(det.label),
                        importanceScore = maxOf(1, minOf(10, (det.confidence * 10).toInt())),
                    ))
                }

                val bitmap = runCatching {
                    android.graphics.BitmapFactory.decodeFile(cap.imagePath)
                }.getOrNull()
                if (bitmap != null) {
                    if (Sam2Engine.isLoaded()) {
                        val samRegions = runCatching {
                            Sam2Engine.segment(context, bitmap, topK = 8)
                        }.getOrDefault(emptyList())
                        val existingCoords = newLabels.map { it.x to it.y }
                        samRegions.forEach { region ->
                            val isDuplicate = existingCoords.any { (ex, ey) ->
                                Math.abs(ex - region.normX) < 0.05f && Math.abs(ey - region.normY) < 0.05f
                            }
                            if (!isDuplicate) {
                                val importance = maxOf(1, minOf(10, (region.score / 100f * 10f).toInt()))
                                newLabels.add(ObjectLabelStore.ObjectLabel(
                                    id             = "${System.currentTimeMillis()}_sam_${(Math.random() * 1000).toInt()}",
                                    appPackage     = cap.appPackage,
                                    screenHash     = cap.screenHash,
                                    x              = region.normX,
                                    y              = region.normY,
                                    name           = "",
                                    context        = "MobileSAM salient region",
                                    elementType    = ObjectLabelStore.ElementType.UNKNOWN,
                                    importanceScore = importance,
                                ))
                            }
                        }
                    }
                    bitmap.recycle()
                }

                if (newLabels.isEmpty()) {
                    _labelerError.value = "No objects detected. Try adding pins manually, or load SAM2/detector models."
                    return@launch
                }
                _labelerLabels.update { prev -> prev + newLabels }
            } catch (e: Exception) {
                _labelerError.value = e.message ?: "Auto-detect failed"
            } finally {
                _labelerDetecting.value = false
            }
        }
    }

    fun enrichAllLabelerPins() {
        val cap = _labelerCapture.value ?: return
        if (!LlamaEngine.isLoaded()) {
            _labelerError.value = "Load the LLM engine first (Control → Load LLM Engine)."
            return
        }
        if (_labelerEnriching.value) return
        _labelerEnriching.value = true
        _labelerError.value     = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val visionDesc = runCatching {
                    val bmp = android.graphics.BitmapFactory.decodeFile(cap.imagePath)
                    if (bmp != null && VisionEngine.isVisionModelReady(context)) {
                        VisionEngine.describe(
                            context    = context,
                            bitmap     = bmp,
                            goal       = "Describe this Android UI for element annotation",
                            screenHash = cap.screenHash,
                            maxTokens  = 128
                        ).also { bmp.recycle() }
                    } else ""
                }.getOrDefault("")

                val screenContext = buildString {
                    append(cap.ocrText.take(400))
                    if (cap.a11yTree.isNotBlank()) append("\n${cap.a11yTree.take(300)}")
                    if (visionDesc.isNotBlank()) append("\n[VISION] $visionDesc")
                }

                val enriched = _labelerLabels.value.map { label ->
                    val prompt = buildLabelEnrichPrompt(label, screenContext)
                    val raw    = LlamaEngine.infer(prompt, maxTokens = 150)
                    parseLabelEnrichOutput(raw, label)
                }
                _labelerLabels.value = enriched
            } catch (e: Exception) {
                _labelerError.value = e.message ?: "Enrichment failed"
            } finally {
                _labelerEnriching.value = false
            }
        }
    }

    fun saveLabelerLabels(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cap = _labelerCapture.value ?: return
        if (_labelerSaving.value) return
        _labelerSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val store = ObjectLabelStore.getInstance(context)
                store.saveAll(_labelerLabels.value)
                _labelerSaveSuccess.value = true
                refreshModuleState()
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                val msg = e.message ?: "Save failed"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onError(msg) }
            } finally {
                _labelerSaving.value = false
            }
        }
    }

    fun clearLabelerCapture() {
        _labelerCapture.value    = null
        _labelerLabels.value     = emptyList()
        _labelerError.value      = null
        _labelerSaveSuccess.value = false
    }

    fun dismissLabelerError() {
        _labelerError.value = null
    }

    private fun inferElementType(cocoLabel: String): ObjectLabelStore.ElementType {
        val l = cocoLabel.lowercase()
        return when {
            l.contains("cell phone") || l.contains("remote") ||
            l.contains("keyboard")   || l.contains("mouse")  -> ObjectLabelStore.ElementType.BUTTON
            l.contains("book") || l.contains("laptop") ||
            l.contains("tv")   || l.contains("monitor") -> ObjectLabelStore.ElementType.IMAGE
            l.contains("person") || l.contains("face")   -> ObjectLabelStore.ElementType.ICON
            else -> ObjectLabelStore.ElementType.UNKNOWN
        }
    }

    private fun buildLabelEnrichPrompt(
        label: ObjectLabelStore.ObjectLabel,
        screenContext: String,
    ): String = buildString {
        append("<|begin_of_text|>")
        append("<|start_header_id|>system<|end_header_id|>\n")
        append("You are an Android UI analyst. Analyze the given UI element and output JSON metadata for an autonomous agent.\n")
        append("Respond ONLY in JSON:\n")
        append("""{"meaning":"...","interactionHint":"...","reasoningContext":"...","importanceScore":7}""")
        append("\nRules: meaning≤20 words, interactionHint≤20 words, reasoningContext≤25 words, importanceScore 0-10.\n")
        append("<|eot_id|>\n")
        append("<|start_header_id|>user<|end_header_id|>\n")
        append("Element: \"${label.name}\" (${label.elementType.name.lowercase()})\n")
        append("User description: ${label.context}\n")
        if (label.ocrText.isNotBlank()) append("OCR text: ${label.ocrText.take(100)}\n")
        append("Screen context: ${screenContext.take(400)}\n")
        append("<|eot_id|>\n")
        append("<|start_header_id|>assistant<|end_header_id|>\n")
    }

    /** Copies a content:// URI to a temp file and returns its absolute path. */
    private fun resolveContentUri(uriString: String): String {
        if (!uriString.startsWith("content://")) return uriString
        val uri     = android.net.Uri.parse(uriString)
        val tmp     = java.io.File(context.cacheDir, "irl_video_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(tmp).use { out -> input.copyTo(out) }
        }
        return tmp.absolutePath
    }

    private fun parseLabelEnrichOutput(
        rawOutput: String,
        original: ObjectLabelStore.ObjectLabel,
    ): ObjectLabelStore.ObjectLabel {
        return try {
            val start = rawOutput.indexOfFirst { it == '{' }
            val end   = rawOutput.lastIndexOf('}')
            if (start == -1 || end <= start) return original
            val json = JSONObject(rawOutput.substring(start, end + 1))
            original.copy(
                meaning         = json.optString("meaning",         original.meaning).take(200),
                interactionHint = json.optString("interactionHint", original.interactionHint).take(200),
                reasoningContext = json.optString("reasoningContext", original.reasoningContext).take(250),
                importanceScore = json.optInt("importanceScore",    original.importanceScore).coerceIn(0, 10),
                isEnriched      = true,
                updatedAt       = System.currentTimeMillis(),
            )
        } catch (_: Exception) {
            original
        }
    }

    // ─── Onboarding ───────────────────────────────────────────────────────────

    fun checkOnboardingComplete() {
        val prefs = context.getSharedPreferences("aria_prefs", android.content.Context.MODE_PRIVATE)
        _onboardingComplete.value = prefs.getBoolean("onboarding_complete", false)
    }

    fun setOnboardingComplete() {
        context.getSharedPreferences("aria_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        _onboardingComplete.value = true
    }

    // ─── Safety Config ────────────────────────────────────────────────────────

    fun loadSafetyConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _safetyConfig.value = SafetyConfigStore.load(context)
        }
    }

    private fun persistSafety(config: SafetyConfig) {
        _safetyConfig.value = config
        viewModelScope.launch(Dispatchers.IO) { SafetyConfigStore.save(context, config) }
    }

    fun toggleGlobalKill() =
        persistSafety(_safetyConfig.value.copy(globalKillActive = !_safetyConfig.value.globalKillActive))

    fun setConfirmMode(enabled: Boolean) =
        persistSafety(_safetyConfig.value.copy(confirmMode = enabled))

    fun setAllowlistMode(enabled: Boolean) =
        persistSafety(_safetyConfig.value.copy(allowlistMode = enabled))

    fun addBlockedPackage(pkg: String) {
        if (pkg.isBlank()) return
        persistSafety(_safetyConfig.value.copy(blockedPackages = _safetyConfig.value.blockedPackages + pkg.trim()))
    }

    fun removeBlockedPackage(pkg: String) =
        persistSafety(_safetyConfig.value.copy(blockedPackages = _safetyConfig.value.blockedPackages - pkg))

    fun addAllowedPackage(pkg: String) {
        if (pkg.isBlank()) return
        persistSafety(_safetyConfig.value.copy(allowedPackages = _safetyConfig.value.allowedPackages + pkg.trim()))
    }

    fun removeAllowedPackage(pkg: String) =
        persistSafety(_safetyConfig.value.copy(allowedPackages = _safetyConfig.value.allowedPackages - pkg))

    // ─── LoRA Training History ────────────────────────────────────────────────

    fun refreshLoraHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val loraDir = java.io.File(context.filesDir, "lora")
            if (!loraDir.exists()) { _loraHistory.value = emptyList(); return@launch }
            val currentVer = LoraTrainer.currentVersion(context)
            val checkpoints = loraDir.listFiles()
                ?.filter { it.extension == "gguf" || it.extension == "bin" }
                ?.mapNotNull { f ->
                    val ver = Regex("adapter_v(\\d+)").find(f.name)
                        ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                    LoraCheckpointItem(
                        version     = ver,
                        adapterPath = f.absolutePath,
                        sizeKb      = f.length() / 1024L,
                        createdAt   = f.lastModified(),
                        isLatest    = ver == currentVer,
                    )
                }
                ?.sortedByDescending { it.version }
                ?: emptyList()
            _loraHistory.value = checkpoints
        }
    }

    /** Publish live LoRA training progress. Called by LoRA training wrapper in TrainScreen. */
    fun reportLoraTrainingProgress(pct: Int, loss: Double, samples: Int, phase: String) {
        _loraTrainingProgress.value = LoraTrainingProgress(
            percentComplete = pct,
            currentLoss     = loss,
            samplesUsed     = samples,
            phase           = phase,
        )
    }

    fun clearLoraTrainingProgress() {
        _loraTrainingProgress.value = null
        refreshLoraHistory()
    }
}
