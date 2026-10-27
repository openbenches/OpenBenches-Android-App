package org.openbenches.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device inscription OCR: OpenCV preprocess + official PaddleOCR Android ONNX SDK
 * (PP-OCRv5 mobile models via Microsoft onnxruntime-android, 16 KB page-size aligned).
 */
object InscriptionOcr {
    private const val TAG = "InscriptionOcr"

    private val mutex = Mutex()

    @Volatile
    private var engine: PaddleOCR? = null

    @Volatile
    private var initFailed = false

    suspend fun ensureEngine(context: Context): PaddleOCR? {
        engine?.let { return it }
        if (initFailed) return null
        return mutex.withLock {
            engine?.let { return it }
            if (initFailed) return null
            try {
                InscriptionOcrPreprocessor.ensureOpenCvLoaded()
                if (!OpenCVUtils.init(context.applicationContext)) {
                    throw IllegalStateException("OpenCV native library failed to load")
                }
                val created = PaddleOCR.create(
                    context = context.applicationContext,
                    config = PaddleOCRConfig(
                        detThresh = 0.3f,
                        detBoxThresh = 0.5f,
                        detUnclipRatio = 1.8f,
                        recScoreThresh = 0.0f,
                    ),
                    engineConfig = EngineConfig(numThreads = 4),
                )
                engine = created
                created
            } catch (e: Exception) {
                initFailed = true
                Log.e(TAG, "Failed to init PaddleOCR engine", e)
                null
            }
        }
    }

    /**
     * Loads [uri], preprocesses with OpenCV when possible, runs PaddleOCR, and returns
     * assembled inscription text (or empty string on failure).
     */
    suspend fun recognizeInscription(context: Context, uri: Uri): String {
        val ocr = ensureEngine(context) ?: return ""
        val bitmap = InscriptionOcrPreprocessor.loadAndPreprocess(context, uri) ?: return ""
        return try {
            recognizeBitmap(ocr, bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Blocking helper for callers that are not already in a coroutine (e.g. background Thread).
     */
    fun ensureEngineBlocking(context: Context): PaddleOCR? = runBlocking {
        ensureEngine(context)
    }

    suspend fun recognizeBitmap(ocr: PaddleOCR, input: Bitmap): String {
        return try {
            val result = ocr.recognize(input)
            assemblePaddleInscriptionText(result.results).ifBlank {
                result.results.joinToString("\n") { it.text.trim() }.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PaddleOCR recognize failed", e)
            ""
        }
    }

    /**
     * Runs OCR on an already-decoded [bitmap] (e.g. a user-selected crop).
     * Applies OpenCV preprocess when available; does not recycle [bitmap].
     */
    suspend fun recognizeFromBitmap(context: Context, bitmap: Bitmap): String {
        if (bitmap.width <= 0 || bitmap.height <= 0) return ""
        val ocr = ensureEngine(context) ?: return ""
        val prepared = try {
            InscriptionOcrPreprocessor.preprocessForOcr(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Crop preprocess failed; using raw crop", e)
            null
        }
        return try {
            recognizeBitmap(ocr, prepared ?: bitmap)
        } finally {
            if (prepared != null && prepared !== bitmap && !prepared.isRecycled) {
                prepared.recycle()
            }
        }
    }
}

/**
 * Builds inscription text from PaddleOCR [OCRResult]s (top-to-bottom, left-to-right).
 */
fun assemblePaddleInscriptionText(results: List<OCRResult>): String {
    val lines = results.mapNotNull { item ->
        val text = item.text.trim()
        if (text.isEmpty()) return@mapNotNull null
        val points = item.box.points
        if (points.isEmpty()) {
            OcrLineInput(0, 0, 0, text)
        } else {
            val top = points.minOf { it.y.toInt() }
            val left = points.minOf { it.x.toInt() }
            val bottom = points.maxOf { it.y.toInt() }
            OcrLineInput(top, left, (bottom - top).coerceAtLeast(1), text)
        }
    }
    return assembleInscriptionTextFromLines(lines)
}
