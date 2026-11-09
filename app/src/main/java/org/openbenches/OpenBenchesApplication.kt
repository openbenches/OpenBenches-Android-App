package org.openbenches

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder

class OpenBenchesApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // OCR/OpenCV are warmed lazily when Add Bench opens — not on every cold start.
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
