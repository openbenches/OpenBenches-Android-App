package org.openbenches

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import io.sentry.android.core.SentryAndroid
import org.openbenches.util.InscriptionOcr
import org.openbenches.util.InscriptionOcrPreprocessor

class OpenBenchesApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        if (!InscriptionOcrPreprocessor.ensureOpenCvLoaded()) {
            Log.w(TAG, "OpenCV failed to load; OCR will use unprocessed photos")
        }
        // Warm PaddleOCR (loads ONNX models from assets) off the critical path of first photo.
        Thread({
            if (InscriptionOcr.ensureEngineBlocking(this) == null) {
                Log.w(TAG, "PaddleOCR engine failed to init; inscription auto-fill disabled")
            }
        }, "paddle-ocr-init").start()

        SentryAndroid.init(this) { options ->
            options.dsn = "https://examplePublicKey@o0.ingest.sentry.io/0" // TODO: Replace with your Sentry DSN
        }
    }

    companion object {
        private const val TAG = "OpenBenchesApp"
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
