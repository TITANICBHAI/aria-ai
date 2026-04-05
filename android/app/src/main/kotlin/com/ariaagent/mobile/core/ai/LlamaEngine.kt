package com.ariaagent.mobile.core.ai

/**
 * LlamaEngine — JNI wrapper around llama.cpp for on-device inference.
 *
 * Phase 1 status: Full JNI declarations wired. CMakeLists.txt written.
 * To activate: add llama.cpp as a submodule in android/app/src/main/cpp/
 *
 *   cd android/app/src/main/cpp
 *   git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
 *   git submodule update --init --recursive
 *   Then run: ./gradlew assembleDebug (from android/)
 *
 * Hardware target: Samsung Galaxy M31 (Exynos 9611)
 *   - Mali-G72 MP3 → Vulkan 1.1 → n_gpu_layers = 32 (all layers offloaded)
 *   - Cortex-A73 big cores → n_threads = 4
 *   - mmap = true → CRITICAL: keeps RSS ~1700MB instead of ~2.5GB
 *   - mlock = false → do NOT lock pages (would cause OOM on 6GB device)
 *
 * Model: Llama-3.2-1B-Instruct-Q4_K_M.gguf
 *   - Disk: ~870MB
 *   - RAM (RSS): ~1500–1900MB with mmap
 *   - Speed: 8–15 tok/s on Exynos 9611 with Vulkan offload
 *
 * Token callback interface (streamed to ChatScreen / AgentViewModel):
 *   interface TokenCallback { fun onToken(token: String) }
 */
object LlamaEngine {

    private var modelHandle: Long = 0L
    private var contextHandle: Long = 0L

    // Track last-used load params so GGUF hot-reload can reload with same settings
    private var lastModelPath: String = ""
    private var lastContextSize: Int = 4096
    private var lastNGpuLayers: Int = 32

    var lastToksPerSec: Double = 0.0
        private set

    var memoryMb: Double = 0.0
        private set

    private var jniAvailable = false

    fun isLoaded(): Boolean = modelHandle != 0L

    /**
     * Load the GGUF model from disk.
     * Calls nativeLoadModel() → nativeCreateContext() via JNI.
     *
     * @param path        Absolute path to the .gguf file in internal storage
     * @param contextSize Token context window (4096 for M31 — 128K causes OOM)
     * @param nGpuLayers  Layers to offload to GPU (32 = all layers for Q4_K_M 1B)
     */
    fun load(path: String, contextSize: Int = 4096, nGpuLayers: Int = 32) {
        lastModelPath    = path
        lastContextSize  = contextSize
        lastNGpuLayers   = nGpuLayers
        if (jniAvailable) {
            modelHandle   = nativeLoadModel(path, contextSize, nGpuLayers)
            contextHandle = if (modelHandle != 0L) nativeCreateContext(modelHandle) else 0L
            memoryMb      = if (isLoaded()) nativeGetMemoryMb() else 0.0
        } else {
            // Stub active when llama.cpp submodule not yet compiled
            modelHandle   = 1L
            contextHandle = 1L
            memoryMb      = 1700.0
        }
    }

    /**
     * Run a single inference turn with streaming token callback.
     *
     * @param prompt    Full formatted prompt (built by PromptBuilder)
     * @param maxTokens Maximum tokens to generate (200–512 for action responses)
     * @param onToken   Streaming callback — called once per token as it generates
     * @return          Full generated text (complete before returning)
     */
    suspend fun infer(
        prompt: String,
        maxTokens: Int = 512,
        onToken: ((String) -> Unit)? = null
    ): String {
        if (!isLoaded()) throw IllegalStateException("Model not loaded. Call load() first.")

        return if (jniAvailable) {
            val callback = onToken?.let { cb ->
                object : TokenCallback { override fun onToken(token: String) { cb(token) } }
            }
            val result = nativeRunInference(contextHandle, prompt, maxTokens, callback)
            lastToksPerSec = nativeGetToksPerSec()
            result
        } else {
            // Stub response for architecture testing without llama.cpp compiled
            val stub = """{"tool":"Click","node_id":"#1","reason":"stub inference — llama.cpp not compiled"}"""
            lastToksPerSec = 11.5
            onToken?.invoke(stub)
            stub
        }
    }

    fun unload() {
        if (jniAvailable) {
            if (contextHandle != 0L) nativeFreeContext(contextHandle)
            if (modelHandle != 0L) nativeFreeModel(modelHandle)
        }
        modelHandle   = 0L
        contextHandle = 0L
        lastToksPerSec = 0.0
        memoryMb = 0.0
    }

