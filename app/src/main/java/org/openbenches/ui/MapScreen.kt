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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.openbenches.data.Bench
import org.openbenches.data.TodoBench
import org.openbenches.util.resolveUserLocation
import org.openbenches.util.userLocationUpdates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.MotionEvent
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.CustomZoomButtonsController
import androidx.compose.runtime.LaunchedEffect
import org.osmdroid.util.BoundingBox
import android.graphics.Color as AndroidColor
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import org.openbenches.R
import kotlin.math.max

private fun createBenchMarkerDrawable(context: Context): BitmapDrawable {
    val markerBitmapRaw = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.marker,
    )
    val markerBitmap = markerBitmapRaw.scale(196, 128)
    return markerBitmap.toDrawable(context.resources)
}

/** Expands a bench bounding box so markers sit inset from the map edges after zoom-to-fit. */
private fun boundingBoxAroundBenches(
    north: Double,
    south: Double,
    east: Double,
    west: Double,
    paddingFraction: Double = 0.2,
    minPaddingDegrees: Double = 0.01,
): BoundingBox {
    var latSpan = north - south
    var lonSpan = east - west
    if (latSpan < 1e-6) latSpan = minPaddingDegrees * 2
    if (lonSpan < 1e-6) lonSpan = minPaddingDegrees * 2

    val latPad = max(latSpan * paddingFraction, minPaddingDegrees)
    val lonPad = max(lonSpan * paddingFraction, minPaddingDegrees)

    return BoundingBox(
        (north + latPad).coerceAtMost(90.0),
        east + lonPad,
        (south - latPad).coerceAtLeast(-90.0),
        west - lonPad,
    )
}

/**
 * Enhanced Map Clustering Implementation
 * 
 * This implementation is based on the clustering approach used by OpenBenches.org
 * (https://github.com/openbenches/openbenches.org) and includes several improvements:
 * 
 * Key Features:
 * 1. Grid-based clustering for better performance with large datasets
 * 2. Dynamic cluster radius based on zoom level
 * 3. Enhanced visual design with color-coded clusters
 * 4. Optimized Haversine distance calculation
 * 5. Efficient marker management and updates
 * 
 * Clustering Algorithm:
 * - Uses a grid-based approach to reduce computational complexity
 * - Groups benches by geographic grid cells before applying distance-based clustering
 * - Cluster radius varies from 50km (very zoomed out) to 100m (very zoomed in)
 * - Only applies clustering when zoom level < 16.0 and bench count > 10
 * 
 * Visual Design:
 * - Color-coded clusters: Green (<10), Yellow (10-49), Orange (50-99), Red (100+)
 * - Enhanced cluster icons with shadow effects and better typography
 * - Compact display for large numbers (e.g., "1k" for 1000+ benches)
 * 
 * Performance Optimizations:
 * - Efficient hash-based change detection
 * - Selective marker removal (preserves user location marker)
 * - Grid-based clustering reduces O(n²) complexity to O(n log n)
 * - Hardware acceleration and tile preloading
 * 
 * Interactive Features:
 * - Click clusters to zoom in and reveal individual markers
 * - Tap outside markers to close popups
 * - Smooth animations when zooming to clusters
 * - Proper popup management (close others when opening new ones)
 */

