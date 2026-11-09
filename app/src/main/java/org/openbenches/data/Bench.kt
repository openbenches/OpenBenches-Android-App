package org.openbenches.data

import android.util.Log
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UPLOAD_LOG_TAG = "OpenBenchesUpload"

private fun stripHtmlToPlainText(html: String): String =
    html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

private fun logLong(message: String) {
    if (message.isEmpty()) return
    val chunkSize = 3500
    var start = 0
    while (start < message.length) {
        val end = (start + chunkSize).coerceAtMost(message.length)
        Log.e(UPLOAD_LOG_TAG, message.substring(start, end))
        start = end
    }
}

/** Logs the full HTTP failure (headers + body) under Logcat tag [UPLOAD_LOG_TAG]. */
private fun logHttpFailure(
    operation: String,
    response: retrofit2.Response<*>,
    bodyAlreadyRead: String?,
): String {
    val errorText = response.errorBody()?.string()?.trim().orEmpty()
        .ifEmpty { bodyAlreadyRead?.trim().orEmpty() }
    val headers = buildString {
        response.headers().forEach { (name, value) ->
            append(name).append(": ").append(value).append('\n')
        }
    }
    Log.e(
        UPLOAD_LOG_TAG,
        "$operation failed HTTP ${response.code()} ${response.message()} — full response below (filter Logcat by $UPLOAD_LOG_TAG)",
    )
    if (headers.isNotBlank()) {
        logLong("$operation response headers:\n$headers")
    }
    if (errorText.isNotEmpty()) {
        logLong("$operation response body (${errorText.length} chars):\n$errorText")
    } else {
        Log.e(UPLOAD_LOG_TAG, "$operation response body: <empty>")
    }
    return errorText
}

/** Detects Auth0 HTML error pages (e.g. missing browser session after a login redirect). */
private fun isAuth0SessionFirewallError(body: String): Boolean {
    val m = body.lowercase()
    return m.contains("cdn.auth0.com") ||
        m.contains("invalid_request") ||
        m.contains("couldn't find your session") ||
        m.contains("couldn&#39;t find your session") ||
        (m.contains("auth0") && m.contains("tenant-error"))
}

/**
 * Detects CDN/WAF bot-challenge HTML (Cloudflare-style "One moment, please…" pages).
 * OkHttp cannot complete these JS challenges, so the app must not dump the raw HTML to users.
 */
private fun isBotChallengePage(body: String): Boolean {
    if (body.isBlank()) return false
    val m = body.lowercase()
    val challengePhrases = listOf(
        "one moment, please",
        "please wait while your request is being verified",
        "checking your browser",
        "just a moment",
        "attention required",
        "cf-browser-verification",
        "challenge-platform",
        "cdn-cgi/challenge",
        "cf-challenge",
        "enable javascript and cookies to continue",
    )
    if (challengePhrases.any { m.contains(it) }) return true
    // Obfuscated challenge scripts often reload after a short delay.
    return m.contains("window.location.reload") &&
        (m.contains("settimeout") || m.contains("spinner") || m.contains("outer-container"))
}

private fun botChallengeFailureMessage(operation: String, code: Int? = null): String {
    val codePart = code?.let { " ($it)" }.orEmpty()
    return "$operation failed$codePart: blocked by the server's security check. " +
        "Please wait a moment and try again. If it keeps happening, try another network " +
        "(Wi‑Fi or mobile data), or try later."
}

private fun failureMessageForHttpBody(operation: String, code: Int, errorText: String): String {
    if (isBotChallengePage(errorText)) {
        Log.w(UPLOAD_LOG_TAG, "$operation got bot-challenge HTML (HTTP $code); suppressing raw body in UI")
        return botChallengeFailureMessage(operation, code)
    }
    if (isAuth0SessionFirewallError(errorText)) {
        return "$operation failed ($code): Auth0 login/session error. " +
            "Try logging out and back in — see Logcat tag $UPLOAD_LOG_TAG."
    }
    return if (errorText.isNotEmpty()) {
        val plain = stripHtmlToPlainText(errorText)
        // Huge HTML leftovers (after tag strip) are almost never useful to show.
        if (plain.length > 280 || plain.contains("function()") || plain.contains("{var ")) {
            return "$operation failed ($code): unexpected server response. " +
                "Please try again — see Logcat tag $UPLOAD_LOG_TAG."
        }
        "$operation failed ($code): ${plain.ifBlank { errorText }} — see Logcat tag $UPLOAD_LOG_TAG"
    } else {
        "$operation failed ($code) — see Logcat tag $UPLOAD_LOG_TAG"
    }
}

