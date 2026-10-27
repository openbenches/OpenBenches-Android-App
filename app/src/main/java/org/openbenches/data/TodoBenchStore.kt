package org.openbenches.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * A location where the user saw a bench but could not photograph it yet.
 */
data class TodoBench(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val note: String? = null,
    val savedAtMs: Long,
)

/**
 * Local JSON store for "to do" bench locations (no photos yet).
 */
class TodoBenchStore(private val context: Context) {
    private val gson = Gson()
    private val rootDir: File
        get() = File(context.filesDir, "todo_benches").also { it.mkdirs() }
    private val indexFile: File
        get() = File(rootDir, "todo_benches.json")

    fun list(): List<TodoBench> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = indexFile.readText()
            val type = object : TypeToken<List<TodoBench>>() {}.type
            gson.fromJson<List<TodoBench>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun count(): Int = list().size

    fun get(id: String): TodoBench? = list().find { it.id == id }

    @Synchronized
    fun add(latitude: Double, longitude: Double, note: String? = null): TodoBench {
        val item = TodoBench(
            id = UUID.randomUUID().toString(),
            latitude = latitude,
            longitude = longitude,
            note = note?.takeIf { it.isNotBlank() },
            savedAtMs = System.currentTimeMillis(),
        )
        val updated = list() + item
        writeAll(updated)
        return item
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = list()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false
        writeAll(updated)
        return true
    }

    @Synchronized
    fun clear() {
        writeAll(emptyList())
    }

    /** JSON export of the current list (same shape as the on-device store). */
    fun exportJson(): String = gson.toJson(list())

    /**
     * Parses a JSON array of [TodoBench] and appends items whose ids are not already present.
     * Items with blank ids get a new id. Existing ids are skipped (no duplicates).
     * @return number of items newly added
     */
    @Synchronized
    fun importAndAppend(json: String): Int {
        val type = object : TypeToken<List<TodoBench>>() {}.type
        val imported = try {
            gson.fromJson<List<TodoBench>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            throw IllegalArgumentException("Not a valid To-Do list file.")
        }
        if (imported.isEmpty()) return 0

        val current = list().toMutableList()
        val existingIds = current.map { it.id }.toMutableSet()
        var added = 0
        for (raw in imported) {
            if (raw.latitude !in -90.0..90.0 || raw.longitude !in -180.0..180.0) continue
            if (raw.id.isNotBlank() && raw.id in existingIds) continue
            val id = raw.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val item = TodoBench(
                id = id,
                latitude = raw.latitude,
                longitude = raw.longitude,
                note = raw.note?.takeIf { it.isNotBlank() },
                savedAtMs = if (raw.savedAtMs > 0L) raw.savedAtMs else System.currentTimeMillis(),
            )
            current.add(item)
            existingIds.add(item.id)
            added++
        }
        if (added > 0) writeAll(current)
        return added
    }

    private fun writeAll(items: List<TodoBench>) {
        indexFile.writeText(gson.toJson(items))
    }
}
