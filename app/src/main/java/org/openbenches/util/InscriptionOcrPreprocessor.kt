package org.openbenches.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.InputStream

/**
 * Prepares bench/inscription photos for PaddleOCR: downscale, grayscale, CLAHE contrast,
 * and a light unsharp pass. Falls back gracefully if OpenCV is unavailable.
 */
object InscriptionOcrPreprocessor {
    private const val TAG = "InscriptionOcrPrep"
    private const val MAX_LONG_EDGE_PX = 1600

    @Volatile
    private var openCvReady: Boolean? = null

    fun ensureOpenCvLoaded(): Boolean {
        openCvReady?.let { return it }
        return synchronized(this) {
            openCvReady?.let { return it }
            val ok = try {
                OpenCVLoader.initLocal()
            } catch (e: Exception) {
                Log.e(TAG, "OpenCV init failed", e)
                false
            }
            openCvReady = ok
            ok
        }
    }

    /**
     * Loads [uri], applies EXIF orientation, and runs OpenCV preprocessing.
     * Returns null if decode or OpenCV fails (caller should fall back to the raw image).
     */
    fun loadAndPreprocess(context: Context, uri: Uri): Bitmap? {
        val oriented = loadOrientedBitmap(context, uri) ?: return null
        val processed = try {
            preprocessForOcr(oriented)
        } catch (e: Exception) {
            Log.w(TAG, "Preprocess failed; using oriented bitmap", e)
            null
        }
        return if (processed != null) {
            if (processed !== oriented && !oriented.isRecycled) {
                oriented.recycle()
            }
            processed
        } else {
            oriented
        }
    }

    /**
     * OpenCV pipeline on an already-decoded bitmap. Returns a new ARGB bitmap, or null on failure.
     */
    fun preprocessForOcr(source: Bitmap): Bitmap? {
        if (!ensureOpenCvLoaded()) return null

        var rgba = Mat()
        var working = Mat()
        var gray = Mat()
        var enhanced = Mat()
        var blurred = Mat()
        var sharpened = Mat()
        try {
            val argb = if (source.config == Bitmap.Config.ARGB_8888) {
                source
            } else {
                source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            }
            Utils.bitmapToMat(argb, rgba)
            if (argb !== source && !argb.isRecycled) {
                argb.recycle()
            }

            when (rgba.channels()) {
                4 -> Imgproc.cvtColor(rgba, working, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(rgba, working, Imgproc.COLOR_RGB2GRAY)
                1 -> rgba.copyTo(working)
                else -> {
                    rgba.convertTo(working, CvType.CV_8UC1)
                }
            }

            val longEdge = maxOf(working.cols(), working.rows())
            if (longEdge > MAX_LONG_EDGE_PX) {
                val scale = MAX_LONG_EDGE_PX.toDouble() / longEdge
                Imgproc.resize(
                    working,
                    gray,
                    Size(working.cols() * scale, working.rows() * scale),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA,
                )
            } else {
                working.copyTo(gray)
            }

            // Local contrast boost helps engraved/metal plaques under uneven light.
            val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
            clahe.apply(gray, enhanced)

            // Mild unsharp mask to restore edge clarity after CLAHE.
            Imgproc.GaussianBlur(enhanced, blurred, Size(0.0, 0.0), 1.0)
            Core.addWeighted(enhanced, 1.4, blurred, -0.4, 0.0, sharpened)

            // matToBitmap expects 1/3/4 channels; convert gray → RGBA for a stable ARGB Bitmap.
            Imgproc.cvtColor(sharpened, rgba, Imgproc.COLOR_GRAY2RGBA)
            val out = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, out)
            return out
        } catch (e: Exception) {
            Log.w(TAG, "OpenCV preprocess error", e)
            return null
        } finally {
            rgba.release()
            working.release()
            gray.release()
            enhanced.release()
            blurred.release()
            sharpened.release()
        }
    }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openImageStream(resolver, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, MAX_LONG_EDGE_PX)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openImageStream(resolver, uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val orientation = readExifOrientation(resolver, uri)
        return applyExifOrientation(decoded, orientation)
    }

    private fun openImageStream(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val requireOriginal = MediaStore.setRequireOriginal(uri)
                resolver.openInputStream(requireOriginal)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return resolver.openInputStream(uri)
    }

    private fun readExifOrientation(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): Int {
        return try {
            openImageStream(resolver, uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / (sample * 2) >= maxLongEdge) {
            sample *= 2
        }
        return sample
    }
}