/** Maps a non-bench-ID "success" body to a user-facing failure (challenge pages, HTML, etc.). */
private fun unexpectedSuccessBodyFailure(operation: String, bodyText: String): UploadBenchResult.Failure {
    if (isBotChallengePage(bodyText)) {
        Log.w(UPLOAD_LOG_TAG, "$operation got bot-challenge HTML on HTTP success; suppressing raw body in UI")
        return UploadBenchResult.Failure(botChallengeFailureMessage(operation))
    }
    val plain = stripHtmlToPlainText(bodyText)
    if (plain.length > 280 || plain.contains("function()") || plain.contains("{var ")) {
        return UploadBenchResult.Failure(
            "$operation returned an unexpected server page. Please try again later.",
        )
    }
    return UploadBenchResult.Failure(
        plain.ifBlank {
            "$operation returned unexpected response (not bench ID). Check Logcat tag $UPLOAD_LOG_TAG."
        },
    )
}

private fun mediaTypeToShortName(input: String?): String {
    return when (input?.trim()?.lowercase()) {
        "close-up of the inscription", "inscription", "plaque", "inscription/plaque" -> "inscription"
        "long shot of the bench", "bench", "the bench" -> "bench"
        "view from the bench", "view" -> "view"
        "panoramic view", "vr 360 panoramic", "pano" -> "pano"
        "video of the bench", "video" -> "video"
        else -> "bench"
    }
}

/**
 * Data class representing a bench with only id, coordinates, and popupContent.
 */
data class Bench(
    val id: Int,
    val coordinates: List<Double>,
    val popupContent: String
) {
    val lat: Double get() = coordinates[1]
    val lng: Double get() = coordinates[0]
}

/**
 * Data class for media in bench details
 */
data class BenchMedia(
    @SerializedName("URL") val url: String,
    @SerializedName("mediaID") val mediaId: Int,
    @SerializedName("licence") val licence: String?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("sha1") val sha1: String?,
    @SerializedName("user") val user: Int?,
    @SerializedName("userprovider") val userProvider: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

/**
 * Data class for bench details properties
 */
data class BenchDetailsProperties(
    @SerializedName("popupContent") val popupContent: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("media") val media: List<BenchMedia>?
)

/**
 * Data class for bench details geometry
 */
data class BenchDetailsGeometry(
    @SerializedName("type") val type: String?,
    @SerializedName("coordinates") val coordinates: List<Double>?
)

/**
 * Data class for a single bench feature in details
 */
data class BenchDetailsFeature(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val type: String?,
    @SerializedName("geometry") val geometry: BenchDetailsGeometry?,
    @SerializedName("properties") val properties: BenchDetailsProperties?
)

/**
 * Data class for the bench details API response
 */
data class BenchDetailsResponse(
    @SerializedName("type") val type: String?,
    @SerializedName("features") val features: List<BenchDetailsFeature>?
)

/**
 * Retrofit service for OpenBenches API.
 */
interface OpenBenchesService {
    @Headers("Accept: application/json")
    @GET("nearest/")
    suspend fun getBenchesNearby(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distance: Int = 10,
        @Query("limit") limit: Int = 1000
    ): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("benches")
    suspend fun getLatestBenches(@Query("latest") latest: Int = 5): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("bench/{id}")
    suspend fun getBenchDetails(
        @Path("id") id: Int,
        @Query("truncated") truncated: Boolean = false,
        @Query("media") media: Boolean = true,
    ): BenchDetailsResponse

    @Headers("Accept: application/json")
    @GET("search")
    suspend fun searchBenches(@Query("search") search: String, @Query("limit") limit: Int = 1000): BenchesRawResponse

    /** Benches contributed by a user; see [https://openbenches.org/api/user/{id}](https://openbenches.org/api/user/). */
    @Headers("Accept: application/json")
    @GET("user/{userId}")
    suspend fun getBenchesForUser(
        @Path("userId") userId: Int,
        @Query("limit") limit: Int = 20000
    ): BenchesRawResponse

    @POST("upload")
    suspend fun uploadBenchBody(
        @Body body: RequestBody,
    ): retrofit2.Response<ResponseBody>

    @POST("update")
    suspend fun updateBenchBody(
        @Body body: RequestBody,
    ): retrofit2.Response<ResponseBody>
}

data class BenchesRawResponse(
    @SerializedName("features") val features: List<BenchRaw>
)

data class BenchRaw(
    @SerializedName("id") val id: Int,
    @SerializedName("geometry") val geometry: Geometry,
    @SerializedName("properties") val properties: Properties
)

data class Geometry(
    @SerializedName("coordinates") val coordinates: List<Double>
)

data class Properties(
    @SerializedName("popupContent") val popupContent: String,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class UserBenchDateRange(
    val oldestCreatedAt: String?,
    val newestCreatedAt: String?,
)

/**
 * Total number of benches from [https://openbenches.org/api/benches](https://openbenches.org/api/benches),
 * counted by streaming the GeoJSON `features` array (avoids loading the full payload into memory as objects).
 */
suspend fun fetchGlobalBenchCount(): Int = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    val request = okhttp3.Request.Builder()
        .url("https://openbenches.org/api/benches")
        .header("Accept", "application/json")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} loading bench count")
        }
        val body = response.body ?: throw IllegalStateException("Empty response loading bench count")
        body.byteStream().use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "features" -> {
                            reader.beginArray()
                            var count = 0
                            while (reader.hasNext()) {
                                reader.skipValue()
                                count++
                            }
                            reader.endArray()
                            return@withContext count
                        }
                        else -> reader.skipValue()
                    }
                }
                throw IllegalStateException("No features array in bench count response")
            }
        }
    }
}

