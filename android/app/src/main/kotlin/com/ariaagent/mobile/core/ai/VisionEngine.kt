package com.ariaagent.mobile.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * VisionEngine — Multimodal screen understanding via SmolVLM-256M + llama.cpp mtmd.
 *
 * Architecture:
 *   • Vision base GGUF  (~150 MB)  — SmolVLM-256M-Instruct-Q4_K_M.gguf
 *   • mmproj GGUF       (~50 MB)   — mmproj-SmolVLM-256M-Instruct-f16.gguf
 *
 * Both are downloaded on demand (not bundled in the APK) to internal storage.
 * The vision model uses completely independent LlamaEngine handles so it does
 * not interfere with the primary Llama 3.2-1B text model.
 *
 * Frame compression (aspect-preserving letterbox):
 *   Phone screens are portrait (e.g. 1080×2400). Naive square scaling distorts the UI.
 *   compressFrame() letterboxes the bitmap into a 384×384 canvas with black bars,
 *   preserving the screen's true aspect ratio. The JPEG size is still ~35–55 KB.
 *
 * Frame caching:
 *   Each call to describe() stores (screenHash → description). If the next call
 *   has the same hash, the cached description is returned immediately — no inference.
 *   This saves ~400 ms on steps where the screen has not changed.
 *
 * Goal-aware prompting:
 *   describe() accepts the agent's current goal and injects it into the vision prompt,
 *   so SmolVLM answers a targeted question rather than a generic screen description.
 *   Example prompt: "Goal: Turn off Bluetooth. What UI elements relate to Bluetooth on this screen?"
 *
 * Thread safety:
 *   ensureLoaded() uses a @Synchronized guard so concurrent coroutines cannot double-load.
 *
 * Phase 17 — Multimodal.
 */
object VisionEngine {

    private const val TAG = "VisionEngine"

    // ── SmolVLM-256M from ggml-org on HuggingFace ────────────────────────────

    const val VISION_MODEL_URL =
        "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/" +
        "SmolVLM-256M-Instruct-Q4_K_M.gguf"

    const val MMPROJ_URL =
        "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/" +
        "mmproj-SmolVLM-256M-Instruct-f16.gguf"

    const val VISION_MODEL_FILENAME = "smolvlm-256m-q4_k_m.gguf"
    const val MMPROJ_FILENAME       = "smolvlm-256m-mmproj-f16.gguf"

    const val VISION_MODEL_MIN_BYTES = 140_000_000L   // ~150 MB expected
    const val MMPROJ_MIN_BYTES       =  40_000_000L   // ~50 MB expected

    /** Native resolution expected by SmolVLM CLIP encoder. */
    private const val VISION_RES = 384

    /** JPEG quality — 82 keeps file ~35–55 KB while preserving UI text readability. */
    private const val JPEG_QUALITY = 82

    // ── Frame cache ───────────────────────────────────────────────────────────

    @Volatile private var lastCacheKey: String = ""
    @Volatile private var lastCacheDesc: String = ""

    // ── File paths ────────────────────────────────────────────────────────────

    fun modelDir(context: Context): File =
        File(context.filesDir, "models").also { it.mkdirs() }

    fun visionModelPath(context: Context): File =
        File(modelDir(context), VISION_MODEL_FILENAME)

    fun mmProjPath(context: Context): File =
        File(modelDir(context), MMPROJ_FILENAME)

    fun visionModelPartial(context: Context): File =
        File(modelDir(context), "$VISION_MODEL_FILENAME.part")

    fun mmProjPartial(context: Context): File =
        File(modelDir(context), "$MMPROJ_FILENAME.part")

    // ── Readiness checks ──────────────────────────────────────────────────────

    fun isVisionModelFileReady(context: Context): Boolean {
        val f = visionModelPath(context)
        return f.exists() && f.length() >= VISION_MODEL_MIN_BYTES
    }

    fun isMmProjReady(context: Context): Boolean {
        val f = mmProjPath(context)
        return f.exists() && f.length() >= MMPROJ_MIN_BYTES
    }

    /** Both files present → vision can be loaded into LlamaEngine. */
    fun isVisionModelReady(context: Context): Boolean =
        isVisionModelFileReady(context) && isMmProjReady(context)

    fun mmProjDownloadedBytes(context: Context): Long {
        val partial = mmProjPartial(context)
        return if (partial.exists()) partial.length()
        else if (mmProjPath(context).exists()) mmProjPath(context).length()
        else 0L
    }

    fun visionModelDownloadedBytes(context: Context): Long {
        val partial = visionModelPartial(context)
        return if (partial.exists()) partial.length()
        else if (visionModelPath(context).exists()) visionModelPath(context).length()
        else 0L
    }

    // ── Load / unload ─────────────────────────────────────────────────────────