/**
 * MapScreen composable displays benches from OpenBenches API on OpenStreetMap (osmdroid),
 * centered on the user's current location if available.
 * @param mapProvider Street map (OSM) or satellite (Esri World Imagery)
 */
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    recenterRequest: Int = 0,
    refreshLocation: Boolean = false,
    onRefreshLocationHandled: () -> Unit = {},
    onCenterChanged: (GeoPoint) -> Unit = {},
    benches: List<Bench>,
    loading: Boolean,
    error: String?,
    mapCenter: GeoPoint,
    setMapCenter: (GeoPoint) -> Unit,
    mapProvider: MapProvider = MapProvider.OSM,
    onBenchPopupClick: (benchId: Int) -> Unit = {},
    /** When false, any open bench marker popup is dismissed. */
    showBenchDetails: Boolean = false,
    zoomToFitBenches: Boolean = false,
    onZoomToFitHandled: () -> Unit = {},
    mapZoomLevel: Double = 11.0, //Current zoom level of the map
    onZoomLevelChanged: (Double) -> Unit = {}, //Callback for when zoom level changes
    /** Bumped to fly the map to [mapCenter] at [mapZoomLevel] (e.g. show a to-do). */
    mapFocusRequest: Int = 0,
    /** When non-empty, map markers use cached global benches + viewport clustering. */
    globalBenches: List<Bench>? = null,
    /** When non-empty, only these benches are shown (active inscription search). */
    searchResultBenches: List<Bench>? = null,
    /** Local "to do" locations (seen benches without photos yet). */
    todoBenches: List<TodoBench> = emptyList(),
    /** Fired after the user holds a finger still on the map for ~3 seconds. */
    onMapLongHold: (GeoPoint) -> Unit = {},
    /** Fired when a to-do marker is tapped. */
    onTodoClick: (TodoBench) -> Unit = {},
    /** Benches in the current viewport that are drawn on the map (sum of cluster sizes). */
    onVisibleBenchCountChanged: (Int) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Reuse one scaled marker drawable across pan/zoom rebuilds (avoids decode+scale each time).
    val benchMarkerDrawable = remember(context) { createBenchMarkerDrawable(context) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var initialZoomSet by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableDoubleStateOf(mapZoomLevel) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var lastBenchesHash by remember { mutableIntStateOf(0) }
    var lastMarkerGenerationKey by remember { mutableIntStateOf(0) }
    /** Bumped when the map viewport pans so bench markers refresh for the visible area. */
    var viewportPanKey by remember { mutableIntStateOf(0) }
    var lastProcessedPanKey by remember { mutableIntStateOf(0) }
    val scrollGenerationHolder = remember { intArrayOf(0) }
    fun benchMarkerSource(): List<Bench> = when {
        // Non-null (including empty) means an active filter — e.g. To-Do view hides benches.
        searchResultBenches != null -> searchResultBenches
        !globalBenches.isNullOrEmpty() -> globalBenches
        else -> benches
    }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var isRefreshingLocation by remember { mutableStateOf(false) }
    var lastProcessedRecenterRequest by remember { mutableIntStateOf(0) }
    var lastTodoMarkersKey by remember { mutableIntStateOf(0) }
    var lastMapFocusRequest by remember { mutableIntStateOf(0) }
    /** When true, map keeps re-centering on the user ~every 0.5s until they pan/zoom/tap. */
    var followingUserLocation by remember { mutableStateOf(false) }
    /** Ignore scroll/zoom cancel briefly after programmatic follow moves. */
    val ignoreFollowCancelUntilMs = remember { longArrayOf(0L) }

    fun stopFollowingUser() {
        followingUserLocation = false
    }

    fun markProgrammaticFollowMove() {
        ignoreFollowCancelUntilMs[0] = SystemClock.uptimeMillis() + 250L
    }

    fun shouldCancelFollowFromUserGesture(): Boolean =
        followingUserLocation && SystemClock.uptimeMillis() >= ignoreFollowCancelUntilMs[0]

    fun isTodoMarker(marker: Marker): Boolean =
        marker.relatedObject is TodoBench

    // Per-bench-id: true = first tap done (popup shown), waiting for second tap to open details.
    // Cleared when bench/cluster markers are rebuilt (createMarkers) so state never outlives real popups.
    val markerClickStates = remember { mutableStateMapOf<Int, Boolean>() }
    // One shared bubble for all bench markers — recreating per rebuild left orphan popups on the map.
    var sharedBenchInfoWindow by remember { mutableStateOf<BenchMarkerInfoWindow?>(null) }

    /**
     * True if the tap lies on any [Marker] icon (bench, cluster, or user location).
     * Used so we do not clear "pending second tap" when the user is actually tapping a marker —
     * otherwise the clear runs before the marker click listener and breaks the two-tap flow.
     */
    fun tapHitsAnyMarker(mapView: MapView, event: MotionEvent?): Boolean {
        if (event == null) return false
        for (overlay in mapView.overlays) {
            if (overlay is Marker && overlay.hitTest(event, mapView)) {
                return true
            }
        }
        return false
    }

    /**
     * Closes every info window currently attached to the map (bench, cluster, orphans).
     * Does not clear two-tap state — callers that need that should use dismissAllInfoWindows.
     */
    fun closeOpenInfoWindows(mapView: MapView) {
        InfoWindow.closeAllInfoWindowsOn(mapView)
        sharedBenchInfoWindow?.close()
        // Defensive: close by marker reference in case a window was not tagged as a MapView child.
        for (overlay in mapView.overlays) {
            if (overlay is Marker) {
                overlay.closeInfoWindow()
            }
        }
        mapView.invalidate()
    }

    /**
     * Closes every info window and resets two-tap popup state.
     * Prefer this over [Marker.isInfoWindowShown], which is unreliable with shared windows.
     */
    fun dismissAllInfoWindows(mapView: MapView) {
        closeOpenInfoWindows(mapView)
        if (markerClickStates.isNotEmpty()) {
            markerClickStates.clear()
        }
    }

    /**
     * Clears currently open bench popups and any pending second-tap state.
     * Called when user taps the map background to "unselect" a bench.
     */
    fun clearBenchSelection(mapView: MapView) {
        dismissAllInfoWindows(mapView)
    }

    fun benchInfoWindowFor(mapView: MapView): BenchMarkerInfoWindow {
        val existing = sharedBenchInfoWindow
        if (existing != null && existing.mapView === mapView) {
            existing.onBenchClick = { benchId ->
                stopFollowingUser()
                markerClickStates[benchId] = false
                closeOpenInfoWindows(mapView)
                onBenchPopupClick(benchId)
            }
            return existing
        }
        existing?.close()
        val created = BenchMarkerInfoWindow(mapView) { benchId ->
            stopFollowingUser()
            markerClickStates[benchId] = false
            closeOpenInfoWindows(mapView)
            onBenchPopupClick(benchId)
        }
        sharedBenchInfoWindow = created
        return created
    }

    /**
     * Create markers for benches and clusters with enhanced functionality
     * Uses fixed-size markers (196x128) for consistent click area at all zoom levels
     */
    fun createMarkers(
        mapView: MapView,
        clusters: List<ViewportBenchClusterer.BenchCluster>,
        markerDrawable: BitmapDrawable,
        context: Context
    ) {
        // Drop any leftover popups from a previous marker set before building a new one.
        dismissAllInfoWindows(mapView)
        val clusterZoom = ViewportBenchClusterer.quantizeZoomForClustering(zoomLevel)
        val benchInfoWindow = benchInfoWindowFor(mapView)
        clusters.forEach { cluster ->
            if (cluster.benches.size == 1) {
                // Single bench - show individual marker
                val bench = cluster.benches.first()
                val marker = Marker(mapView)
                marker.position = GeoPoint(bench.lat, bench.lng)
                marker.title = "Bench #${bench.id}"
                marker.subDescription = bench.popupContent
                marker.relatedObject = bench.id
                // OverlayWithIW setter accepts any InfoWindow (shared across benches).
                marker.infoWindow = benchInfoWindow
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                marker.icon = markerDrawable
                marker.setOnMarkerClickListener { m, _ ->
                    stopFollowingUser()
                    val hasBeenClicked = markerClickStates[bench.id] == true
                    if (!hasBeenClicked) {
                        // Always close every open bubble first — only one popup at a time.
                        closeOpenInfoWindows(mapView)
                        markerClickStates.clear()
                        markerClickStates[bench.id] = true
                        m.showInfoWindow()
                    } else {
                        // Second click - navigate to bench details
                        markerClickStates[bench.id] = false
                        closeOpenInfoWindows(mapView)
                        onBenchPopupClick(bench.id)
                    }
                    true // Consume the event to prevent map interaction
                }

                mapView.overlays.add(marker)
            } else {
                // Multiple benches - marker at unweighted midpoint of merged sub-cells
                val centerPoint = GeoPoint(cluster.centerLat, cluster.centerLng)

                val clusterMarker = Marker(mapView)
                clusterMarker.position = centerPoint
                clusterMarker.title = "${cluster.benches.size} benches"
                clusterMarker.subDescription = "Tap to zoom in and see individual benches"
                clusterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Create cluster icon with count
                val clusterIcon = createClusterIcon(cluster.benches.size, context, clusterZoom)
                clusterMarker.icon = clusterIcon

                clusterMarker.setOnMarkerClickListener { m, _ ->
                    stopFollowingUser()
                    val alreadyOpenForThis = m.isInfoWindowShown
                    // Close every other bubble (including bench popups) before opening/acting.
                    closeOpenInfoWindows(mapView)
                    markerClickStates.clear()
                    if (!alreadyOpenForThis) {
                        m.showInfoWindow()
                    } else {
                        onBenchPopupClick(cluster.benches.first().id)
                    }
                    true // Consume the event to prevent map interaction
                }

                mapView.overlays.add(clusterMarker)
            }
        }
    }

    LaunchedEffect(context) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@LaunchedEffect
        val location = withContext(Dispatchers.IO) {
            resolveUserLocation(context)
        }
        if (location != null) {
            userLocation = GeoPoint(location.latitude, location.longitude)
        }
    }

    // Handle location refresh requests (updates marker; recenter also fetches fresh GPS).
    LaunchedEffect(refreshLocation) {
        if (!refreshLocation) return@LaunchedEffect
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            onRefreshLocationHandled()
            return@LaunchedEffect
        }
        isRefreshingLocation = true
        val location = withContext(Dispatchers.IO) {
            resolveUserLocation(context)
        }
        if (location != null) {
            userLocation = GeoPoint(location.latitude, location.longitude)
        }
        isRefreshingLocation = false
        onRefreshLocationHandled()
    }

    LaunchedEffect(zoomToFitBenches, benches, globalBenches, searchResultBenches, todoBenches, mapProvider) {
        if (!zoomToFitBenches) return@LaunchedEffect
        val fitBenches = benchMarkerSource()
        val fitPoints: List<Pair<Double, Double>> = when {
            fitBenches.isNotEmpty() -> fitBenches.map { it.lat to it.lng }
            // To-Do list filter: no bench markers, fit the exclamation markers instead.
            searchResultBenches != null && todoBenches.isNotEmpty() ->
                todoBenches.map { it.latitude to it.longitude }
            else -> emptyList()
        }
        if (fitPoints.isEmpty()) {
            onZoomToFitHandled()
            return@LaunchedEffect
        }
        stopFollowingUser()
        val lats = fitPoints.map { it.first }
        val lngs = fitPoints.map { it.second }
        val north = lats.maxOrNull() ?: 0.0
        val south = lats.minOrNull() ?: 0.0
        val east = lngs.maxOrNull() ?: 0.0
        val west = lngs.minOrNull() ?: 0.0

        mapViewRef?.post {
            if (fitPoints.size == 1) {
                val (lat, lng) = fitPoints.first()
                mapViewRef?.controller?.setCenter(GeoPoint(lat, lng))
                mapViewRef?.controller?.setZoom(16.0)
            } else {
                val mapView = mapViewRef ?: return@post
                val bbox = boundingBoxAroundBenches(north, south, east, west)
                val borderPx = (72 * mapView.context.resources.displayMetrics.density).toInt()
                mapView.zoomToBoundingBox(bbox, true, borderPx)
            }
        }
        onZoomToFitHandled()
    }

    fun markerGenerationKey(mapView: MapView): Int {
        val bbox = mapView.boundingBox
        val sourceToken = benchMarkerSource().size
        val clusterZoom = ViewportBenchClusterer.quantizeZoomForClustering(zoomLevel)
        val panStep = ViewportBenchClusterer.viewportPanGridStep(clusterZoom)
        return listOf(
            sourceToken,
            (clusterZoom * 10).toInt(),
            kotlin.math.floor(bbox.centerLatitude / panStep).toInt(),
            kotlin.math.floor(bbox.centerLongitude / panStep).toInt(),
            mapView.width,
            mapView.height,
        ).hashCode()
    }

    /** Finer grid than [markerGenerationKey] so panning refreshes markers before a full cluster step. */
    fun viewportScrollKey(mapView: MapView): Int {
        val bbox = mapView.boundingBox
        val clusterZoom = ViewportBenchClusterer.quantizeZoomForClustering(zoomLevel)
        val panStep = ViewportBenchClusterer.viewportPanGridStep(clusterZoom) / 3.0
        return listOf(
            (clusterZoom * 10).toInt(),
            kotlin.math.floor(bbox.centerLatitude / panStep).toInt(),
            kotlin.math.floor(bbox.centerLongitude / panStep).toInt(),
            mapView.width,
            mapView.height,
        ).hashCode()
    }

    fun computeBenchClusters(mapView: MapView): Pair<List<ViewportBenchClusterer.BenchCluster>, Int> {
        val source = benchMarkerSource()
        val visible = ViewportBenchClusterer.filterInViewport(source, mapView.boundingBox)
        val clusterZoom = ViewportBenchClusterer.quantizeZoomForClustering(zoomLevel)
        if (clusterZoom < 16.0 && visible.size > 10) {
            val result = ViewportBenchClusterer.cluster(visible, mapView, zoomLevel)
            val onMap = result.mapped.coerceAtMost(result.inViewport)
            return result.clusters to onMap
        }
        val singles = visible.map { bench ->
            ViewportBenchClusterer.BenchCluster(listOf(bench), bench.lat, bench.lng)
        }
        return singles to visible.size
    }

    fun rebuildBenchMarkers(mapView: MapView, markerDrawable: BitmapDrawable) {
        // Close popups before removing markers — removeAll does not detach InfoWindows.
        dismissAllInfoWindows(mapView)
        val overlaysToRemove = mapView.overlays.filter { overlay ->
            overlay is Marker &&
                overlay != userMarker &&
                !isTodoMarker(overlay)
        }
        for (overlay in overlaysToRemove) {
            (overlay as? Marker)?.closeInfoWindow()
        }
        mapView.overlays.removeAll(overlaysToRemove)

        val (clusters, visibleCount) = computeBenchClusters(mapView)
        onVisibleBenchCountChanged(visibleCount)
        createMarkers(mapView, clusters, markerDrawable, mapView.context)
    }

    fun syncTodoMarkers(mapView: MapView) {
        val existing = mapView.overlays.filterIsInstance<Marker>().filter { isTodoMarker(it) }
        mapView.overlays.removeAll(existing.toSet())

        val icon = createTodoMarkerDrawable(mapView.context)
        todoBenches.forEach { todo ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(todo.latitude, todo.longitude)
            marker.title = "To do"
            marker.subDescription = "Bench seen — photo still needed"
            marker.relatedObject = todo
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.icon = icon
            marker.setOnMarkerClickListener { _, _ ->
                stopFollowingUser()
                dismissAllInfoWindows(mapView)
                onTodoClick(todo)
                true
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
        lastTodoMarkersKey = todoBenches.hashCode()
    }

    // Rebuild markers when the bench source changes (e.g. search cleared or new results).
    LaunchedEffect(benches, globalBenches, searchResultBenches) {
        lastMarkerGenerationKey = 0
        lastBenchesHash = benchMarkerSource().hashCode() - 1
        lastProcessedPanKey = -1
        viewportPanKey++
        val mapView = mapViewRef ?: return@LaunchedEffect
        mapView.post {
            val currentMapView = mapViewRef ?: return@post
            rebuildBenchMarkers(currentMapView, benchMarkerDrawable)
            syncTodoMarkers(currentMapView)
            val generationKey = markerGenerationKey(currentMapView)
            lastMarkerGenerationKey = generationKey
            lastProcessedPanKey = viewportPanKey
            scrollGenerationHolder[0] = viewportScrollKey(currentMapView)
            lastBenchesHash = benchMarkerSource().hashCode()
            currentMapView.invalidate()
        }
    }

    LaunchedEffect(todoBenches) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        mapView.post {
            val currentMapView = mapViewRef ?: return@post
            syncTodoMarkers(currentMapView)
        }
    }

    // Keep Compose zoom state in sync with parent; map view zoom is driven by gestures and recenter.
    LaunchedEffect(mapZoomLevel) {
        zoomLevel = mapZoomLevel
    }

    LaunchedEffect(showBenchDetails) {
        if (!showBenchDetails) {
            mapViewRef?.let { clearBenchSelection(it) }
        }
    }

    // Parent changed map center (search, bench details, initial load) — not on every pan/zoom.
    LaunchedEffect(mapCenter.latitude, mapCenter.longitude) {
        if (followingUserLocation) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        mapView.post {
            mapView.controller.setCenter(mapCenter)
        }
    }

    LaunchedEffect(mapFocusRequest, mapViewRef) {
        if (mapFocusRequest == 0 || mapFocusRequest == lastMapFocusRequest) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        lastMapFocusRequest = mapFocusRequest
        stopFollowingUser()
        zoomLevel = mapZoomLevel
        mapView.post {
            mapView.controller.setCenter(mapCenter)
            mapView.controller.setZoom(mapZoomLevel)
            mapView.invalidate()
        }
    }

    LaunchedEffect(recenterRequest, mapViewRef) {
        if (recenterRequest == 0 || recenterRequest == lastProcessedRecenterRequest) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        lastProcessedRecenterRequest = recenterRequest
        val target = withContext(Dispatchers.IO) {
            resolveUserLocation(context)?.let { loc ->
                GeoPoint(loc.latitude, loc.longitude)
            }
        } ?: userLocation ?: return@LaunchedEffect

        userLocation = target
        followingUserLocation = true
        val recenterZoom = maxOf(zoomLevel, 16.0)
        zoomLevel = recenterZoom
        setMapCenter(target)
        onCenterChanged(target)
        onZoomLevelChanged(recenterZoom)

        mapView.post {
            markProgrammaticFollowMove()
            mapView.controller.setCenter(target)
            mapView.controller.setZoom(recenterZoom)
            mapView.invalidate()
        }
    }

    // Keep map locked to the user while follow mode is active (~0.5s updates).
    LaunchedEffect(followingUserLocation) {
        if (!followingUserLocation) return@LaunchedEffect
        userLocationUpdates(context, intervalMs = 500L).collectLatest { location ->
            if (!followingUserLocation) return@collectLatest
            val target = GeoPoint(location.latitude, location.longitude)
            userLocation = target
            setMapCenter(target)
            onCenterChanged(target)
            val mapView = mapViewRef ?: return@collectLatest
            mapView.post {
                if (!followingUserLocation) return@post
                markProgrammaticFollowMove()
                mapView.controller.setCenter(target)
                mapView.invalidate()
            }
        }
    }

    // Handle user location marker updates when userLocation changes
    LaunchedEffect(userLocation) {
        mapViewRef?.let { mapView ->
            val currentUserLocation = userLocation
            if (currentUserLocation != null) {
                if (userMarker == null) {
                    // Create user marker if it doesn't exist
                    userMarker = Marker(mapView)
                    userMarker!!.position = currentUserLocation
                    userMarker!!.title = if (isRefreshingLocation) "Updating your location..." else "You are here"
                    userMarker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mapView.overlays.add(userMarker!!)
                    android.util.Log.d("MapScreen", "Created new user marker at: ${ currentUserLocation.latitude}, ${currentUserLocation.longitude}")
                } else {
                    // Update existing user marker position and title
                    userMarker!!.position = currentUserLocation
                    userMarker!!.title = if (isRefreshingLocation) "Updating your location..." else "You are here"
                    android.util.Log.d("MapScreen", "Updated user marker to: ${currentUserLocation.latitude}, ${currentUserLocation.longitude}")
                }
                mapView.invalidate()
            } else {
                // Remove user marker if location is not available
                userMarker?.let { marker ->
                    mapView.overlays.remove(marker)
                    userMarker = null
                    android.util.Log.d("MapScreen", "Removed user marker")
                }
                mapView.invalidate()
            }
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

                    // Set tile source from map style preference
                    mapView.setTileSource(MapTileSources.tileSourceFor(mapProvider))
                    
                    // Configure map for smooth scrolling and preloading
                    mapView.setMultiTouchControls(true)
                    mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    
                    // Enable tile preloading for smoother scrolling
                    mapView.isTilesScaledToDpi = true
                    // Horizontal wrap (date line); no vertical copies above/below at min zoom
                    mapView.isHorizontalMapRepetitionEnabled = true
                    mapView.isVerticalMapRepetitionEnabled = false
                    val tileSystem = MapView.getTileSystem()
                    mapView.setScrollableAreaLimitLatitude(
                        tileSystem.maxLatitude,
                        tileSystem.minLatitude,
                        0
                    )
                    
                    // Enable hardware acceleration for smoother rendering
                    mapView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // Set tile overlay options for better performance
                    mapView.overlayManager.tilesOverlay?.loadingBackgroundColor = AndroidColor.TRANSPARENT
                    mapView.overlayManager.tilesOverlay?.loadingLineColor = AndroidColor.TRANSPARENT
                    if (!initialZoomSet) {
                        mapView.controller.setZoom(zoomLevel)
                        initialZoomSet = true
                    }
                    mapView.controller.setCenter(mapCenter)
                    
                    // Create user marker if location is available
                    if (userLocation != null) {
                        val newUserMarker = Marker(mapView)
                        newUserMarker.position = userLocation
                        newUserMarker.title = if (isRefreshingLocation) "Updating your location..." else "You are here"
                        newUserMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mapView.overlays.add(newUserMarker)
                        userMarker = newUserMarker
                    }
                    
                    // Create markers with a slight delay to ensure map view is fully initialized
                    mapView.post {
                        mapView.postDelayed({
                            rebuildBenchMarkers(mapView, benchMarkerDrawable)
                            syncTodoMarkers(mapView)
                            val initialKey = markerGenerationKey(mapView)
                            lastMarkerGenerationKey = initialKey
                            lastProcessedPanKey = viewportPanKey
                            scrollGenerationHolder[0] = viewportScrollKey(mapView)
                            lastBenchesHash = benchMarkerSource().hashCode()
                            mapView.invalidate()
                            android.util.Log.d("MapScreen", "Initial markers created and click listeners attached")
                        }, 100)
                    }
                    
                    // Use the newer MapListener API instead of deprecated setMapListener
                    mapView.addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val mapView = event?.source ?: return true
                            if (shouldCancelFollowFromUserGesture()) {
                                stopFollowingUser()
                            }
                            val key = viewportScrollKey(mapView)
                            if (key != scrollGenerationHolder[0]) {
                                scrollGenerationHolder[0] = key
                                viewportPanKey = key
                            }
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            if (shouldCancelFollowFromUserGesture()) {
                                stopFollowingUser()
                            }
                            val z = mapView.zoomLevelDouble
                            zoomLevel = z
                            onZoomLevelChanged(z)
                            return true
                        }
                    })
                    // Background taps dismiss the open popup. Placed at index 0 so markers (later in the
                    // list) receive the tap first; if no marker consumes it, this closes any bubble.
                    mapView.overlays.add(
                        0,
                        MapEventsOverlay(
                            object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    val point = Point()
                                    mapView.projection.toPixels(p, point)
                                    val me = MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_DOWN,
                                        point.x.toFloat(),
                                        point.y.toFloat(),
                                        0
                                    )
                                    return try {
                                        if (tapHitsAnyMarker(mapView, me)) {
                                            false
                                        } else {
                                            stopFollowingUser()
                                            clearBenchSelection(mapView)
                                            false
                                        }
                                    } finally {
                                        me.recycle()
                                    }
                                }

                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            }
                        )
                    )
                    // 3-second still hold saves a "to do" location (does not consume pan/zoom).
                    mapView.overlays.add(
                        MapLongHoldOverlay(holdDurationMs = 3_000L) { geo ->
                            stopFollowingUser()
                            onMapLongHold(geo)
                        }
                    )

                    mapView
                },
                update = { mapView ->
                    val expectedTileSource = MapTileSources.tileSourceFor(mapProvider)
                    if (mapView.tileProvider.tileSource.name() != expectedTileSource.name()) {
                        mapView.setTileSource(expectedTileSource)
                    }

                    val panTrigger = viewportPanKey
                    val currentBenchesHash = benchMarkerSource().hashCode()
                    val generationKey = markerGenerationKey(mapView)
                    if (
                        generationKey != lastMarkerGenerationKey ||
                        currentBenchesHash != lastBenchesHash ||
                        panTrigger != lastProcessedPanKey
                    ) {
                        rebuildBenchMarkers(mapView, benchMarkerDrawable)
                        syncTodoMarkers(mapView)
                        lastMarkerGenerationKey = generationKey
                        lastProcessedPanKey = panTrigger
                        scrollGenerationHolder[0] = viewportScrollKey(mapView)
                        lastBenchesHash = currentBenchesHash
                    } else if (todoBenches.hashCode() != lastTodoMarkersKey) {
                        syncTodoMarkers(mapView)
                    }
                    
                    // User location marker only — do not reset map center/zoom here (causes post-gesture drift).
                    if (userLocation != null) {
                        if (userMarker == null) {
                            userMarker = Marker(mapView)
                            userMarker!!.position = userLocation
                            userMarker!!.title =
                                if (isRefreshingLocation) "Updating your location..." else "You are here"
                            userMarker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(userMarker!!)
                        } else {
                            userMarker!!.position = userLocation
                            userMarker!!.title =
                                if (isRefreshingLocation) "Updating your location..." else "You are here"
                        }
                    } else {
                        userMarker?.let { marker ->
                            mapView.overlays.remove(marker)
                            userMarker = null
                        }
                    }
                }
            )
        }
    }
}