/**
 * Streams all benches from the global API into lightweight [Bench] objects.
 * Uses a streaming JSON parser to avoid holding the raw response string in memory.
 */
suspend fun fetchGlobalBenchesStreaming(): List<Bench> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()
    val request = okhttp3.Request.Builder()
        .url("https://openbenches.org/api/benches")
        .header("Accept", "application/json")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} loading global benches")
        }
        val body = response.body ?: throw IllegalStateException("Empty response loading global benches")
        val benches = mutableListOf<Bench>()
        body.byteStream().use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "features" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                parseBenchFeature(reader)?.let { benches.add(it) }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
            }
        }
        benches
    }
}

private fun parseBenchFeature(reader: JsonReader): Bench? {
    var id = -1
    var lng = 0.0
    var lat = 0.0
    var popupHtml = ""
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "id" -> id = reader.nextInt()
            "geometry" -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "coordinates" -> {
                            reader.beginArray()
                            if (reader.hasNext()) lng = reader.nextDouble()
                            if (reader.hasNext()) lat = reader.nextDouble()
                            while (reader.hasNext()) reader.skipValue()
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            "properties" -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "popupContent" -> popupHtml = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    if (id < 0) return null
    return Bench(
        id = id,
        coordinates = listOf(lng, lat),
        popupContent = stripHtmlToPlainText(popupHtml),
    )
}

/**
 * Refreshes global benches from the network and updates the on-device cache.
 */
suspend fun refreshGlobalBenchesCache(context: android.content.Context): List<Bench> {
    val downloaded = fetchGlobalBenchesStreaming()
    if (downloaded.isNotEmpty()) {
        GlobalBenchCache(context).save(downloaded)
    }
    return downloaded
}

/**
 * Fetches benches near a given location from the OpenBenches API.
 * @return List of benches
 */
suspend fun fetchBenchesNearby(latitude: Double, longitude: Double, distance: Int = 10, limit: Int = 2000): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.getBenchesNearby(latitude, longitude, distance, limit).features.map {
        Bench(
            id = it.id,
            coordinates = it.geometry.coordinates,
            popupContent = it.properties.popupContent.replace(Regex("<[^>]+>"), "")
        )
    }
}

/**
 * Fetches details for a single bench by ID from the OpenBenches API.
 * @return BenchDetailsResponse for the bench
 */
suspend fun fetchBenchDetails(id: Int): BenchDetailsResponse {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.getBenchDetails(id, truncated = false, media = true)
}