    /**
     * Ensure the vision model is loaded into LlamaEngine.
     * Thread-safe — a @Synchronized lock prevents double-loading from parallel coroutines.
     * No-op if already loaded. Returns false if files are missing or load fails.
     */
    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (LlamaEngine.isVisionLoaded()) return true
        if (!isVisionModelReady(context)) {
            Log.w(TAG, "Vision model files not ready — download first")
            return false
        }
        val ok = LlamaEngine.loadVision(
            visionModelPath = visionModelPath(context).absolutePath,
            mmProjPath      = mmProjPath(context).absolutePath,
            contextSize     = 2048,
            nGpuLayers      = 0    // CPU-only — Mali-G72 + mtmd is unstable with GPU layers
        )
        Log.i(TAG, if (ok) "Vision loaded OK" else "Vision load FAILED")
        return ok
    }

    fun unload() {
        lastCacheKey = ""
        lastCacheDesc = ""
        LlamaEngine.unloadVision()
        Log.i(TAG, "Vision unloaded — cache cleared")
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    /**
     * Describe the current screen frame, optionally grounded in [goal].
     *
     * Caching: if [screenHash] matches the previous call, the cached description is
     * returned without running inference (~0 ms vs ~400 ms). Pass an empty string
     * to bypass the cache.
     *
     * Goal-aware prompting: when [goal] is non-empty, the vision prompt becomes a
     * targeted question ("What elements on screen relate to: [goal]?") rather than
     * a generic description. This produces more relevant context for the LLM.
     *
     * @param bitmap      Current screen frame from ScreenObserver
     * @param goal        Agent's current task goal (empty = generic description)
     * @param screenHash  Screen identity key for frame deduplication (from ScreenSnapshot.screenHash())
     * @param maxTokens   Token cap — 96 tokens ≈ 2–3 sentences, enough for screen context
     * @return            Vision description, or "" on failure/unavailability
     */
    suspend fun describe(
        context: Context,
        bitmap: Bitmap,
        goal: String = "",
        screenHash: String = "",
        maxTokens: Int = 96
    ): String {
        // ── Cache hit — screen hasn't changed ─────────────────────────────────
        if (screenHash.isNotEmpty() && screenHash == lastCacheKey && lastCacheDesc.isNotEmpty()) {
            Log.d(TAG, "Vision cache hit: $screenHash")
            return lastCacheDesc
        }

        if (!ensureLoaded(context)) return ""

        return try {
            val prompt = buildVisionPrompt(goal)
            val imageBytes = compressFrame(bitmap)
            val desc = LlamaEngine.inferWithVision(imageBytes, prompt, maxTokens)

            // Store in cache
            if (screenHash.isNotEmpty() && desc.isNotBlank()) {
                lastCacheKey  = screenHash
                lastCacheDesc = desc
            }
            desc
        } catch (e: Exception) {
            Log.w(TAG, "Vision inference failed: ${e.message}")
            ""
        }
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    /**
     * Build a goal-aware vision prompt.
     *
     * With goal:    "Goal: Open Bluetooth settings. Which UI elements on this Android screen
     *                relate to Bluetooth or Settings? Be concise."
     * Without goal: "Describe the Android UI on screen. List visible interactive elements
     *                (buttons, menus, text fields) and any important text. 2–3 sentences max."
     */
    private fun buildVisionPrompt(goal: String): String {
        return if (goal.isNotBlank()) {
            "Goal: $goal. " +
            "Which UI elements on this Android screen are relevant to achieving this goal? " +
            "Describe only what matters. Be concise — 2 sentences max."
        } else {
            "Describe the Android UI visible on this screen. " +
            "List key interactive elements (buttons, menus, text fields) and any visible text. " +
            "Be concise — 2–3 sentences max."
        }
    }

    // ── Aspect-preserving frame compression ───────────────────────────────────

    /**
     * Letterbox [bitmap] into a VISION_RES×VISION_RES canvas (black background) and
     * JPEG-compress it to a byte array.
     *
     * Why letterbox instead of stretch:
     *   A phone screen at 1080×2400 has a 1:2.22 aspect ratio. Squishing it to 384×384
     *   compresses vertical space by 2.22×, making buttons appear 2× wider than tall and
     *   confusing SmolVLM's spatial reasoning. Letterboxing preserves true proportions.
     *
     * The black bars occupy ~22% of the 384×384 canvas (sides) for portrait content,
     * which SmolVLM ignores — it understands letterbox format from its training data.
     */
    private fun compressFrame(bitmap: Bitmap): ByteArray {
        val canvas = Bitmap.createBitmap(VISION_RES, VISION_RES, Bitmap.Config.RGB_565)
        val androidCanvas = Canvas(canvas)
        androidCanvas.drawColor(Color.BLACK)

        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val scale = minOf(VISION_RES / srcW, VISION_RES / srcH)
        val dstW  = (srcW * scale).toInt()
        val dstH  = (srcH * scale).toInt()
        val dstX  = (VISION_RES - dstW) / 2
        val dstY  = (VISION_RES - dstH) / 2

        val dst = Rect(dstX, dstY, dstX + dstW, dstY + dstH)
        androidCanvas.drawBitmap(bitmap, null, dst, null as Paint?)

        return ByteArrayOutputStream(56_000).use { baos ->
            canvas.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            canvas.recycle()
            baos.toByteArray()
        }
    }

    // ── Download helpers ──────────────────────────────────────────────────────

    /**
     * Download a single file from [url] to [dest], resuming from [partial].
     * Reports progress via [onProgress] (bytesDownloaded, totalBytes).
     * Finalizes by renaming [partial] → [dest].
     *
     * Called by AgentViewModel.downloadVisionModel() on an IO coroutine.
     */
    fun downloadFile(
        url: String,
        dest: File,
        partial: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val resumeFrom = if (partial.exists()) partial.length() else 0L

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AriaAgent/1.0")
            .apply { if (resumeFrom > 0) addHeader("Range", "bytes=$resumeFrom-") }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP ${response.code} for $url")
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body
                    ?: throw Exception("Empty response body for $url")
                val contentLength = body.contentLength()
                val totalBytes    = if (resumeFrom > 0 && contentLength > 0)
                    resumeFrom + contentLength else contentLength

                var downloaded = resumeFrom
                FileOutputStream(partial, resumeFrom > 0).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(256 * 1024)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            onProgress(downloaded, totalBytes)
                        }
                    }
                }
                partial.renameTo(dest).also { renamed ->
                    if (!renamed) throw Exception("Failed to rename partial → final: $dest")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $url: ${e.message}")
            throw e
        }
    }
}
