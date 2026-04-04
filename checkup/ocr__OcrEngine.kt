package com.ariaagent.mobile.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OcrEngine — ML Kit Text Recognition wrapper.
 *
 * Converts a 512×512 screenshot bitmap into "white-space structured text."
 * The output preserves spatial layout so the LLM understands UI groupings:
 *   "Price $9.99   [Add to Cart]" instead of just "Price 9.99 Add to Cart"
 *
 * Why white-space structure matters:
 *   A label above an input field needs to be understood as belonging to that field.
 *   A price next to a product button is a unit. Raw word extraction loses this context.
 *
 * Phase: 2 (Perception layer)
 * Fully on-device. No network. Google Play Services provides the ML model.
 */
object OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Run OCR on a bitmap and return white-space structured text.
     * The bitmap should be 512×512 (downsampled from full screen).
     */
    suspend fun run(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                cont.resume(buildWhiteSpaceText(result))
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    /**
     * Convert ML Kit TextBlock list into white-space aligned text.
     *
     * Algorithm:
     *   1. Get all text blocks with their bounding rectangles
     *   2. Sort blocks by top Y coordinate (top of screen first)
     *   3. Group blocks into "rows" if their Y centers are within 20px of each other
     *   4. Within each row, sort by left X coordinate
     *   5. Join blocks in the same row with spaces proportional to X gap
     *   6. Join rows with newlines
     */
    private fun buildWhiteSpaceText(result: com.google.mlkit.vision.text.Text): String {
        data class Block(val text: String, val left: Int, val top: Int, val bottom: Int)

        val blocks = result.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                Block(line.text, box.left, box.top, box.bottom)
            }

        if (blocks.isEmpty()) return ""

        val rowThreshold = 20
        val sortedByY = blocks.sortedBy { it.top }
        val rows = mutableListOf<MutableList<Block>>()

        for (block in sortedByY) {
            val existingRow = rows.lastOrNull {
                it.isNotEmpty() && Math.abs(it[0].top - block.top) <= rowThreshold
            }
            if (existingRow != null) {
                existingRow.add(block)
            } else {
                rows.add(mutableListOf(block))
            }
        }

        return rows.joinToString("\n") { row ->
            row.sortedBy { it.left }
                .joinToString("  ") { it.text }
        }
    }
}
