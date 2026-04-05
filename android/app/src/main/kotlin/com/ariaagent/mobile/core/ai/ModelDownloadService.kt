package com.ariaagent.mobile.core.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ariaagent.mobile.core.events.AgentEventBus
import com.ariaagent.mobile.ui.ComposeMainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * ModelDownloadService — foreground service that downloads a GGUF model.
 *
 * Survives app backgrounding. Shows a persistent notification with live progress.
 * Supports resuming partial downloads via HTTP Range headers.
 * Emits progress events to AgentEventBus (consumed by AgentViewModel → Compose UI).
 *
 * The model to download is specified by passing EXTRA_MODEL_ID in the start intent.
 * If omitted, falls back to the currently active model in ModelManager.
 *
 * Flow:
 *   1. Resolve CatalogModel from EXTRA_MODEL_ID (or active model)
 *   2. Check if partial download exists → resume from byte offset
 *   3. Send Range: bytes=<offset>- header to HuggingFace
 *   4. Stream to partial file, emitting progress every 2 MB
 *   5. On completion: rename partial → final, emit model_download_complete
 *   6. Stop self
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val CHANNEL_ID        = "aria_model_download"
        private const val NOTIF_ID          = 1001
        /** Pass a ModelCatalog ID string to download a specific model. */
        const val EXTRA_MODEL_ID            = "aria_model_id"
    }

    /** The catalog entry being downloaded this session (resolved in onStartCommand). */
    private var targetModel: CatalogModel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        targetModel = if (modelId != null) {
            ModelCatalog.findById(modelId) ?: ModelManager.activeEntry(this)
        } else {
            ModelManager.activeEntry(this)
        }
        startForeground(NOTIF_ID, buildNotification("Starting download…", 0))
        scope.launch { download() }
        return START_STICKY
    }

    private suspend fun download() {
        val model       = targetModel ?: ModelManager.activeEntry(this)
        val partial     = ModelManager.partialPathFor(this, model.id)
        val resumeFrom  = if (partial.exists()) partial.length() else 0L
        val totalExpected = model.expectedSizeBytes

        val request = Request.Builder()
            .url(model.url)
            .apply { if (resumeFrom > 0) addHeader("Range", "bytes=$resumeFrom-") }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emitError(model.id, "HTTP ${response.code}: ${response.message}")
                    stopSelf()
                    return
                }

                val body = response.body ?: run {
                    emitError(model.id, "Empty response body")
                    stopSelf()
                    return
                }

                val serverTotal = (body.contentLength()
                    .takeIf { it > 0 } ?: (totalExpected - resumeFrom)) + resumeFrom

                var downloaded  = resumeFrom
                var lastEmitAt  = resumeFrom
                val emitEveryBytes = 2_000_000L
                val startTime   = System.currentTimeMillis()

                FileOutputStream(partial, resumeFrom > 0).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(32_768)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read

                            if (downloaded - lastEmitAt >= emitEveryBytes) {
                                lastEmitAt = downloaded
                                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                                val speed   = if (elapsed > 0) (downloaded - resumeFrom) / elapsed / 1_000_000 else 0.0
                                val pct     = ((downloaded.toDouble() / serverTotal) * 100).roundToInt()
                                val dlMb    = downloaded / 1_000_000.0
                                val totalMb = serverTotal / 1_000_000.0

                                updateNotification("Downloading ${model.displayName}… $pct%", pct)
                                emitProgress(model.id, pct, dlMb, totalMb, speed)
                            }
                        }
                    }
                }

                val success = ModelManager.finalizeDownloadFor(this, model.id)
                if (success && ModelManager.isModelDownloaded(this, model.id)) {
                    emitComplete(model.id, ModelManager.modelPathFor(this, model.id).absolutePath)
                } else {
                    emitError(model.id, "Failed to finalize download or file size mismatch")
                }
            }
        } catch (e: Exception) {
            emitError(model.id, e.message ?: "Unknown download error")
        } finally {
            stopSelf()
        }
    }

    // ── Event helpers ─────────────────────────────────────────────────────────

    private fun emitProgress(modelId: String, pct: Int, dlMb: Double, totalMb: Double, speedMbps: Double) {
        AgentEventBus.emit("model_download_progress", mapOf(
            "modelId"      to modelId,
            "percent"      to pct,
            "downloadedMb" to dlMb,
            "totalMb"      to totalMb,
            "speedMbps"    to speedMbps
        ))
    }

    private fun emitComplete(modelId: String, path: String) {
        AgentEventBus.emit("model_download_complete", mapOf("modelId" to modelId, "path" to path))
    }

    private fun emitError(modelId: String, error: String) {
        AgentEventBus.emit("model_download_error", mapOf("modelId" to modelId, "error" to error))
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ARIA Model Download",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Downloading an on-device AI model" }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARIA Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, ComposeMainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String, progress: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text, progress))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
