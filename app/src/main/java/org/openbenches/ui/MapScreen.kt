package org.openbenches.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.openbenches.data.Bench
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import android.graphics.BitmapFactory
import androidx.core.content.res.ResourcesCompat
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.CustomZoomButtonsController
import androidx.compose.runtime.LaunchedEffect
import org.osmdroid.util.BoundingBox
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Enum for selecting map provider
 */
enum class MapProvider {
    OSM
}

/**
 * MapScreen composable displays benches from OpenBenches API on OpenStreetMap (osmdroid),
 * centered on the user's current location if available.
 * @param mapProvider Which map provider to use (OSM)
 */
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    recenterOnUser: Boolean = false,
    onRecenterHandled: () -> Unit = {},
    onCenterChanged: (GeoPoint) -> Unit = {},
    onSearchThisArea: ((GeoPoint) -> Unit)? = null,
    benches: List<Bench>,
    loading: Boolean,
    error: String?,
    mapCenter: GeoPoint,
    setMapCenter: (GeoPoint) -> Unit,
    mapProvider: MapProvider = MapProvider.OSM,
    onBenchPopupClick: (benchId: Int) -> Unit = {},
    zoomToFitBenches: Boolean = false,
    onZoomToFitHandled: () -> Unit = {},
    shouldAnimateToMapCenter: Boolean = false,
    onMapCenterAnimated: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var lastRecenter by remember { mutableStateOf(false) }
    var initialZoomSet by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(15.0) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var lastBenchesHash by remember { mutableStateOf(0) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }

    // Only center on user location once after map loads and user location is available
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var mapLoaded by remember { mutableStateOf(false) }
    var lastAnimatedMapCenter by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(context) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val fusedLocationClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLocation = GeoPoint(location.latitude, location.longitude)
                }
            }
        }
    }

    val markerBitmap: Bitmap? = remember {
        val resId = context.resources.getIdentifier("marker", "drawable", context.packageName)
        if (resId != 0) {
            BitmapFactory.decodeResource(context.resources, resId)?.let {
                Bitmap.createScaledBitmap(it, 196, 128, true)
            }
        } else null
    }

    LaunchedEffect(zoomToFitBenches, benches, mapProvider) {
        if (zoomToFitBenches && benches.isNotEmpty()) {
            val lats = benches.map { it.lat }
            val lngs = benches.map { it.lng }
            val north = lats.maxOrNull() ?: 0.0
            val south = lats.minOrNull() ?: 0.0
            val east = lngs.maxOrNull() ?: 0.0
            val west = lngs.minOrNull() ?: 0.0
            val bbox = BoundingBox(north, east, south, west)
            mapViewRef?.post {
                mapViewRef?.zoomToBoundingBox(bbox, true)
            }
            onZoomToFitHandled()
        }
    }

    Box(modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (error != null) {
            Text("Error: $error", Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // Configure osmdroid for better tile loading performance
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    
                    // Set tile cache size for better performance
                    Configuration.getInstance().tileFileSystemCacheMaxBytes = 50L * 1024L * 1024L // 50MB cache
                    Configuration.getInstance().tileDownloadThreads = 8 // More download threads
                    Configuration.getInstance().tileDownloadMaxQueueSize = 100 // Larger download queue
                    
                    val mapView = MapView(ctx)
                    mapViewRef = mapView
                    
                    // Set tile source to OpenStreetMap
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                    
                    // Configure map for smooth scrolling and preloading
                    mapView.setMultiTouchControls(true)
                    mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    
                    // Enable tile preloading for smoother scrolling
                    mapView.isTilesScaledToDpi = true
                    mapView.isHorizontalMapRepetitionEnabled = true
                    mapView.isVerticalMapRepetitionEnabled = true
                    
                    // Set scroll rate for smoother movement (if available)
                    try {
                        mapView.javaClass.getMethod("setScrollRate", Float::class.java).invoke(mapView, 0.5f)
                    } catch (e: Exception) {
                        // Method not available, ignore
                    }
                    
                    // Enable hardware acceleration for smoother rendering
                    mapView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // Set tile overlay options for better performance
                    mapView.overlayManager.tilesOverlay?.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
                    mapView.overlayManager.tilesOverlay?.setLoadingLineColor(android.graphics.Color.TRANSPARENT)
                    val center = mapCenter
                    if (!initialZoomSet) {
                        mapView.controller.setZoom(zoomLevel)
                        initialZoomSet = true
                    }
                    mapView.controller.setCenter(center)
                    
                    // Initial tile preloading after map is set up
                    mapView.postDelayed({
                        // Define preload function here since it's not available yet
                        try {
                            val cacheManager = CacheManager(mapView)
                            val boundingBox = mapView.boundingBox
                            val zoomLevel = mapView.zoomLevelDouble.toInt()
                            
                            val latSpan = boundingBox.latNorth - boundingBox.latSouth
                            val lonSpan = boundingBox.lonEast - boundingBox.lonWest
                            
                            val expandedBoundingBox = org.osmdroid.util.BoundingBox(
                                boundingBox.latNorth + latSpan,
                                boundingBox.lonEast + lonSpan,
                                boundingBox.latSouth - latSpan,
                                boundingBox.lonWest - lonSpan
                            )
                            
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    cacheManager.downloadAreaAsync(
                                        ctx,
                                        expandedBoundingBox,
                                        zoomLevel,
                                        zoomLevel,
                                        object : CacheManager.CacheManagerCallback {
                                            override fun onTaskComplete() {}
                                            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                                            override fun downloadStarted() {}
                                            override fun setPossibleTilesInArea(total: Int) {}
                                            override fun onTaskFailed(errors: Int) {}
                                        }
                                    )
                                } catch (e: Exception) {
                                    // Ignore preloading errors
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore preloading errors
                        }
                    }, 1000) // Wait 1 second after initial setup
                    
                    // Create user marker if location is available
                    if (userLocation != null) {
                        val userMarker = Marker(mapView)
                        userMarker.position = userLocation
                        userMarker.title = "You are here"
                        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mapView.overlays.add(userMarker)
                    }
                    
                    // Create bench markers
                    val markerBitmapRaw = BitmapFactory.decodeResource(ctx.resources, ctx.resources.getIdentifier("marker", "drawable", ctx.packageName))
                    val markerBitmap = Bitmap.createScaledBitmap(markerBitmapRaw, 196, 128, true)
                    val markerDrawable = BitmapDrawable(ctx.resources, markerBitmap)
                    benches.forEach { bench ->
                        val marker = Marker(mapView)
                        marker.position = GeoPoint(bench.lat, bench.lng)
                        marker.title = "Bench #${bench.id}"
                        marker.subDescription = bench.popupContent
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.icon = markerDrawable
                        marker.setOnMarkerClickListener { m, _ ->
                            if (!m.isInfoWindowShown) {
                                m.showInfoWindow()
                            } else {
                                onBenchPopupClick(bench.id)
                            }
                            true
                        }
                        mapView.overlays.add(marker)
                    }
                    
                    // Function to preload tiles around the current view
                    fun preloadTilesAroundCenter() {
                        try {
                            val cacheManager = CacheManager(mapView)
                            val boundingBox = mapView.boundingBox
                            val zoomLevel = mapView.zoomLevelDouble.toInt()
                            
                            // Calculate the size of the current view
                            val latSpan = boundingBox.latNorth - boundingBox.latSouth
                            val lonSpan = boundingBox.lonEast - boundingBox.lonWest
                            
                            // Preload tiles in a larger area around the current view (2x the current view size)
                            val expandedBoundingBox = org.osmdroid.util.BoundingBox(
                                boundingBox.latNorth + latSpan, // Expand north by view height
                                boundingBox.lonEast + lonSpan,  // Expand east by view width
                                boundingBox.latSouth - latSpan, // Expand south by view height
                                boundingBox.lonWest - lonSpan   // Expand west by view width
                            )
                            
                            // Start preloading in background
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    cacheManager.downloadAreaAsync(
                                        ctx,
                                        expandedBoundingBox,
                                        zoomLevel,
                                        zoomLevel,
                                        object : CacheManager.CacheManagerCallback {
                                            override fun onTaskComplete() {
                                                // Preloading completed
                                            }
                                            
                                            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                                                // Progress update
                                            }
                                            
                                            override fun downloadStarted() {
                                                // Download started
                                            }
                                            
                                            override fun setPossibleTilesInArea(total: Int) {
                                                // Set total tiles to download
                                            }
                                            
                                            override fun onTaskFailed(errors: Int) {
                                                // Task failed
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    // Ignore preloading errors
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore preloading errors
                        }
                    }
                    
                    // Function to preload tiles in specific directions based on scroll direction
                    var lastScrollTime = 0L
                    var scrollDirection = 0 // 0 = none, 1 = north, 2 = south, 3 = east, 4 = west
                    
                    fun preloadTilesInDirection() {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastScrollTime < 100) return // Throttle preloading
                        
                        try {
                            val cacheManager = CacheManager(mapView)
                            val boundingBox = mapView.boundingBox
                            val zoomLevel = mapView.zoomLevelDouble.toInt()
                            
                            val latSpan = boundingBox.latNorth - boundingBox.latSouth
                            val lonSpan = boundingBox.lonEast - boundingBox.lonWest
                            
                            // Preload tiles in the direction of movement
                            val directionBoundingBox = when (scrollDirection) {
                                1 -> org.osmdroid.util.BoundingBox( // North
                                    boundingBox.latNorth + latSpan,
                                    boundingBox.lonEast,
                                    boundingBox.latNorth,
                                    boundingBox.lonWest
                                )
                                2 -> org.osmdroid.util.BoundingBox( // South
                                    boundingBox.latSouth,
                                    boundingBox.lonEast,
                                    boundingBox.latSouth - latSpan,
                                    boundingBox.lonWest
                                )
                                3 -> org.osmdroid.util.BoundingBox( // East
                                    boundingBox.latNorth,
                                    boundingBox.lonEast + lonSpan,
                                    boundingBox.latSouth,
                                    boundingBox.lonEast
                                )
                                4 -> org.osmdroid.util.BoundingBox( // West
                                    boundingBox.latNorth,
                                    boundingBox.lonWest,
                                    boundingBox.latSouth,
                                    boundingBox.lonWest - lonSpan
                                )
                                else -> return
                            }
                            
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    cacheManager.downloadAreaAsync(
                                        ctx,
                                        directionBoundingBox,
                                        zoomLevel,
                                        zoomLevel,
                                        object : CacheManager.CacheManagerCallback {
                                            override fun onTaskComplete() {}
                                            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                                            override fun downloadStarted() {}
                                            override fun setPossibleTilesInArea(total: Int) {}
                                            override fun onTaskFailed(errors: Int) {}
                                        }
                                    )
                                } catch (e: Exception) {
                                    // Ignore preloading errors
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore preloading errors
                        }
                    }
                    
                    // Use the newer MapListener API instead of deprecated setMapListener
                    mapView.addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            setMapCenter(mapView.mapCenter as GeoPoint)
                            onCenterChanged(mapView.mapCenter as GeoPoint)
                            
                            // Update scroll direction and time
                            lastScrollTime = System.currentTimeMillis()
                            
                            // Determine scroll direction based on event
                            event?.let { scrollEvent ->
                                // This is a simplified direction detection
                                // In a real implementation, you might want to track previous position
                                scrollDirection = 1 // Default to north for now
                            }
                            
                            // Preload tiles in the direction of movement
                            preloadTilesInDirection()
                            
                            // Also preload tiles around center when scrolling stops
                            mapView.postDelayed({
                                // Inline preload function call
                                try {
                                    val cacheManager = CacheManager(mapView)
                                    val boundingBox = mapView.boundingBox
                                    val zoomLevel = mapView.zoomLevelDouble.toInt()
                                    
                                    val latSpan = boundingBox.latNorth - boundingBox.latSouth
                                    val lonSpan = boundingBox.lonEast - boundingBox.lonWest
                                    
                                    val expandedBoundingBox = org.osmdroid.util.BoundingBox(
                                        boundingBox.latNorth + latSpan,
                                        boundingBox.lonEast + lonSpan,
                                        boundingBox.latSouth - latSpan,
                                        boundingBox.lonWest - lonSpan
                                    )
                                    
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            cacheManager.downloadAreaAsync(
                                                ctx,
                                                expandedBoundingBox,
                                                zoomLevel,
                                                zoomLevel,
                                                object : CacheManager.CacheManagerCallback {
                                                    override fun onTaskComplete() {}
                                                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                                                    override fun downloadStarted() {}
                                                    override fun setPossibleTilesInArea(total: Int) {}
                                                    override fun onTaskFailed(errors: Int) {}
                                                }
                                            )
                                        } catch (e: Exception) {
                                            // Ignore preloading errors
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore preloading errors
                                }
                            }, 500) // Wait 500ms after scroll stops
                            
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            zoomLevel = mapView.zoomLevelDouble
                            setMapCenter(mapView.mapCenter as GeoPoint)
                            onCenterChanged(mapView.mapCenter as GeoPoint)
                            
                            // Preload tiles after zoom
                            mapView.postDelayed({
                                // Inline preload function call
                                try {
                                    val cacheManager = CacheManager(mapView)
                                    val boundingBox = mapView.boundingBox
                                    val zoomLevel = mapView.zoomLevelDouble.toInt()
                                    
                                    val latSpan = boundingBox.latNorth - boundingBox.latSouth
                                    val lonSpan = boundingBox.lonEast - boundingBox.lonWest
                                    
                                    val expandedBoundingBox = org.osmdroid.util.BoundingBox(
                                        boundingBox.latNorth + latSpan,
                                        boundingBox.lonEast + lonSpan,
                                        boundingBox.latSouth - latSpan,
                                        boundingBox.lonWest - lonSpan
                                    )
                                    
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            cacheManager.downloadAreaAsync(
                                                ctx,
                                                expandedBoundingBox,
                                                zoomLevel,
                                                zoomLevel,
                                                object : CacheManager.CacheManagerCallback {
                                                    override fun onTaskComplete() {}
                                                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                                                    override fun downloadStarted() {}
                                                    override fun setPossibleTilesInArea(total: Int) {}
                                                    override fun onTaskFailed(errors: Int) {}
                                                }
                                            )
                                        } catch (e: Exception) {
                                            // Ignore preloading errors
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore preloading errors
                                }
                            }, 300) // Wait 300ms after zoom
                            
                            return true
                        }
                    })
                    mapView
                },
                update = { mapView ->
                    mapViewRef = mapView
                    
                    // Calculate hash of current benches to detect changes
                    val currentBenchesHash = benches.hashCode()
                    
                    // Only update markers if bench data has changed
                    if (currentBenchesHash != lastBenchesHash) {
                        // Remove only bench markers, keep user marker
                        val overlaysToRemove = mapView.overlays.filter { overlay ->
                            overlay is Marker && overlay != userMarker
                        }
                        mapView.overlays.removeAll(overlaysToRemove)
                        
                        // Add new bench markers
                        val markerBitmapRaw = BitmapFactory.decodeResource(mapView.context.resources, mapView.context.resources.getIdentifier("marker", "drawable", mapView.context.packageName))
                        val markerBitmap = Bitmap.createScaledBitmap(markerBitmapRaw, 196, 128, true)
                        val markerDrawable = BitmapDrawable(mapView.context.resources, markerBitmap)
                        benches.forEach { bench ->
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(bench.lat, bench.lng)
                            marker.title = "Bench #${bench.id}"
                            marker.subDescription = bench.popupContent
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.icon = markerDrawable
                            marker.setOnMarkerClickListener { m, _ ->
                                if (!m.isInfoWindowShown) {
                                    m.showInfoWindow()
                                } else {
                                    onBenchPopupClick(bench.id)
                                }
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                        lastBenchesHash = currentBenchesHash
                    }
                    
                    // Handle user location marker updates
                    if (userLocation != null) {
                        if (userMarker == null) {
                            // Create user marker if it doesn't exist
                            userMarker = Marker(mapView)
                            userMarker!!.position = userLocation
                            userMarker!!.title = "You are here"
                            userMarker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(userMarker!!)
                        } else {
                            // Update existing user marker position
                            userMarker!!.position = userLocation
                        }
                        
                        if (recenterOnUser && !lastRecenter) {
                            mapView.controller.setCenter(userLocation)
                            mapView.controller.setZoom(15.0)
                            onRecenterHandled()
                            lastRecenter = true
                        } else if (!recenterOnUser) {
                            lastRecenter = false
                            mapView.controller.setZoom(zoomLevel)
                            mapView.controller.setCenter(mapCenter)
                        }
                    } else {
                        // Remove user marker if location is not available
                        userMarker?.let { marker ->
                            mapView.overlays.remove(marker)
                            userMarker = null
                        }
                        mapView.controller.setZoom(zoomLevel)
                        mapView.controller.setCenter(mapCenter)
                    }
                    
                    mapView.invalidate()
                }
            )
        }
    }
} 