    // ─── JNI declarations ────────────────────────────────────────────────────
    // Implemented in llama_jni.cpp — compiled by CMakeLists.txt via NDK.
    // System.loadLibrary() is called in companion object init.

    private external fun nativeLoadModel(path: String, ctxSize: Int, nGpuLayers: Int): Long
    private external fun nativeCreateContext(modelHandle: Long): Long
    private external fun nativeRunInference(
        ctxHandle:     Long,
        prompt:        String,
        maxTokens:     Int,
        tokenCallback: TokenCallback?
    ): String
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeFreeContext(handle: Long)
    private external fun nativeGetToksPerSec(): Double
    private external fun nativeGetMemoryMb(): Double

    // ─── Token streaming callback interface ───────────────────────────────────
    // Passed through JNI to C++ — each generated token calls onToken().

    interface TokenCallback {
        fun onToken(token: String)
    }

    // ─── LoRA adapter loading ─────────────────────────────────────────────────

    private external fun nativeLoadLora(ctxHandle: Long, adapterPath: String, scale: Float): Boolean

    /**
     * Load a trained adapter or checkpoint on top of the current model.
     *
     * Detects the file type from its first 4 bytes:
     *   "GGUF" (0x47 0x47 0x55 0x46) → full GGUF model checkpoint produced by
     *     nativeTrainLora() via llama_model_save_to_file(). Unloads the current
     *     model and reloads using the checkpoint path, preserving contextSize and
     *     nGpuLayers from the original load() call.
     *   Otherwise → classic LoRA adapter binary; loaded via nativeLoadLora()
     *     (llama_adapter_lora_init + llama_set_adapters_lora).
     *
     * @param adapterPath Absolute path to the .gguf checkpoint or LoRA .bin file
     * @param scale       LoRA influence weight — ignored for GGUF checkpoints
     */
    fun loadLora(adapterPath: String, scale: Float = 0.8f): Boolean {
        if (!jniAvailable || !isLoaded()) return false
        return try {
            if (isGgufFile(adapterPath)) {
                // Full GGUF model checkpoint — unload inference model and reload
                // the fine-tuned weights as the new base model.
                android.util.Log.i("LlamaEngine",
                    "GGUF checkpoint detected — hot-reloading as base model: $adapterPath")
                unload()
                load(adapterPath, lastContextSize, lastNGpuLayers)
                isLoaded()
            } else {
                // Classic LoRA adapter binary — apply on top of loaded base model
                val ok = nativeLoadLora(contextHandle, adapterPath, scale)
                android.util.Log.i("LlamaEngine",
                    "LoRA adapter loaded: $adapterPath (scale=$scale) → $ok")
                ok
            }
        } catch (e: Exception) {
            android.util.Log.w("LlamaEngine", "loadLora failed: ${e.message}")
            false
        }
    }

    /**
     * Returns true if the file starts with the GGUF magic bytes (0x47 0x47 0x55 0x46 = "GGUF").
     * Used by loadLora() to distinguish trained GGUF checkpoints from LoRA adapter binaries.
     */
    private fun isGgufFile(path: String): Boolean {
        return try {
            java.io.RandomAccessFile(path, "r").use { f ->
                val magic = ByteArray(4)
                f.readFully(magic)
                magic[0] == 0x47.toByte() &&   // 'G'
                magic[1] == 0x47.toByte() &&   // 'G'
                magic[2] == 0x55.toByte() &&   // 'U'
                magic[3] == 0x46.toByte()      // 'F'
            }
        } catch (e: Exception) { false }
    }

    // companion object is invalid inside an `object` — use a bare init block instead.
    init {
        try {
            System.loadLibrary("llama-jni")
            // Library loaded successfully — activate real JNI inference path
            markJniAvailable()
            android.util.Log.i("LlamaEngine", "llama-jni loaded — real inference active")
        } catch (e: UnsatisfiedLinkError) {
            // llama.cpp not compiled yet (submodule missing) — stub mode active
            android.util.Log.w("LlamaEngine", "llama-jni not found — running in stub mode")
        }
    }

    /**
     * Called by init block after System.loadLibrary succeeds.
     * Switches from stub → real JNI inference.
     */
    internal fun markJniAvailable() {
        jniAvailable = true
    }
}