/**
 * Fetches benches by search query from the OpenBenches API.
 * @return List of benches
 */
suspend fun fetchBenchesBySearch(query: String, limit: Int = 1000): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.searchBenches(query, limit).features.map {
        Bench(
            id = it.id,
            coordinates = it.geometry.coordinates,
            popupContent = it.properties.popupContent.replace(Regex("<[^>]+>"), "")
        )
    }
}

/**
 * Fetches the most recently added benches from `https://openbenches.org/api/benches?latest=`.
 */
suspend fun fetchLatestBenches(latest: Int = 5): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.getLatestBenches(latest).features.map {
        Bench(
            id = it.id,
            coordinates = it.geometry.coordinates,
            popupContent = it.properties.popupContent.replace(Regex("<[^>]+>"), "")
        )
    }
}

/**
 * Fetches benches for a contributor from `https://openbenches.org/api/user/{userId}`.
 */
suspend fun fetchBenchesForUser(userId: Int, limit: Int = 20000): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.getBenchesForUser(userId, limit).features.map {
        Bench(
            id = it.id,
            coordinates = it.geometry.coordinates,
            popupContent = it.properties.popupContent.replace(Regex("<[^>]+>"), "")
        )
    }
}

/**
 * Oldest and newest `created_at` from [https://openbenches.org/api/user/{userId}](https://openbenches.org/api/user/).
 */
suspend fun fetchUserBenchDateRange(userId: Int, limit: Int = 20000): UserBenchDateRange {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    val dates = service.getBenchesForUser(userId, limit).features
        .mapNotNull { it.properties.createdAt?.takeIf { d -> d.isNotBlank() } }
    if (dates.isEmpty()) {
        return UserBenchDateRange(null, null)
    }
    val sorted = dates.sorted()
    return UserBenchDateRange(
        oldestCreatedAt = sorted.first(),
        newestCreatedAt = sorted.last(),
    )
}

sealed class UploadBenchResult {
    data class Success(val benchId: String) : UploadBenchResult()
    data class Failure(val message: String) : UploadBenchResult()
}

/**
 * Heuristic: server/HTML error text when the first image has no usable GPS/EXIF location.
 */
fun isGpsExifRelatedUploadFailure(message: String): Boolean {
    val m = message.lowercase()
    // OpenBenches returns HTTP 200 + HTML: "No location metadata found in image."
    if (m.contains("location metadata")) return true
    if (m.contains("no location") && m.contains("metadata")) return true
    if (m.contains("photo") && m.contains("gps")) return true
    if (m.contains("image") && m.contains("gps")) return true
    if (m.contains("geotag")) return true
    if (m.contains("gps data")) return true
    if (m.contains("exif") && (m.contains("gps") || m.contains("location") || m.contains("latitude") || m.contains("longitude"))) {
        return true
    }
    if (m.contains("gps")) {
        if (m.contains("exif") || m.contains("missing") || m.contains("no ") || m.contains("not ") ||
            m.contains("without") || m.contains("present")
        ) {
            return true
        }
    }
    return false
}

/**
 * Uploads a bench to OpenBenches.org
 * @param inscription The inscription text
 * @param latitude Latitude coordinate
 * @param longitude Longitude coordinate
 * @param photoFiles List of photo files to upload (at least one required)
 * @param photoTypes List of media types corresponding to photos
 * @param tags Optional comma-separated tags
 * @return Bench ID if successful, null if failed
 */
private fun buildBenchUploadMultipartBody(
    inscription: String,
    latitude: Double,
    longitude: Double,
    photoFiles: List<File>,
    photoTypes: List<String>,
    tags: String?,
): MultipartBody {
    val imageMediaType = "image/*".toMediaType()
    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
    builder.addFormDataPart("inscription", inscription)
    builder.addFormDataPart("newLatitude", latitude.toString())
    builder.addFormDataPart("newLongitude", longitude.toString())
    tags?.let { builder.addFormDataPart("tags", it) }
    builder.addFormDataPart("media_type1", mediaTypeToShortName(photoTypes.getOrNull(0)))
    builder.addFormDataPart(
        "userfile1",
        photoFiles[0].name,
        photoFiles[0].asRequestBody(imageMediaType),
    )
    photoFiles.getOrNull(1)?.let { file ->
        builder.addFormDataPart("media_type2", mediaTypeToShortName(photoTypes.getOrNull(1)))
        builder.addFormDataPart("userfile2", file.name, file.asRequestBody(imageMediaType))
    }
    photoFiles.getOrNull(2)?.let { file ->
        builder.addFormDataPart("media_type3", mediaTypeToShortName(photoTypes.getOrNull(2)))
        builder.addFormDataPart("userfile3", file.name, file.asRequestBody(imageMediaType))
    }
    photoFiles.getOrNull(3)?.let { file ->
        builder.addFormDataPart("media_type4", mediaTypeToShortName(photoTypes.getOrNull(3)))
        builder.addFormDataPart("userfile4", file.name, file.asRequestBody(imageMediaType))
    }
    return builder.build()
}

