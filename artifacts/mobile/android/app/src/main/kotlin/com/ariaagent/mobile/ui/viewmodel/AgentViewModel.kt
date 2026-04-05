package com.ariaagent.mobile.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ariaagent.mobile.core.agent.AgentLoop
import com.ariaagent.mobile.core.agent.AppSkillRegistry
import com.ariaagent.mobile.core.agent.TaskQueueManager
import com.ariaagent.mobile.core.ai.LlamaEngine
import com.ariaagent.mobile.core.ai.ModelManager
import com.ariaagent.mobile.core.config.AriaConfig
import com.ariaagent.mobile.core.config.ConfigStore
import com.ariaagent.mobile.core.events.AgentEventBus
import com.ariaagent.mobile.core.memory.ExperienceStore
import com.ariaagent.mobile.core.memory.ObjectLabelStore
import com.ariaagent.mobile.core.perception.ObjectDetectorEngine
import com.ariaagent.mobile.core.persistence.ProgressPersistence
import com.ariaagent.mobile.core.rl.LoraTrainer
import com.ariaagent.mobile.core.rl.PolicyNetwork
import com.ariaagent.mobile.system.AgentForegroundService
import com.ariaagent.mobile.system.accessibility.AgentAccessibilityService
import com.ariaagent.mobile.system.screen.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val ocrReady: Boolean             = true,
    val detectorReady: Boolean        = false,
    val detectorSizeMb: Float         = 0f,
    val embeddingCount: Int           = 0,
    val labelCount: Int               = 0,
    val accessibilityGranted: Boolean = false,
    val screenCaptureGranted: Boolean = false,
    val episodesRun: Int              = 0,
    val adapterLoaded: Boolean        = false,
    val loraVersion: Int              = 0,
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

    /** Config — reactive DataStore flow, auto-updates on any config change. */
    val config: StateFlow<AriaConfig> = ConfigStore.flow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AriaConfig())

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        refreshModuleState()
        refreshTaskQueue()
        refreshAppSkills()

        viewModelScope.launch {
            AgentEventBus.flow.collect { (name, data) ->
                when (name) {
                    "agent_status_changed"    -> handleStatusChanged(data)
                    "action_performed"        -> handleActionPerformed(data)
                    "token_generated"         -> handleTokenGenerated(data)
                    "step_started"            -> handleStepStarted(data)
                    "thermal_status_changed"  -> handleThermalChanged(data)
                    "learning_cycle_complete" -> handleLearningComplete(data)
                    "model_download_complete" -> refreshModuleState()
                    "game_loop_status"        -> handleGameLoopStatus(data)
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

    fun refreshModuleState() {
        viewModelScope.launch(Dispatchers.IO) {
            val store = ExperienceStore.getInstance(context)
            val loraVer = LoraTrainer.currentVersion(context)
            _moduleState.value = ModuleUiState(
                modelReady           = ModelManager.isModelReady(context),
                modelLoaded          = LlamaEngine.isLoaded(),
                tokensPerSecond      = LlamaEngine.lastToksPerSec,
                ocrReady             = true,
                detectorReady        = ObjectDetectorEngine.isModelReady(context),
                detectorSizeMb       = ObjectDetectorEngine.downloadedBytes(context).toFloat() / 1_048_576f,
                embeddingCount       = store.count(),
                labelCount           = ObjectLabelStore.getInstance(context).count(),
                accessibilityGranted = AgentAccessibilityService.isActive,
                screenCaptureGranted = ScreenCaptureService.isActive,
                episodesRun          = store.countByResult("success") + store.countByResult("failure"),
                adapterLoaded        = LoraTrainer.latestAdapterPath(context) != null,
                loraVersion          = loraVer,
            )
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
            val cfg = ConfigStore.load(context)
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
}
