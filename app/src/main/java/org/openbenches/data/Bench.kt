package org.openbenches.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

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
    @GET("benches")
    suspend fun getBenches(): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("nearest/")
    suspend fun getBenchesNearby(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distance: Int = 400
    ): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("bench/{id}")
    suspend fun getBenchDetails(@Path("id") id: Int, @Query("truncated") truncated: Boolean = false): BenchDetailsResponse

    @Headers("Accept: application/json")
    @GET("search")
    suspend fun searchBenches(@Query("search") search: String): BenchesRawResponse
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
    @SerializedName("popupContent") val popupContent: String
)

/**
 * Fetches benches from the OpenBenches API and maps to simplified Bench objects.
 * @return List of benches
 */
suspend fun fetchBenches(): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.getBenches().features.map {
        Bench(
            id = it.id,
            coordinates = it.geometry.coordinates,
            popupContent = it.properties.popupContent.replace(Regex("<[^>]+>"), "")
        )
    }
}

/**
 * Fetches benches near a given location from the OpenBenches API.
 * @return List of benches
 */
suspend fun fetchBenchesNearby(latitude: Double, longitude: Double, distance: Int = 200): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.getBenchesNearby(latitude, longitude, distance).features.map {
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
    return service.getBenchDetails(id, truncated = false)
}

/**
 * Fetches benches by search query from the OpenBenches API.
 * @return List of benches
 */
suspend fun fetchBenchesBySearch(query: String): List<Bench> {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://openbenches.org/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenBenchesService::class.java)
    return service.searchBenches(query).features.map {
        Bench(
            id = it.id,
            coordinates = it.geometry.coordinates,
            popupContent = it.properties.popupContent.replace(Regex("<[^>]+>"), "")
        )
    }
} 

/**
 * Data class for OpenCageData API response
 */
data class OpenCageResponse(
    val results: List<OpenCageResult>?
)

data class OpenCageResult(
    val formatted: String?
)

/**
 * Retrofit service for OpenCageData API
 */
interface OpenCageService {
    @GET("geocode/v1/json")
    suspend fun reverseGeocode(
        @Query("q") latlng: String,
        @Query("key") apiKey: String
    ): OpenCageResponse
}

/**
 * Fetches address from OpenCageData API for given latitude and longitude
 * @return formatted address or null
 */
suspend fun fetchAddressFromLatLng(latitude: Double, longitude: Double): String? {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api.opencagedata.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val service = retrofit.create(OpenCageService::class.java)
    val response = service.reverseGeocode("$latitude,$longitude", "d78d11599647436f9277f344dcc8a5ee")
    return response.results?.firstOrNull()?.formatted
} 