suspend fun uploadBench(
    inscription: String,
    latitude: Double,
    longitude: Double,
    photoFiles: List<File>,
    photoTypes: List<String>,
    authToken: String? = null,
    tags: String? = null,
    onUploadProgress: ((Float) -> Unit)? = null,
): UploadBenchResult {
    if (photoFiles.isEmpty()) {
        return UploadBenchResult.Failure("No photos selected.")
    }

    val auth0Token = authToken?.trim()?.takeIf { it.isNotEmpty() }
    fun createService(headers: Map<String, String>): OpenBenchesService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                headers.forEach { (name, value) ->
                    builder.header(name, value)
                }
                val request = builder.build()
                chain.proceed(request)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://openbenches.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(OpenBenchesService::class.java)
    }

    val multipartBody = buildBenchUploadMultipartBody(
        inscription = inscription,
        latitude = latitude,
        longitude = longitude,
        photoFiles = photoFiles,
        photoTypes = photoTypes,
        tags = tags,
    )
    val requestBody: RequestBody = if (onUploadProgress != null) {
        UploadProgressRequestBody(multipartBody) { bytesWritten, contentLength ->
            val progress = if (contentLength > 0L) {
                (bytesWritten.toFloat() / contentLength).coerceIn(0f, 1f)
            } else {
                0f
            }
            onUploadProgress(progress)
        }
    } else {
        multipartBody
    }

    try {
        suspend fun requestUpload(service: OpenBenchesService) =
            service.uploadBenchBody(requestBody)

        val response = if (auth0Token != null) {
            requestUpload(
                createService(mapOf("Auth0-Authorization" to auth0Token)),
            )
        } else {
            // Anonymous upload — no auth headers required on production.
            requestUpload(createService(emptyMap()))
        }

        val bodyText = response.body()?.string()?.trim()
        if (response.isSuccessful) {
            if (!bodyText.isNullOrEmpty()) {
                if (bodyText.matches(Regex("^\\d+$"))) {
                    Log.i(UPLOAD_LOG_TAG, "Upload success. Bench ID response: $bodyText")
                    return UploadBenchResult.Success(bodyText)
                }
                logLong("Upload HTTP ${response.code()} non-numeric success body:\n$bodyText")
                return unexpectedSuccessBodyFailure("Upload", bodyText)
            }
            return UploadBenchResult.Failure("Upload succeeded but server returned an empty response body.")
        } else {
            val errorText = logHttpFailure("Upload", response, bodyText)
            if (response.code() == 401) {
                return UploadBenchResult.Failure(
                    "Upload failed, please try logging out and back in, to refresh your authentication"
                )
            }
            return UploadBenchResult.Failure(
                failureMessageForHttpBody("Upload", response.code(), errorText),
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e(UPLOAD_LOG_TAG, "Upload exception: ${e.message}", e)
        return UploadBenchResult.Failure("Network/error during upload: ${e.message ?: "unknown error"}")
    }
}

/**
 * Updates an existing bench via POST https://openbenches.org/update
 * (same form fields as EditController::update — no delete).
 * Auth uses the Auth0-Authorization header (same as [uploadBench]).
 */
private fun buildBenchUpdateMultipartBody(
    benchId: Int,
    inscription: String,
    latitude: Double,
    longitude: Double,
    existingMediaTypes: Map<Int, String>,
    photoFiles: List<File>,
    photoTypes: List<String>,
    tags: String?,
): MultipartBody {
    val imageMediaType = "image/*".toMediaType()
    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
    builder.addFormDataPart("benchID", benchId.toString())
    builder.addFormDataPart("inscription", inscription)
    builder.addFormDataPart("newLatitude", latitude.toString())
    builder.addFormDataPart("newLongitude", longitude.toString())
    builder.addFormDataPart("tags", tags.orEmpty())
    builder.addFormDataPart("delete", "false")
    existingMediaTypes.forEach { (mediaId, mediaType) ->
        builder.addFormDataPart("media_$mediaId", mediaTypeToShortName(mediaType))
    }
    photoFiles.forEachIndexed { index, file ->
        val n = index + 1
        if (n > 4) return@forEachIndexed
        builder.addFormDataPart("media_type$n", mediaTypeToShortName(photoTypes.getOrNull(index)))
        builder.addFormDataPart("userfile$n", file.name, file.asRequestBody(imageMediaType))
    }
    return builder.build()
}

suspend fun updateBench(
    benchId: Int,
    inscription: String,
    latitude: Double,
    longitude: Double,
    authToken: String,
    existingMediaTypes: Map<Int, String> = emptyMap(),
    photoFiles: List<File> = emptyList(),
    photoTypes: List<String> = emptyList(),
    tags: String? = null,
    onUploadProgress: ((Float) -> Unit)? = null,
): UploadBenchResult {
    val auth0Token = authToken.trim().takeIf { it.isNotEmpty() }
        ?: return UploadBenchResult.Failure("You must be logged in to edit a bench.")

    fun createService(headers: Map<String, String>): OpenBenchesService {
        val client = OkHttpClient.Builder()
            // Avoid following login redirects so callers see the real HTTP status.
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                headers.forEach { (name, value) ->
                    builder.header(name, value)
                }
                chain.proceed(builder.build())
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://openbenches.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(OpenBenchesService::class.java)
    }

    val multipartBody = buildBenchUpdateMultipartBody(
        benchId = benchId,
        inscription = inscription,
        latitude = latitude,
        longitude = longitude,
        existingMediaTypes = existingMediaTypes,
        photoFiles = photoFiles.take(4),
        photoTypes = photoTypes,
        tags = tags,
    )
    val requestBody: RequestBody = if (onUploadProgress != null) {
        UploadProgressRequestBody(multipartBody) { bytesWritten, contentLength ->
            val progress = if (contentLength > 0L) {
                (bytesWritten.toFloat() / contentLength).coerceIn(0f, 1f)
            } else {
                0f
            }
            onUploadProgress(progress)
        }
    } else {
        multipartBody
    }

    try {
        val response = createService(mapOf("Auth0-Authorization" to auth0Token))
            .updateBenchBody(requestBody)
        val bodyText = response.body()?.string()?.trim()
        if (response.isSuccessful) {
            if (!bodyText.isNullOrEmpty()) {
                if (bodyText.matches(Regex("^\\d+$"))) {
                    Log.i(UPLOAD_LOG_TAG, "Update success. Bench ID response: $bodyText")
                    return UploadBenchResult.Success(bodyText)
                }
                logLong("Update HTTP ${response.code()} non-numeric success body:\n$bodyText")
                return unexpectedSuccessBodyFailure("Update", bodyText)
            }
            return UploadBenchResult.Failure("Update succeeded but server returned an empty response body.")
        } else {
            val errorText = logHttpFailure("Update", response, bodyText)
            if (response.code() == 302 || response.code() == 301) {
                val location = response.headers()["Location"].orEmpty()
                Log.e(UPLOAD_LOG_TAG, "Update redirected to: $location")
                return UploadBenchResult.Failure(
                    "Update failed (${response.code()}): redirected to $location. " +
                        "Try logging out and back in — see Logcat tag $UPLOAD_LOG_TAG.",
                )
            }
            if (response.code() == 401) {
                return UploadBenchResult.Failure(
                    "Update failed, please try logging out and back in, to refresh your authentication"
                )
            }
            return UploadBenchResult.Failure(
                failureMessageForHttpBody("Update", response.code(), errorText),
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e(UPLOAD_LOG_TAG, "Update exception: ${e.message}", e)
        return UploadBenchResult.Failure("Network/error during update: ${e.message ?: "unknown error"}")
    }
}