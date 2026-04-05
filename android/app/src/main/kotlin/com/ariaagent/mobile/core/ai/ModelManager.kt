package com.ariaagent.mobile.core.ai

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * ModelManager — single source of truth for GGUF model state on disk.
 *
 * Model: Llama-3.2-1B-Instruct-Q4_K_M.gguf
 * Source: bartowski/Llama-3.2-1B-Instruct-GGUF on HuggingFace
 * Size: ~870 MB disk, ~1700 MB RSS on Exynos 9611
 *
 * The model is NEVER bundled in the APK. It is downloaded at first launch
 * by ModelDownloadService and stored in internal storage forever after.
 */
object ModelManager {

    const val MODEL_URL =
        "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/" +
        "Llama-3.2-1B-Instruct-Q4_K_M.gguf"

    const val MODEL_FILENAME = "llama-3.2-1b-q4_k_m.gguf"

    const val EXPECTED_SIZE_BYTES = 780_000_000L

    // SHA256 of the exact bartowski Q4_K_M file — verify after download
    // Update this if HuggingFace re-uploads the file
    const val EXPECTED_SHA256 = "VERIFY_AFTER_FIRST_DOWNLOAD"

    fun modelDir(context: Context): File =
        (context.getExternalFilesDir("models") ?: File(context.filesDir, "models"))
            .also { it.mkdirs() }

    fun modelPath(context: Context): File =
        File(modelDir(context), MODEL_FILENAME)

    fun partialPath(context: Context): File =
        File(modelDir(context), "$MODEL_FILENAME.part")

    /**
     * Returns true if the GGUF is fully present and has the expected minimum size.
     * Does not check SHA256 on every launch (too slow) — only after download.
     */
    fun isModelReady(context: Context): Boolean {
        val f = modelPath(context)
        return f.exists() && f.length() >= EXPECTED_SIZE_BYTES
    }

    /** Bytes already downloaded (partial file size, for resume). */
    fun downloadedBytes(context: Context): Long {
        val partial = partialPath(context)
        return if (partial.exists()) partial.length() else 0L
    }

    /** Move the partial file to the final path after verified download. */
    fun finalizeDownload(context: Context): Boolean {
        val partial = partialPath(context)
        val final = modelPath(context)
        return partial.renameTo(final)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
