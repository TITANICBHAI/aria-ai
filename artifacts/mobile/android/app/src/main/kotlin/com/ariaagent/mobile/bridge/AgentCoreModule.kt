package com.ariaagent.mobile.bridge

import com.ariaagent.mobile.core.agent.AgentLoop
import com.ariaagent.mobile.core.agent.AppSkillRegistry
import com.ariaagent.mobile.core.agent.GameLoop
import com.ariaagent.mobile.core.agent.TaskQueueManager
import com.ariaagent.mobile.core.ai.ChatContextBuilder
import com.ariaagent.mobile.core.ai.LlamaEngine
import com.ariaagent.mobile.core.ai.ModelDownloadService
import com.ariaagent.mobile.core.ai.ModelManager
import com.ariaagent.mobile.core.config.ConfigStore
import com.ariaagent.mobile.core.events.AgentEventBus
import com.ariaagent.mobile.core.monitoring.LocalDeviceServer
import com.ariaagent.mobile.core.monitoring.MonitoringPusher
import com.ariaagent.mobile.core.memory.EmbeddingModelManager
import com.ariaagent.mobile.core.perception.ObjectDetectorEngine
import com.ariaagent.mobile.core.memory.ExperienceStore
import com.ariaagent.mobile.core.memory.ObjectLabelStore
import com.ariaagent.mobile.core.ocr.OcrEngine
import com.ariaagent.mobile.core.perception.ScreenObserver
import com.ariaagent.mobile.core.rl.IrlModule
import com.ariaagent.mobile.core.rl.LearningScheduler
import com.ariaagent.mobile.core.rl.LoraTrainer
import com.ariaagent.mobile.core.rl.PolicyNetwork
import com.ariaagent.mobile.core.persistence.ProgressPersistence
import com.ariaagent.mobile.core.system.SustainedPerformanceManager
import com.ariaagent.mobile.core.setup.ModelBootstrap
import com.ariaagent.mobile.core.system.ThermalGuard
import com.ariaagent.mobile.system.AgentForegroundService
import com.ariaagent.mobile.system.accessibility.AgentAccessibilityService
import com.ariaagent.mobile.system.actions.GestureEngine
import com.ariaagent.mobile.system.screen.ScreenCaptureService
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AgentCoreModule — The TurboModule bridge between JS and the Kotlin brain.
 *
 * JS calls methods here. Kotlin owns ALL logic. JS only displays results.
 * This is the ONLY permitted channel between the JS layer and:
 *   - The LLM (Llama 3.2-1B via llama.cpp)
 *   - OCR (ML Kit)
 *   - Screen capture (MediaProjection)
 *   - Gesture dispatch (AccessibilityService)
 *   - Memory / experience store (SQLite)
 *   - RL / LoRA training scheduler
 *
 * Events pushed to JS (via DeviceEventEmitter):
 *   model_download_progress  { percent, downloadedMb, totalMb, speedMbps }
 *   model_download_complete  { path }
 *   model_download_error     { error }
 *   agent_status_changed     { status, currentTask, currentApp }
 *   token_generated          { token, tokensPerSecond }
 *   action_performed         { tool, nodeId, success, reward }
 *   learning_cycle_complete  { loraVersion, policyVersion }
 */
