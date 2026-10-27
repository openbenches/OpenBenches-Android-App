package org.openbenches.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class PendingBenchUpload(
    val id: String,
    val inscription: String,
    val latitude: Double,
    val longitude: Double,
    val photoFileNames: List<String>,
    val photoTypes: List<String>,
    val savedAtMs: Long,
)

class PendingBenchUploadStore(private val context: Context) {
    private val gson = Gson()
    private val rootDir: File
        get() = File(context.filesDir, "pending_bench_uploads").also { it.mkdirs() }
    private val indexFile: File
        get() = File(rootDir, "pending_uploads.json")

    fun list(): List<PendingBenchUpload> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = indexFile.readText()
            val type = object : TypeToken<List<PendingBenchUpload>>() {}.type
            gson.fromJson<List<PendingBenchUpload>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun count(): Int = list().size

    fun get(id: String): PendingBenchUpload? = list().find { it.id == id }

    fun directoryFor(id: String): File = File(rootDir, id)

    fun getPhotoFiles(upload: PendingBenchUpload): List<File> {
        val dir = directoryFor(upload.id)
        return upload.photoFileNames.map { File(dir, it) }.filter { it.exists() }
    }

    suspend fun save(
        inscription: String,
        latitude: Double,
        longitude: Double,
        photos: List<Pair<Int, Uri>>,
        photoTypes: List<String>,
    ): Result<PendingBenchUpload> = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val dir = directoryFor(id)
            dir.mkdirs()

            val fileNames = mutableListOf<String>()
            val types = mutableListOf<String>()
            val resolver = context.contentResolver

            photos.forEach { (index, uri) ->
                val fileName = "photo_$index.jpg"
                val dest = File(dir, fileName)
                if (!copyUriToFile(resolver, uri, dest, dir)) {
                    return@withContext Result.failure(
                        IllegalStateException("Could not read photo ${index + 1} for saving.")
                    )
                }
                fileNames.add(fileName)
                types.add(photoTypes.getOrElse(index) { "Long shot of the bench" })
            }

            val pending = PendingBenchUpload(
                id = id,
                inscription = inscription,
                latitude = latitude,
                longitude = longitude,
                photoFileNames = fileNames,
                photoTypes = types,
                savedAtMs = System.currentTimeMillis(),
            )

            val updated = list() + pending
            writeIndex(updated)
            Result.success(pending)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun delete(id: String) {
        directoryFor(id).deleteRecursively()
        writeIndex(list().filter { it.id != id })
    }

    /** Overwrites an existing saved bench (same id); used when editing a pending upload. */
    suspend fun replace(
        id: String,
        inscription: String,
        latitude: Double,
        longitude: Double,
        photos: List<Pair<Int, Uri>>,
        photoTypes: List<String>,
    ): Result<PendingBenchUpload> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "pending_replace_$id")
        try {
            if (get(id) == null) {
                return@withContext Result.failure(IllegalArgumentException("Pending bench not found."))
            }
            val dir = directoryFor(id)
            dir.mkdirs()
            stagingDir.mkdirs()
            stagingDir.listFiles()?.forEach { it.delete() }

            val resolver = context.contentResolver
            // Read photos before clearing the pending folder — edit mode URIs often point at those files.
            photos.forEach { (index, uri) ->
                val staged = File(stagingDir, "photo_$index.jpg")
                if (!copyUriToFile(resolver, uri, staged, directoryFor(id))) {
                    return@withContext Result.failure(
                        IllegalStateException("Could not read photo ${index + 1} for saving.")
                    )
                }
            }

            dir.listFiles()?.forEach { it.delete() }

            val fileNames = mutableListOf<String>()
            val types = mutableListOf<String>()
            photos.forEach { (index, _) ->
                val fileName = "photo_$index.jpg"
                val dest = File(dir, fileName)
                File(stagingDir, fileName).copyTo(dest, overwrite = true)
                fileNames.add(fileName)
                types.add(photoTypes.getOrElse(index) { "Long shot of the bench" })
            }

            val pending = PendingBenchUpload(
                id = id,
                inscription = inscription,
                latitude = latitude,
                longitude = longitude,
                photoFileNames = fileNames,
                photoTypes = types,
                savedAtMs = System.currentTimeMillis(),
            )

            writeIndex(list().map { if (it.id == id) pending else it })
            Result.success(pending)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun copyUriToFile(
        resolver: ContentResolver,
        uri: Uri,
        dest: File,
        pendingDir: File,
    ): Boolean {
        resolvePendingPhotoFile(uri, pendingDir)?.takeIf { it.exists() }?.let { source ->
            return try {
                source.copyTo(dest, overwrite = true)
                true
            } catch (_: Exception) {
                false
            }
        }
        return try {
            resolver.openImageInputStreamPreservingMetadata(uri)?.use { input ->
                FileOutputStream(dest).use { out ->
                    input.copyTo(out)
                }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    /** FileProvider URIs for saved pending photos map back to files under [pendingDir]. */
    private fun resolvePendingPhotoFile(uri: Uri, pendingDir: File): File? {
        val name = uri.lastPathSegment ?: return null
        val candidate = File(pendingDir, name)
        if (candidate.exists()) return candidate
        // content://…/pending_uploads/{id}/photo_0.jpg — segment may be id/photo_0.jpg on some providers
        val path = uri.path ?: return null
        val marker = "pending_bench_uploads/"
        val idx = path.indexOf(marker)
        if (idx >= 0) {
            val relative = path.substring(idx + marker.length)
            val file = File(rootDir, relative)
            if (file.exists()) return file
        }
        return null
    }

    private fun writeIndex(entries: List<PendingBenchUpload>) {
        indexFile.writeText(gson.toJson(entries))
    }
}

suspend fun uploadPendingBench(
    context: Context,
    pending: PendingBenchUpload,
    authToken: String?,
    onUploadProgress: ((Float) -> Unit)? = null,
): UploadBenchResult {
    val store = PendingBenchUploadStore(context)
    val photoFiles = store.getPhotoFiles(pending)
    if (photoFiles.isEmpty()) {
        store.delete(pending.id)
        return UploadBenchResult.Failure("Saved photos are missing.")
    }
    val result = uploadBench(
        inscription = pending.inscription,
        latitude = pending.latitude,
        longitude = pending.longitude,
        photoFiles = photoFiles,
        photoTypes = pending.photoTypes,
        authToken = authToken,
        onUploadProgress = onUploadProgress,
    )
    if (result is UploadBenchResult.Success) {
        store.delete(pending.id)
    }
    return result
}

private fun ContentResolver.openImageInputStreamPreservingMetadata(uri: Uri): InputStream? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val requireOriginal = MediaStore.setRequireOriginal(uri)
        try {
            openInputStream(requireOriginal)?.let { return it }
        } catch (_: SecurityException) {
        } catch (_: UnsupportedOperationException) {
        } catch (_: Exception) {
        }
    }
    return openInputStream(uri)
}
