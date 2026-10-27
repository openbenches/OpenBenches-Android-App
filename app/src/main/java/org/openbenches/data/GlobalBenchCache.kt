package org.openbenches.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-disk cache of all bench map points for offline / fast startup global map mode.
 */
data class CachedBenchPoint(
    val id: Int,
    val lat: Double,
    val lng: Double,
    val popup: String,
) {
    fun toBench(): Bench = Bench(
        id = id,
        coordinates = listOf(lng, lat),
        popupContent = popup,
    )
}

class GlobalBenchCache(private val context: Context) {
    private val gson = Gson()
    private val cacheFile: File
        get() = File(context.filesDir, "global_benches_cache.json")

    suspend fun load(): List<Bench>? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        try {
            val type = object : TypeToken<List<CachedBenchPoint>>() {}.type
            val points: List<CachedBenchPoint> = gson.fromJson(cacheFile.readText(), type) ?: return@withContext null
            if (points.isEmpty()) null else points.map { it.toBench() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(benches: List<Bench>) = withContext(Dispatchers.IO) {
        val points = benches.map { bench ->
            CachedBenchPoint(
                id = bench.id,
                lat = bench.lat,
                lng = bench.lng,
                popup = bench.popupContent,
            )
        }
        cacheFile.writeText(gson.toJson(points))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (cacheFile.exists()) cacheFile.delete()
    }
}
