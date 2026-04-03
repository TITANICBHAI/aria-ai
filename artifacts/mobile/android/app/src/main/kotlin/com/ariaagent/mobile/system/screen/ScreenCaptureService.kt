package com.ariaagent.mobile.system.screen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

/**
 * ScreenCaptureService — MediaProjection screen capture.
 *
 * Captures device display, downsamples to 512×512, saves to internal storage.
 * The screenshot is then read by OcrEngine (text) and fed to the LLM (context).
 *
 * Why 512×512?
 *   - Exynos 9611 memory bandwidth is limited (Mali-G72 MP3 shares memory with CPU)
 *   - Full 1080×2340 captures would exhaust bandwidth and crash
 *   - 512×512 is sufficient for OCR text recognition and UI element detection
 *   - ML Kit OCR works well at this resolution
 *
 * Capture rate: 1-2 FPS for navigation (not continuous — thermal risk)
 *
 * Phase: 2 (Perception)
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "aria_screen_capture"
        private const val NOTIF_ID = 1002
        private const val CAPTURE_WIDTH = 512
        private const val CAPTURE_HEIGHT = 512

        var isActive = false
            private set

        private var latestScreenshotPath: String? = null

        /**
         * Returns the latest captured Bitmap, or null if no capture yet.
         * The bitmap is 512×512 downsampled.
         */
        fun captureLatest(): Bitmap? {
            val path = latestScreenshotPath ?: return null
            return android.graphics.BitmapFactory.decodeFile(path)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isActive = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: return START_NOT_STICKY
        val projectionIntent = intent.getParcelableExtra<Intent>("projectionData") ?: return START_NOT_STICKY

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, projectionIntent)

        setupCapture()
        return START_STICKY
    }

    private fun setupCapture() {
        val mp = mediaProjection ?: return
        val metrics = resources.displayMetrics

        imageReader = ImageReader.newInstance(
            CAPTURE_WIDTH, CAPTURE_HEIGHT,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mp.createVirtualDisplay(
            "ARIACapture",
            CAPTURE_WIDTH, CAPTURE_HEIGHT, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                saveImage(image)
            }
        }, null)
    }

    private fun saveImage(image: android.media.Image) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val rowStride = planes[0].rowStride
        val pixelStride = planes[0].pixelStride
        val rowPadding = rowStride - pixelStride * CAPTURE_WIDTH

        val bitmap = Bitmap.createBitmap(
            CAPTURE_WIDTH + rowPadding / pixelStride,
            CAPTURE_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop to exact size if padding was added
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT)

        val screenshotFile = File(filesDir, "screenshots/latest.jpg").also {
            it.parentFile?.mkdirs()
        }
        FileOutputStream(screenshotFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        latestScreenshotPath = screenshotFile.absolutePath
        bitmap.recycle()
        if (cropped != bitmap) cropped.recycle()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ARIA Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARIA Agent")
            .setContentText("Screen observation active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
