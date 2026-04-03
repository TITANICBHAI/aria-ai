package com.ariaagent.mobile.core.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ariaagent.mobile.MainActivity
import com.ariaagent.mobile.bridge.AgentCoreModule
import com.facebook.react.bridge.Arguments
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
 * ModelDownloadService — foreground service that downloads the GGUF model.
 *
 * Survives app backgrounding. Shows a persistent notification with live progress.
 * Supports resuming partial downloads using HTTP Range headers.
 * Emits progress events to JS via AgentCoreModule.
 *
 * Flow:
 *   1. Check if partial download exists (partialPath) → resume from byte offset
 *   2. Send Range: bytes=<offset>- header to HuggingFace
 *   3. Stream to partial file, emitting progress every 2MB
 *   4. On completion: rename partial → final, emit model_download_complete
 *   5. Stop self
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val CHANNEL_ID = "aria_model_download"
        private const val NOTIF_ID = 1001
        private var module: AgentCoreModule? = null

        fun setModule(m: AgentCoreModule) { module = m }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Starting download...", 0))
        scope.launch { download() }
        return START_STICKY
    }

    private suspend fun download() {
        val partial = ModelManager.partialPath(this)
        val resumeFrom = if (partial.exists()) partial.length() else 0L
        val totalExpected = ModelManager.EXPECTED_SIZE_BYTES

        val request = Request.Builder()
            .url(ModelManager.MODEL_URL)
            .apply { if (resumeFrom > 0) addHeader("Range", "bytes=$resumeFrom-") }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emitError("HTTP ${response.code}: ${response.message}")
                    stopSelf()
                    return
                }

                val body = response.body ?: run {
                    emitError("Empty response body")
                    stopSelf()
                    return
                }

                val serverTotal = (body.contentLength()
                    .takeIf { it > 0 } ?: (totalExpected - resumeFrom)) + resumeFrom

                var downloaded = resumeFrom
                var lastEmitAt = resumeFrom
                val emitEveryBytes = 2_000_000L // emit every 2MB
                val startTime = System.currentTimeMillis()

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
                                val speed = if (elapsed > 0) (downloaded - resumeFrom) / elapsed / 1_000_000 else 0.0
                                val pct = ((downloaded.toDouble() / serverTotal) * 100).roundToInt()
                                val dlMb = downloaded / 1_000_000.0
                                val totalMb = serverTotal / 1_000_000.0

                                updateNotification("Downloading AI brain... $pct%", pct)
                                emitProgress(pct, dlMb, totalMb, speed)
                            }
                        }
                    }
                }

                // Download complete
                val success = ModelManager.finalizeDownload(this)
                if (success && ModelManager.isModelReady(this)) {
                    emitComplete(ModelManager.modelPath(this).absolutePath)
                } else {
                    emitError("Failed to finalize download or file size mismatch")
                }
            }
        } catch (e: Exception) {
            emitError(e.message ?: "Unknown download error")
        } finally {
            stopSelf()
        }
    }

    private fun emitProgress(pct: Int, dlMb: Double, totalMb: Double, speedMbps: Double) {
        module?.emitEvent("model_download_progress", Arguments.createMap().apply {
            putInt("percent", pct)
            putDouble("downloadedMb", dlMb)
            putDouble("totalMb", totalMb)
            putDouble("speedMbps", speedMbps)
        })
    }

    private fun emitComplete(path: String) {
        module?.emitEvent("model_download_complete", Arguments.createMap().apply {
            putString("path", path)
        })
    }

    private fun emitError(error: String) {
        module?.emitEvent("model_download_error", Arguments.createMap().apply {
            putString("error", error)
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ARIA Model Download",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Downloading the on-device AI model" }
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
                    Intent(this, MainActivity::class.java),
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