class AgentCoreModule(private val ctx: ReactApplicationContext) :
    ReactContextBaseJavaModule(ctx) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val learningScheduler = LearningScheduler(ctx)

    // ── MediaProjection permission request state ────────────────────────────
    // Only one pending request at a time. Stored here so onActivityResult can
    // resolve/reject it after the system returns from the permission dialog.
    private var pendingScreenCapturePromise: Promise? = null

    private val activityEventListener: ActivityEventListener =
        object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity?,
                requestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                if (requestCode != SCREEN_CAPTURE_REQUEST_CODE) return
                val promise = pendingScreenCapturePromise ?: return
                pendingScreenCapturePromise = null

                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Launch ScreenCaptureService with the granted projection token.
                    val serviceIntent = Intent(ctx, ScreenCaptureService::class.java).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("projectionData", data)
                    }
                    ctx.startForegroundService(serviceIntent)
                    promise.resolve(Arguments.createMap().apply { putBoolean("granted", true) })
                } else {
                    promise.resolve(Arguments.createMap().apply { putBoolean("granted", false) })
                }
            }
        }

    companion object {
        private const val SCREEN_CAPTURE_REQUEST_CODE = 9001
    }

    override fun getName(): String = "AgentCore"

    // Wire all Kotlin callbacks → JS DeviceEventEmitter + AgentEventBus on module init
    init {
        ctx.addActivityEventListener(activityEventListener)
        // ── Migrate legacy SharedPreferences config to DataStore on first boot ──
        scope.launch(Dispatchers.IO) {
            runCatching { ConfigStore.migrateFromSharedPrefs(ctx) }
        }

        // AgentLoop → JS events via DeviceEventEmitter
        // AgentEventBus receives events directly from AgentLoop (no double-emit here for
        // agent_status_changed / token_generated / action_performed — they're wired inside AgentLoop).
        AgentLoop.onEvent = { name, data ->
            val map = Arguments.createMap()
            data.forEach { (key, value) ->
                when (value) {
                    is String  -> map.putString(key, value)
                    is Boolean -> map.putBoolean(key, value)
                    is Int     -> map.putInt(key, value)
                    is Double  -> map.putDouble(key, value)
                    is Long    -> map.putDouble(key, value.toDouble())
                    else       -> map.putString(key, value.toString())
                }
            }
            emitEvent(name, map)
        }

        // LearningScheduler → JS "learning_cycle_complete" event + AgentEventBus
        learningScheduler.onLearningCycleComplete = { loraVersion, policyVersion ->
            val data = mapOf("loraVersion" to loraVersion, "policyVersion" to policyVersion)
            emitEvent("learning_cycle_complete", Arguments.createMap().apply {
                putInt("loraVersion", loraVersion)
                putInt("policyVersion", policyVersion)
            })
            AgentEventBus.emit("learning_cycle_complete", data)
        }

        // ThermalGuard → JS "thermal_status_changed" event + AgentEventBus
        try {
            ThermalGuard.register(ctx, object : ThermalGuard.ThermalListener {
                override fun onThermalLevelChanged(level: ThermalGuard.ThermalLevel) {
                    val data = mapOf(
                        "level" to level.name.lowercase(),
                        "inferenceSafe" to ThermalGuard.isInferenceSafe(),
                        "trainingSafe" to ThermalGuard.isTrainingSafe(),
                        "emergency" to ThermalGuard.isEmergency()
                    )
                    emitEvent("thermal_status_changed", Arguments.createMap().apply {
                        putString("level", level.name.lowercase())
                        putBoolean("inferenceSafe", ThermalGuard.isInferenceSafe())
                        putBoolean("trainingSafe", ThermalGuard.isTrainingSafe())
                        putBoolean("emergency", ThermalGuard.isEmergency())
                    })
                    AgentEventBus.emit("thermal_status_changed", data)
                }
            })
        } catch (e: Exception) {
            android.util.Log.w("AgentCoreModule", "ThermalGuard.register failed: ${e.message}")
        }

        // Start the scheduler — it will begin training only when idle + charging
        try {
            learningScheduler.start()
        } catch (e: Exception) {
            android.util.Log.w("AgentCoreModule", "LearningScheduler.start failed: ${e.message}")
        }

        // Sequential model bootstrap: GGUF → MiniLM → EfficientDet (in order).
        // ModelBootstrap waits for GGUF (user-initiated via startModelDownload()),
        // then chains MiniLM and EfficientDet so they never compete for bandwidth.
        // Each stage emits "bootstrap_stage" to JS with step / percent / label.
        scope.launch(Dispatchers.IO) {
            ModelBootstrap.run(ctx) { event ->
                emitEvent("bootstrap_stage", Arguments.createMap().apply {
                    putString("stage",      event.stage)
                    putInt("step",          event.step)
                    putInt("totalSteps",    event.totalSteps)
                    putInt("percent",       event.percent)
                    putString("label",      event.label)
                })
            }
        }

        // ── Phase 16: Start local device server so the web dashboard can connect ──
        // Serves live monitoring data at http://{device-ip}:8765/aria/*
        scope.launch(Dispatchers.IO) {
            LocalDeviceServer.start(LocalDeviceServer.DEFAULT_PORT)
            MonitoringPusher.start(ctx)
            android.util.Log.i("AgentCoreModule", "Local server: ${LocalDeviceServer.serverUrl()}")
        }
    }

    // ─── Model readiness ─────────────────────────────────────────────────────

    @ReactMethod
    fun checkModelReady(promise: Promise) {
        promise.resolve(ModelManager.isModelReady(ctx))
    }

    @ReactMethod
    fun getModelInfo(promise: Promise) {
        val map = Arguments.createMap().apply {
            putString("modelName", "Llama-3.2-1B-Instruct-Q4_K_M")
            putString("quantization", "Q4_K_M")
            putInt("contextLength", 4096)
            putInt("maxTokensPerTurn", 512)
            putString("downloadUrl", ModelManager.MODEL_URL)
            putDouble("diskSizeMb", 870.0)
            putDouble("ramSizeMb", 1700.0)
            putBoolean("isReady", ModelManager.isModelReady(ctx))
            putString("path", ModelManager.modelPath(ctx).absolutePath)
        }
        promise.resolve(map)
    }

    // ─── Model download ───────────────────────────────────────────────────────

    @ReactMethod
    fun startModelDownload(promise: Promise) {
        scope.launch(Dispatchers.Main) {
            try {
                val intent = android.content.Intent(ctx, ModelDownloadService::class.java)
                ctx.startForegroundService(intent)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("DOWNLOAD_START_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun cancelModelDownload(promise: Promise) {
        ctx.stopService(android.content.Intent(ctx, ModelDownloadService::class.java))
        promise.resolve(true)
    }

    // ─── LLM inference ───────────────────────────────────────────────────────

    @ReactMethod
    fun loadModel(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                // Read all model config from ConfigStore (DataStore — Phase 10 source of truth).
                val config = ConfigStore.getBlocking(ctx)
                val defaultPath = ModelManager.modelPath(ctx).absolutePath

                val modelPath   = config.modelPath.ifBlank { defaultPath }
                val contextSize = config.contextWindow
                // nGpuLayers 0 in DataStore means "not set yet" — default to 32 for Q4_K_M 1B.
                val nGpuLayers  = if (config.nGpuLayers > 0) config.nGpuLayers else 32

                // Use the config path if the file exists; fall back to default model path.
                val resolvedPath = if (java.io.File(modelPath).exists()) modelPath else defaultPath
                if (resolvedPath != modelPath && modelPath.isNotBlank()) {
                    android.util.Log.w("AgentCoreModule",
                        "Config model path not found: $modelPath — falling back to default")
                }

                android.util.Log.i("AgentCoreModule",
                    "Loading model: $resolvedPath (ctx=$contextSize, gpu=$nGpuLayers)")
                LlamaEngine.load(resolvedPath, contextSize = contextSize, nGpuLayers = nGpuLayers)

                // Auto-load LoRA adapter if one has been trained and is available.
                // Prefer the user-configured adapter path in ConfigStore; fall back to
                // the latest auto-trained adapter on disk.
                val adapterPath = when {
                    config.loraAdapterPath.isNotBlank() && java.io.File(config.loraAdapterPath).exists() ->
                        config.loraAdapterPath
                    else -> LoraTrainer.latestAdapterPath(ctx)
                }
                if (adapterPath != null) {
                    val loaded = LlamaEngine.loadLora(adapterPath, scale = 0.8f)
                    android.util.Log.i("AgentCoreModule", "LoRA auto-load: $adapterPath → $loaded")
                }

                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("LOAD_FAILED", e.message)
            }
        }
    }

    // ─── Unified module status ────────────────────────────────────────────────

    @ReactMethod
    fun getModuleStatus(promise: Promise) {
        scope.launch {
            try {
                val store = ExperienceStore.getInstance(ctx)
                val adapterPath = LoraTrainer.latestAdapterPath(ctx)
                val miniLmReady = EmbeddingModelManager.isModelReady(ctx)

                promise.resolve(Arguments.createMap().apply {

                    // LLM
                    putMap("llm", Arguments.createMap().apply {
                        putBoolean("loaded", LlamaEngine.isLoaded())
                        putString("modelName", "Llama-3.2-1B-Instruct")
                        putString("quantization", "Q4_K_M")
                        putInt("contextLength", 4096)
                        putDouble("tokensPerSecond", LlamaEngine.lastToksPerSec)
                        putDouble("memoryMb", LlamaEngine.memoryMb)
                    })

                    // OCR
                    putMap("ocr", Arguments.createMap().apply {
                        putBoolean("ready", true)  // ML Kit auto-downloads via Play Services
                        putString("engine", "ML Kit Text Recognition v2")
                    })

                    // RL / LoRA
                    putMap("rl", Arguments.createMap().apply {
                        putBoolean("ready", PolicyNetwork.isReady())
                        putInt("episodesRun", store.countByResult("success") + store.countByResult("failure"))
                        putInt("loraVersion", LoraTrainer.currentVersion(ctx))
                        putBoolean("adapterLoaded", adapterPath != null && LlamaEngine.isLoaded())
                        putInt("untrainedSamples", store.getUntrainedSuccesses(1000).size)
                        putInt("adamStep", PolicyNetwork.adamStepCount)
                        putDouble("lastPolicyLoss", PolicyNetwork.lastPolicyLoss)
                    })

                    // Memory / embedding
                    putMap("memory", Arguments.createMap().apply {
                        putBoolean("ready", miniLmReady)
                        putString("engine", if (miniLmReady) "MiniLM-L6-v2 ONNX Runtime" else "hash fallback")
                        putInt("embeddingCount", store.count())
                        putInt("dbSizeKb", (store.count() * 2))   // rough estimate
                        putInt("edgeCaseCount", store.edgeCaseCount())
                    })

                    // Accessibility
                    putMap("accessibility", Arguments.createMap().apply {
                        putBoolean("granted", AgentAccessibilityService.isActive)
                        putBoolean("active", AgentAccessibilityService.isActive)
                    })

                    // Screen capture
                    putMap("screenCapture", Arguments.createMap().apply {
                        putBoolean("granted", ScreenCaptureService.isActive)
                        putBoolean("active", ScreenCaptureService.isActive)
                    })

                    // Thermal
                    putMap("thermal", Arguments.createMap().apply {
                        putString("level", ThermalGuard.currentLevel.name.lowercase())
                        putBoolean("inferenceSafe", ThermalGuard.isInferenceSafe())
                        putBoolean("trainingSafe", ThermalGuard.isTrainingSafe())
                    })
                })
            } catch (e: Exception) {
                promise.resolve(Arguments.createMap())
            }
        }
    }

    // ─── Game loop status ─────────────────────────────────────────────────────

    @ReactMethod
    fun getGameLoopStatus(promise: Promise) {
        val s = GameLoop.state
        promise.resolve(Arguments.createMap().apply {
            putBoolean("isActive",     s.isActive)
            putString("gameType",      s.gameType.name.lowercase())
            putInt("episodeCount",     s.episodeCount)
            putInt("stepCount",        s.stepCount)
            putInt("currentScore",     s.currentScore)
            putInt("highScore",        s.highScore)
            putDouble("totalReward",   s.totalReward)
            putString("lastAction",    s.lastAction)
            putBoolean("isGameOver",   s.isGameOver)
        })
    }

    // ─── Config ───────────────────────────────────────────────────────────────
    // All config reads/writes go through ConfigStore (DataStore<Preferences>) introduced in
    // Phase 10. SharedPreferences ("aria_config") is no longer the source of truth; legacy
    // values are migrated into DataStore on first boot via migrateFromSharedPrefs() in init{}.

    @ReactMethod
    fun getConfig(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val config = ConfigStore.getBlocking(ctx)
                // Prefer the persisted loraAdapterPath; fall back to the latest auto-trained
                // adapter on disk so the UI always shows what the engine will actually load.
                val adapterPath = config.loraAdapterPath.ifBlank {
                    LoraTrainer.latestAdapterPath(ctx) ?: ""
                }
                promise.resolve(Arguments.createMap().apply {
                    putString("modelPath", config.modelPath.ifBlank { ModelManager.modelPath(ctx).absolutePath })
                    putString("quantization", config.quantization)
                    putInt("contextWindow", config.contextWindow)
                    putInt("maxTokensPerTurn", config.maxTokensPerTurn)
                    putInt("temperatureX100", config.temperatureX100)
                    putString("loraAdapterPath", adapterPath)
                    putBoolean("rlEnabled", config.rlEnabled)
                    putInt("nGpuLayers", config.nGpuLayers)
                    putDouble("learningRate", config.learningRate)
                })
            } catch (e: Exception) {
                promise.resolve(Arguments.createMap())
            }
        }
    }

    @ReactMethod
    fun updateConfig(config: ReadableMap, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                // Read current DataStore state, apply only the keys that were sent.
                val current = ConfigStore.getBlocking(ctx)
                val updated = current.copy(
                    modelPath = if (config.hasKey("modelPath") && !config.isNull("modelPath"))
                        config.getString("modelPath") ?: current.modelPath else current.modelPath,
                    quantization = if (config.hasKey("quantization") && !config.isNull("quantization"))
                        config.getString("quantization") ?: current.quantization else current.quantization,
                    contextWindow    = if (config.hasKey("contextWindow"))    config.getInt("contextWindow")    else current.contextWindow,
                    maxTokensPerTurn = if (config.hasKey("maxTokensPerTurn")) config.getInt("maxTokensPerTurn") else current.maxTokensPerTurn,
                    temperatureX100  = if (config.hasKey("temperatureX100"))  config.getInt("temperatureX100")  else current.temperatureX100,
                    nGpuLayers       = if (config.hasKey("nGpuLayers"))       config.getInt("nGpuLayers")       else current.nGpuLayers,
                    rlEnabled        = if (config.hasKey("rlEnabled"))        config.getBoolean("rlEnabled")    else current.rlEnabled,
                    learningRate     = if (config.hasKey("learningRate"))     config.getDouble("learningRate")  else current.learningRate,
                    // null loraAdapterPath means "use auto-trained" — store as empty string
                    loraAdapterPath = when {
                        config.hasKey("loraAdapterPath") && config.isNull("loraAdapterPath") -> ""
                        config.hasKey("loraAdapterPath") -> config.getString("loraAdapterPath") ?: current.loraAdapterPath
                        else -> current.loraAdapterPath
                    }
                )
                ConfigStore.save(ctx, updated)

                // Notify Compose ViewModel and any other AgentEventBus subscribers.
                AgentEventBus.emit("config_updated", mapOf(
                    "modelPath"        to updated.modelPath,
                    "quantization"     to updated.quantization,
                    "contextWindow"    to updated.contextWindow,
                    "maxTokensPerTurn" to updated.maxTokensPerTurn,
                    "temperatureX100"  to updated.temperatureX100,
                    "nGpuLayers"       to updated.nGpuLayers,
                    "loraAdapterPath"  to updated.loraAdapterPath,
                    "rlEnabled"        to updated.rlEnabled,
                    "learningRate"     to updated.learningRate,
                ))

                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("CONFIG_UPDATE_FAILED", e.message)
            }
        }
    }

    // ─── Thermal status ───────────────────────────────────────────────────────

    @ReactMethod
    fun getThermalStatus(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putString("level", ThermalGuard.currentLevel.name.lowercase())
            putBoolean("inferenceSafe", ThermalGuard.isInferenceSafe())
            putBoolean("trainingSafe", ThermalGuard.isTrainingSafe())
            putBoolean("throttleCapture", ThermalGuard.shouldThrottleCapture())
            putBoolean("emergency", ThermalGuard.isEmergency())
        })
    }

    // ─── MiniLM embedding model ───────────────────────────────────────────────

    @ReactMethod
    fun getMiniLmStatus(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putBoolean("ready", EmbeddingModelManager.isModelReady(ctx))
            putString("path", EmbeddingModelManager.modelPath(ctx).absolutePath)
            putDouble("downloadedBytes", EmbeddingModelManager.downloadedBytes(ctx).toDouble())
        })
    }

    @ReactMethod
    fun downloadMiniLm(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            val ok = EmbeddingModelManager.download(ctx)
            promise.resolve(ok)
        }
    }

    // ─── Object Detection (Phase 13 — Auto-detect) ───────────────────────────

    @ReactMethod
    fun isDetectorModelReady(promise: Promise) {
        promise.resolve(ObjectDetectorEngine.isModelReady(ctx))
    }

    @ReactMethod
    fun downloadDetectorModel(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            val ok = ObjectDetectorEngine.ensureModel(ctx)
            promise.resolve(ok)
        }
    }

    /**
     * Run EfficientDet-Lite0 INT8 on a JPEG file and return detections as a JSON array.
     *
     * Each element:
     *   { "label": "person", "confidence": 0.87, "normX": 0.45, "normY": 0.60, "normW": 0.20, "normH": 0.35 }
     *
     * normX/normY are bounding box CENTERS (0–1) — directly usable as pin coordinates in the labeler.
     * Used by the Object Labeler "Auto-detect" button (UI path) and exposed to JS.
     * AgentLoop uses ObjectDetectorEngine.detect() directly (bitmap path, no serialization).
     */
    @ReactMethod
    fun detectObjectsInImage(imageUri: String, promise: Promise) {
        scope.launch {
            try {
                val detections = ObjectDetectorEngine.detectFromPath(ctx, imageUri)
                val json = buildString {
                    append("[")
                    detections.forEachIndexed { i, d ->
                        if (i > 0) append(",")
                        append("""{"label":"${d.label}","confidence":${d.confidence},"normX":${d.normX},"normY":${d.normY},"normW":${d.normW},"normH":${d.normH}}""")
                    }
                    append("]")
                }
                promise.resolve(json)
            } catch (e: Exception) {
                promise.reject("DETECTION_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun runInference(prompt: String, maxTokens: Int, promise: Promise) {
        if (!LlamaEngine.isLoaded()) {
            promise.reject("NOT_LOADED", "Model is not loaded. Call loadModel() first.")
            return
        }
        scope.launch {
            try {
                val result = LlamaEngine.infer(prompt, maxTokens) { token ->
                    emitEvent("token_generated", Arguments.createMap().apply {
                        putString("token", token)
                    })
                }
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("INFERENCE_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun getLlmStatus(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putBoolean("loaded", LlamaEngine.isLoaded())
            putDouble("tokensPerSecond", LlamaEngine.lastToksPerSec)
            putDouble("memoryMb", LlamaEngine.memoryMb)
            putString("modelName", "Llama-3.2-1B-Instruct")
            putString("quantization", "Q4_K_M")
            putInt("contextLength", 4096)
        })
    }

    // ─── Perception ───────────────────────────────────────────────────────────

    @ReactMethod
    fun observeScreen(promise: Promise) {
        scope.launch {
            try {
                val screenshot = ScreenCaptureService.captureLatest()
                val ocrText = if (screenshot != null) OcrEngine.run(screenshot) else ""
                val a11yTree = AgentAccessibilityService.getSemanticTree()
                val fused = "$a11yTree\n\nOCR:\n$ocrText".trim()
                promise.resolve(fused)
            } catch (e: Exception) {
                promise.reject("OBSERVE_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun runOcr(imagePath: String, promise: Promise) {
        scope.launch {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
                val result = OcrEngine.run(bitmap)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("OCR_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun getAccessibilityTree(promise: Promise) {
        promise.resolve(AgentAccessibilityService.getSemanticTree())
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    @ReactMethod
    fun executeAction(actionJson: String, promise: Promise) {
        scope.launch {
            try {
                val success = GestureEngine.executeFromJson(actionJson)
                promise.resolve(success)
            } catch (e: Exception) {
                promise.reject("ACTION_FAILED", e.message)
            }
        }
    }

    // ─── Memory / experience ──────────────────────────────────────────────────

    @ReactMethod
    fun getExperienceStats(promise: Promise) {
        scope.launch {
            try {
                val store = ExperienceStore.getInstance(ctx)
                promise.resolve(Arguments.createMap().apply {
                    putInt("totalTuples", store.count())
                    putInt("successCount", store.countByResult("success"))
                    putInt("failureCount", store.countByResult("failure"))
                    putInt("edgeCaseCount", store.edgeCaseCount())
                })
            } catch (e: Exception) {
                promise.resolve(Arguments.createMap())
            }
        }
    }

    @ReactMethod
    fun getRecentExperiences(limit: Int, promise: Promise) {
        scope.launch {
            try {
                val store = ExperienceStore.getInstance(ctx)
                val entries = store.getRecent(limit)
                val arr = Arguments.createArray()
                entries.forEach { e ->
                    arr.pushMap(Arguments.createMap().apply {
                        putString("id", e.id)
                        putDouble("timestamp", e.timestamp.toDouble())
                        putString("appPackage", e.appPackage)
                        putString("taskType", e.taskType)
                        putString("actionJson", e.actionJson)
                        putString("result", e.result)
                        putDouble("reward", e.reward)
                        putBoolean("isEdgeCase", e.isEdgeCase)
                    })
                }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.resolve(Arguments.createArray())
            }
        }
    }

    @ReactMethod
    fun clearMemory(promise: Promise) {
        scope.launch {
            ExperienceStore.getInstance(ctx).clearAll()
            promise.resolve(true)
        }
    }

    // ─── Agent status ─────────────────────────────────────────────────────────

    @ReactMethod
    fun getAgentStatus(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putString("status", "idle")
            putBoolean("modelReady", ModelManager.isModelReady(ctx))
            putBoolean("llmLoaded", LlamaEngine.isLoaded())
            putBoolean("accessibilityActive", AgentAccessibilityService.isActive)
            putBoolean("screenCaptureActive", ScreenCaptureService.isActive)
            putDouble("tokensPerSecond", LlamaEngine.lastToksPerSec)
            putDouble("memoryMb", LlamaEngine.memoryMb)
        })
    }

    // ─── Agent loop control ───────────────────────────────────────────────────

    @ReactMethod
    fun startAgent(goal: String, appPackage: String, promise: Promise) {
        scope.launch {
            try {
                if (!LlamaEngine.isLoaded()) {
                    promise.reject("MODEL_NOT_LOADED", "Call loadModel() before startAgent()")
                    return@launch
                }
                // Phase 14: route through AgentForegroundService to protect the reasoning loop
                // from Android LMK termination. The service calls AgentLoop.start() internally
                // and updates the persistent notification on each step via AgentEventBus.
                // SustainedPerformanceManager.enable() is called inside AgentLoop.start().
                AgentForegroundService.startWithGoal(ctx, goal, appPackage)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("AGENT_START_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun startAgentLearnOnly(goal: String, appPackage: String, promise: Promise) {
        scope.launch {
            try {
                if (!LlamaEngine.isLoaded()) {
                    promise.reject("MODEL_NOT_LOADED", "Call loadModel() before startAgentLearnOnly()")
                    return@launch
                }
                AgentForegroundService.startLearnOnly(ctx, goal, appPackage)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("AGENT_START_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun stopAgent(promise: Promise) {
        // Phase 14: stop via the foreground service so it can call stopSelf() cleanly.
        // AgentForegroundService.stop() sends ACTION_STOP → AgentLoop.stop() + stopSelf().
        // SustainedPerformanceManager.disable() is called inside AgentLoop.stop().
        AgentForegroundService.stop(ctx)
        promise.resolve(true)
    }

    @ReactMethod
    fun pauseAgent(promise: Promise) {
        // Route pause through the foreground service so the notification text updates too.
        val intent = android.content.Intent(ctx, AgentForegroundService::class.java).apply {
            action = AgentForegroundService.ACTION_PAUSE
        }
        ctx.startService(intent)
        promise.resolve(true)
    }

    @ReactMethod
    fun getAgentLoopStatus(promise: Promise) {
        val s = AgentLoop.state
        promise.resolve(Arguments.createMap().apply {
            putString("status", s.status.name.lowercase())
            putString("goal", s.goal)
            putString("appPackage", s.appPackage)
            putInt("stepCount", s.stepCount)
            putString("lastAction", s.lastAction)
            putString("lastError", s.lastError)
        })
    }

    // ─── RL / Learning ────────────────────────────────────────────────────────

    @ReactMethod
    fun runRlCycle(promise: Promise) {
        scope.launch(Dispatchers.Default) {
            try {
                val store = ExperienceStore.getInstance(ctx)
                val modelPath = ModelManager.modelPath(ctx).absolutePath
                val result = LoraTrainer.train(ctx, store, modelPath)

                if (result.success && result.adapterPath.isNotEmpty()) {
                    // Persist new adapter path to ConfigStore so the next loadModel() picks it up.
                    val current = ConfigStore.getBlocking(ctx)
                    ConfigStore.save(ctx, current.copy(loraAdapterPath = result.adapterPath))
                    android.util.Log.i("AgentCoreModule",
                        "runRlCycle: persisted loraAdapterPath → ${result.adapterPath}")

                    // Hot-reload adapter into the running LlamaEngine if it is currently loaded.
                    if (LlamaEngine.isLoaded()) {
                        val hotLoaded = LlamaEngine.loadLora(result.adapterPath, scale = 0.8f)
                        android.util.Log.i("AgentCoreModule",
                            "runRlCycle: hot-reload adapter v${result.loraVersion} → $hotLoaded")
                    }

                    // Emit event so the JS/Compose UI refreshes module status and LoRA version.
                    emitEvent("learning_cycle_complete", Arguments.createMap().apply {
                        putInt("loraVersion", result.loraVersion)
                        putInt("policyVersion", 1)
                    })
                    AgentEventBus.emit("learning_cycle_complete",
                        mapOf("loraVersion" to result.loraVersion, "policyVersion" to 1))
                }

                promise.resolve(Arguments.createMap().apply {
                    putBoolean("success", result.success)
                    putInt("samplesUsed", result.samplesUsed)
                    putString("adapterPath", result.adapterPath)
                    putInt("loraVersion", result.loraVersion)
                    putString("errorMessage", result.errorMessage)
                })
            } catch (e: Exception) {
                promise.reject("RL_CYCLE_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun processIrlVideo(videoPath: String, taskGoal: String, appPackage: String, promise: Promise) {
        scope.launch {
            try {
                val store = ExperienceStore.getInstance(ctx)
                val result = IrlModule.processVideo(ctx, videoPath, taskGoal, appPackage, store)
                promise.resolve(Arguments.createMap().apply {
                    putString("videoPath", result.videoPath)
                    putInt("framesProcessed", result.framesProcessed)
                    putInt("tuplesExtracted", result.tuplesExtracted)
                    putInt("llmAssistedCount", result.llmAssistedCount)
                    putString("errorMessage", result.errorMessage)
                })
            } catch (e: Exception) {
                promise.reject("IRL_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun getLearningStatus(promise: Promise) {
        scope.launch {
            try {
                val store = ExperienceStore.getInstance(ctx)
                val loraVersion = LoraTrainer.currentVersion(ctx)
                val adapterPath = LoraTrainer.latestAdapterPath(ctx) ?: ""
                promise.resolve(Arguments.createMap().apply {
                    putInt("loraVersion", loraVersion)
                    putString("latestAdapterPath", adapterPath)
                    putBoolean("adapterExists", adapterPath.isNotEmpty())
                    putInt("untrainedSamples", store.getUntrainedSuccesses(limit = 1000).size)
                    putBoolean("policyReady", PolicyNetwork.isReady())
                })
            } catch (e: Exception) {
                promise.resolve(Arguments.createMap())
            }
        }
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    @ReactMethod
    fun isAccessibilityEnabled(promise: Promise) {
        promise.resolve(AgentAccessibilityService.isActive)
    }

    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        scope.launch(Dispatchers.Main) {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                ctx.startActivity(intent)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("SETTINGS_FAILED", e.message)
            }
        }
    }

    /** Returns the live grant status of all three permissions ARIA needs. */
    @ReactMethod
    fun getPermissionsStatus(promise: Promise) {
        scope.launch(Dispatchers.Default) {
            try {
                val notificationsEnabled = androidx.core.app.NotificationManagerCompat
                    .from(ctx).areNotificationsEnabled()
                promise.resolve(Arguments.createMap().apply {
                    putBoolean("accessibility", AgentAccessibilityService.isActive)
                    putBoolean("screenCapture", ScreenCaptureService.isActive)
                    putBoolean("notifications", notificationsEnabled)
                })
            } catch (e: Exception) {
                promise.resolve(Arguments.createMap().apply {
                    putBoolean("accessibility", false)
                    putBoolean("screenCapture", false)
                    putBoolean("notifications", false)
                })
            }
        }
    }

    /** Opens this app's notification settings page in Android system settings. */
    @ReactMethod
    fun openNotificationSettings(promise: Promise) {
        scope.launch(Dispatchers.Main) {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("SETTINGS_FAILED", e.message)
            }
        }
    }

    /**
     * Request MediaProjection (screen capture) permission.
     *
     * Shows the system "ARIA will start capturing everything on your screen" dialog.
     * On RESULT_OK the activity event listener starts ScreenCaptureService and resolves
     * the promise with { granted: true }. On denial it resolves with { granted: false }.
     *
     * If the service is already active this is a no-op and returns { granted: true }.
     */
    @ReactMethod
    fun requestScreenCapturePermission(promise: Promise) {
        if (ScreenCaptureService.isActive) {
            promise.resolve(Arguments.createMap().apply { putBoolean("granted", true) })
            return
        }
        val activity = currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No foreground activity to launch MediaProjection dialog")
            return
        }
        pendingScreenCapturePromise = promise
        val pm = activity.getSystemService(MediaProjectionManager::class.java)
        activity.startActivityForResult(
            pm.createScreenCaptureIntent(),
            SCREEN_CAPTURE_REQUEST_CODE
        )
    }

    // ─── Object Labeler ───────────────────────────────────────────────────────
    // Bridge methods for the human-in-the-loop UI element annotation tool.
    // The labeler lets users tap on a screenshot and annotate any UI element
    // with name, context, and element type. The LLM then enriches each annotation
    // with meaning, interaction hints, and reasoning context injected into future prompts.

    /**
     * Capture the current screen and return metadata for the labeler UI.
     * Returns { imageUri, appPackage, screenHash, ocrText, a11yTree }
     */
    @ReactMethod
    fun captureScreenForLabeling(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val screenshot = ScreenCaptureService.captureLatest()
                if (screenshot == null) {
                    promise.reject("CAPTURE_FAILED", "Screen capture service not active — grant Media Projection permission first")
                    return@launch
                }
                val ocrText  = OcrEngine.run(screenshot)
                val a11yTree = AgentAccessibilityService.getSemanticTree()
                val snapshot = ScreenObserver.capture()

                // Write JPEG to app cache so RN Image component can load it
                val file = java.io.File(ctx.cacheDir, "labeler_capture_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                }

                promise.resolve(Arguments.createMap().apply {
                    putString("imageUri",   file.absolutePath)
                    putString("appPackage", snapshot.appPackage)
                    putString("screenHash", snapshot.screenHash())
                    putString("ocrText",    ocrText.take(2000))
                    putString("a11yTree",   a11yTree.take(2000))
                })
            } catch (e: Exception) {
                promise.reject("CAPTURE_FAILED", e.message)
            }
        }
    }

    /**
     * Return all saved labels for a specific screen (appPackage + screenHash) as a JSON string.
     * Called when the labeler opens to load existing annotations.
     */
    @ReactMethod
    fun getObjectLabels(appPackage: String, screenHash: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val store  = ObjectLabelStore.getInstance(ctx)
                val labels = store.getByScreen(appPackage, screenHash)
                promise.resolve(store.toJson(labels))
            } catch (e: Exception) {
                promise.reject("LABELS_FAILED", e.message)
            }
        }
    }

    /**
     * Return ALL saved labels across every app — used for the label browser in the Modules screen.
     * Returns JSON string (same format as getObjectLabels).
     */
    @ReactMethod
    fun getAllLabels(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val store  = ObjectLabelStore.getInstance(ctx)
                val labels = store.getAllEnriched(limit = 200)
                promise.resolve(store.toJson(labels))
            } catch (e: Exception) {
                promise.resolve("[]")
            }
        }
    }

    /**
     * Persist a batch of annotations from the JS labeler to SQLite.
     * Replaces existing labels for the same (appPackage, screenHash) screen.
     * @param labelsJson JSON array string produced by the JS labeler
     */
    @ReactMethod
    fun saveObjectLabels(appPackage: String, screenHash: String, labelsJson: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val store  = ObjectLabelStore.getInstance(ctx)
                val labels = store.fromJson(labelsJson, appPackage, screenHash)
                store.saveAll(labels)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("SAVE_FAILED", e.message)
            }
        }
    }

    /**
     * Delete a single label by its UUID.
     */
    @ReactMethod
    fun deleteObjectLabel(id: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                ObjectLabelStore.getInstance(ctx).delete(id)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("DELETE_FAILED", e.message)
            }
        }
    }

    /**
     * Send a batch of label annotations to the local LLM for enrichment.
     * For each label, the LLM generates:
     *   meaning           — what this element does in the app
     *   interactionHint   — how the agent should interact with it
     *   reasoningContext  — a hint injected into future LLM prompts
     *   importanceScore   — 0–10 priority weight
     *
     * @param labelsJson    JSON array of ObjectLabel objects (from JS)
     * @param screenContext  OCR + a11y text of the screen (provides app context)
     */
    @ReactMethod
    fun enrichLabelsWithLLM(labelsJson: String, screenContext: String, promise: Promise) {
        if (!LlamaEngine.isLoaded()) {
            promise.reject("NOT_LOADED", "Load the LLM model first (loadModel())")
            return
        }
        scope.launch(Dispatchers.Default) {
            try {
                val store   = ObjectLabelStore.getInstance(ctx)
                val labels  = store.fromJson(labelsJson, "", "")
                val enriched = labels.map { label ->
                    val prompt    = buildLabelEnrichPrompt(label, screenContext)
                    val rawOutput = LlamaEngine.infer(prompt, maxTokens = 150)
                    parseLabelEnrichOutput(rawOutput, label)
                }
                promise.resolve(store.toJson(enriched))
            } catch (e: Exception) {
                promise.reject("ENRICH_FAILED", e.message)
            }
        }
    }

    /**
     * Return summary statistics for the object label store.
     * Used by the Modules screen to display label counts.
     */
    @ReactMethod
    fun getLabelStats(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val store = ObjectLabelStore.getInstance(ctx)
                promise.resolve(Arguments.createMap().apply {
                    putInt("total",    store.count())
                    putInt("enriched", store.countEnriched())
                })
            } catch (e: Exception) {
                promise.resolve(Arguments.createMap().apply {
                    putInt("total", 0); putInt("enriched", 0)
                })
            }
        }
    }

    // ── Object Labeler helpers ────────────────────────────────────────────────

    private fun buildLabelEnrichPrompt(
        label: ObjectLabelStore.ObjectLabel,
        screenContext: String
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

    private fun parseLabelEnrichOutput(
        rawOutput: String,
        original: ObjectLabelStore.ObjectLabel
    ): ObjectLabelStore.ObjectLabel {
        return try {
            val start = rawOutput.indexOfFirst { it == '{' }
            val end   = rawOutput.lastIndexOf('}')
            if (start == -1 || end <= start) return original
            val json = org.json.JSONObject(rawOutput.substring(start, end + 1))
            original.copy(
                meaning          = json.optString("meaning",          original.meaning).take(200),
                interactionHint  = json.optString("interactionHint",  original.interactionHint).take(200),
                reasoningContext  = json.optString("reasoningContext", original.reasoningContext).take(250),
                importanceScore  = json.optInt("importanceScore",     original.importanceScore).coerceIn(0, 10),
                isEnriched       = true,
                updatedAt        = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            original
        }
    }

    // ─── Progress Persistence (Phase 14.4 — Ralph Loop) ──────────────────────

    /**
     * Return the last 20 lines of aria_progress.txt for LLM context injection.
     * Returns empty string if the log does not exist yet.
     */
    @ReactMethod
    fun getProgressContext(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            promise.resolve(ProgressPersistence.readContext(ctx))
        }
    }

    /**
     * Clear both aria_progress.txt and aria_goals.json.
     * Call when the user explicitly resets the agent state from the UI.
     */
    @ReactMethod
    fun clearProgress(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            ProgressPersistence.clear(ctx)
            promise.resolve(true)
        }
    }

    /**
     * Initialise goals.json with a goal and an ordered list of sub-task labels.
     * subTasksJson: JSON array of strings, e.g. ["Open YouTube","Tap Trending","Play video"]
     */
    @ReactMethod
    fun initGoals(goal: String, subTasksJson: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val arr      = org.json.JSONArray(subTasksJson)
                val subTasks = (0 until arr.length()).map { arr.getString(it) }
                ProgressPersistence.initGoals(ctx, goal, subTasks)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("INIT_GOALS_FAILED", e.message)
            }
        }
    }

    /**
     * Return a compact [x]/[ ] checklist summary of the current goal state.
     * Used by the LLM to understand which sub-tasks are already done on resume.
     * Returns empty string if no goals file exists.
     */
    @ReactMethod
    fun getGoalSummary(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            promise.resolve(ProgressPersistence.goalSummary(ctx))
        }
    }

    /**
     * Mark a sub-task as passed in goals.json.
     * subTaskId: 1-indexed string ID (matches the order in initGoals)
     */
    @ReactMethod
    fun markSubTaskPassed(subTaskId: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            ProgressPersistence.markSubTaskPassed(ctx, subTaskId)
            promise.resolve(true)
        }
    }

    // ─── Phase 15: Task Queue ─────────────────────────────────────────────────

    /**
     * Add a task to the queue. Returns the queued task as a JSON map.
     * @param goal        Natural-language task description
     * @param appPackage  Target app package (may be empty — agent will infer from screen)
     * @param priority    Lower = higher priority. Default 0 (FIFO within same priority).
     */
    @ReactMethod
    fun enqueueTask(goal: String, appPackage: String, priority: Int, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val task = TaskQueueManager.enqueue(ctx, goal, appPackage, priority)
                promise.resolve(Arguments.createMap().apply {
                    putString("id",         task.id)
                    putString("goal",       task.goal)
                    putString("appPackage", task.appPackage)
                    putInt("priority",      task.priority)
                    putDouble("enqueuedAt", task.enqueuedAt.toDouble())
                })
            } catch (e: Exception) {
                promise.reject("ENQUEUE_FAILED", e.message)
            }
        }
    }

    /** Remove and return the head task, or null if queue is empty. */
    @ReactMethod
    fun dequeueTask(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val task = TaskQueueManager.dequeue(ctx)
                if (task == null) {
                    promise.resolve(null)
                } else {
                    promise.resolve(Arguments.createMap().apply {
                        putString("id",         task.id)
                        putString("goal",       task.goal)
                        putString("appPackage", task.appPackage)
                        putInt("priority",      task.priority)
                        putDouble("enqueuedAt", task.enqueuedAt.toDouble())
                    })
                }
            } catch (e: Exception) {
                promise.reject("DEQUEUE_FAILED", e.message)
            }
        }
    }

    /** Return all queued tasks in priority order. */
    @ReactMethod
    fun getTaskQueue(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val tasks = TaskQueueManager.getAll(ctx)
                val arr = Arguments.createArray()
                tasks.forEach { t ->
                    arr.pushMap(Arguments.createMap().apply {
                        putString("id",         t.id)
                        putString("goal",       t.goal)
                        putString("appPackage", t.appPackage)
                        putInt("priority",      t.priority)
                        putDouble("enqueuedAt", t.enqueuedAt.toDouble())
                    })
                }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.resolve(Arguments.createArray())
            }
        }
    }

    /** Remove a specific task by UUID without dequeuing the head. */
    @ReactMethod
    fun removeQueuedTask(taskId: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            promise.resolve(TaskQueueManager.remove(ctx, taskId))
        }
    }

    /** Empty the entire task queue. */
    @ReactMethod
    fun clearTaskQueue(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            TaskQueueManager.clear(ctx)
            promise.resolve(true)
        }
    }

    // ─── Phase 15: App Skill Registry ─────────────────────────────────────────

    /** Return the skill record for a specific app, or null if not yet seen. */
    @ReactMethod
    fun getAppSkill(appPackage: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val skill = AppSkillRegistry.getInstance(ctx).get(appPackage)
                if (skill == null) {
                    promise.resolve(null)
                } else {
                    promise.resolve(skillToMap(skill))
                }
            } catch (e: Exception) {
                promise.resolve(null)
            }
        }
    }

    /** Return all known app skill records, sorted by last-seen descending. */
    @ReactMethod
    fun getAllAppSkills(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val skills = AppSkillRegistry.getInstance(ctx).getAll()
                val arr = Arguments.createArray()
                skills.forEach { arr.pushMap(skillToMap(it)) }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.resolve(Arguments.createArray())
            }
        }
    }

    /** Clear all app skill records (destructive — only call from Settings reset). */
    @ReactMethod
    fun clearAppSkills(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            runCatching { AppSkillRegistry.getInstance(ctx).clear() }
            promise.resolve(true)
        }
    }

    private fun skillToMap(s: AppSkillRegistry.AppSkill): com.facebook.react.bridge.WritableMap =
        Arguments.createMap().apply {
            putString("appPackage",   s.appPackage)
            putString("appName",      s.appName)
            putInt("taskSuccess",     s.taskSuccess)
            putInt("taskFailure",     s.taskFailure)
            putInt("totalSteps",      s.totalSteps)
            putDouble("successRate",  s.successRate.toDouble())
            putDouble("avgSteps",     s.avgStepsPerTask.toDouble())
            putString("promptHint",   s.promptHint)
            putDouble("lastSeen",     s.lastSeen.toDouble())
            // Serialize lists as JSON strings to avoid WritableArray nesting complexity
            putString("learnedElements", org.json.JSONArray(s.learnedElements).toString())
            putString("taskTemplates",   org.json.JSONArray(s.taskTemplates).toString())
        }

    // ─── Monitoring snapshot ──────────────────────────────────────────────────

    /**
     * Returns the absolute path to the latest on-device monitoring snapshot, or null.
     *
     * MonitoringPusher writes a JSON snapshot to {filesDir}/monitoring/snapshot.json
     * every time a significant agent event fires (≤1 per 3s). The JS layer can use
     * this path to show the file location in the Settings screen and let the user
     * pull the file via ADB or Android file sharing for web-dashboard inspection.
     *
     * No network, no cloud — the file is entirely local to the device.
     */
    @ReactMethod
    fun getSnapshotPath(promise: Promise) {
        promise.resolve(MonitoringPusher.snapshotPath(ctx))
    }

    // ─── Phase 16: Local device server ───────────────────────────────────────

    /**
     * Returns the HTTP URL of the on-device monitoring server.
     * Example: "http://192.168.1.42:8765"
     * The web dashboard can connect to this URL on the same Wi-Fi network.
     */
    @ReactMethod
    fun getLocalServerUrl(promise: Promise) {
        promise.resolve(LocalDeviceServer.serverUrl())
    }

    /** Returns the device's local IPv4 address (Wi-Fi), or "127.0.0.1". */
    @ReactMethod
    fun getDeviceIp(promise: Promise) {
        promise.resolve(LocalDeviceServer.getDeviceIp())
    }

    /** Returns true if the local monitoring server is currently accepting connections. */
    @ReactMethod
    fun isLocalServerRunning(promise: Promise) {
        promise.resolve(LocalDeviceServer.running)
    }

    /**
     * (Re)starts the local monitoring server on the given port.
     * Defaults to 8765 if port is 0.
     */
    @ReactMethod
    fun startLocalServer(port: Int, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            val p = if (port <= 0) LocalDeviceServer.DEFAULT_PORT else port
            LocalDeviceServer.start(p)
            promise.resolve(LocalDeviceServer.serverUrl())
        }
    }

    /** Stops the local monitoring server. */
    @ReactMethod
    fun stopLocalServer(promise: Promise) {
        LocalDeviceServer.stop()
        promise.resolve(true)
    }

    // ─── Chat context builder ─────────────────────────────────────────────────

    /**
     * Builds the full system prompt for the ARIA chat interface in Kotlin.
     *
     * Replaces the JS-side buildContextPrompt() function that previously made 4 bridge
     * round-trips (getAgentState, getMemoryEntries, getTaskQueue, getAllAppSkills) and
     * assembled the prompt in TypeScript. A single Kotlin call now reads all stores
     * directly and returns the formatted string — one bridge crossing instead of four.
     *
     * @param userMessage The user's current message (for future retrieval-augmented lookup)
     * @param historyJson JSON array string of past messages [{role, text}]
     */
    @ReactMethod
    fun buildChatContext(userMessage: String, historyJson: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val prompt = ChatContextBuilder.build(ctx, userMessage, historyJson)
                promise.resolve(prompt)
            } catch (e: Exception) {
                promise.reject("BUILD_CONTEXT_ERROR", e.message ?: "Unknown error", e)
            }
        }
    }

    // ─── Event emitter ────────────────────────────────────────────────────────

    fun emitEvent(name: String, params: com.facebook.react.bridge.WritableMap) {
        ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(name, params)
    }

    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}
}
