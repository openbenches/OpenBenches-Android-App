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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.openbenches.data.Bench
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Marker as GoogleMarker
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import androidx.compose.runtime.LaunchedEffect
import com.google.maps.android.compose.MapUiSettings
import org.osmdroid.util.BoundingBox
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdateFactory

/**
 * Enum for selecting map provider
 */
enum class MapProvider {
    OSM,
    GOOGLE
}

/**
 * MapScreen composable displays benches from OpenBenches API on OpenStreetMap (osmdroid) or Google Maps,
 * centered on the user's current location if available.
 * @param mapProvider Which map provider to use (OSM or Google)
 * @param googleMapType Google Maps map type (NORMAL, SATELLITE, HYBRID, TERRAIN)
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
    googleMapType: MapType = MapType.NORMAL,
    onBenchPopupClick: (benchId: Int) -> Unit = {},
    zoomToFitBenches: Boolean = false,
    onZoomToFitHandled: () -> Unit = {},
    shouldAnimateToMapCenter: Boolean = false,
    onMapCenterAnimated: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var lastRecenter by remember { mutableStateOf(false) }
    var initialZoomSet by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(15.0) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val cameraPositionState = if (mapProvider == MapProvider.GOOGLE) rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            LatLng(mapCenter.latitude, mapCenter.longitude),
            zoomLevel.toFloat()
        )
    } else null

    // Only center on user location once after map loads and user location is available
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var mapLoaded by remember { mutableStateOf(false) }
    var lastAnimatedMapCenter by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(userLocation, mapProvider, cameraPositionState, mapLoaded) {
        if (
            mapProvider == MapProvider.GOOGLE &&
            userLocation != null &&
            cameraPositionState != null &&
            !hasCenteredOnUser &&
            mapLoaded
        ) {
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                LatLng(userLocation!!.latitude, userLocation!!.longitude), 15f
            )
            cameraPositionState.animate(cameraUpdate)
            hasCenteredOnUser = true
        }
    }

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

    LaunchedEffect(mapCenter, mapProvider, cameraPositionState, mapLoaded, shouldAnimateToMapCenter) {
        if (
            mapProvider == MapProvider.GOOGLE &&
            cameraPositionState != null &&
            mapLoaded &&
            shouldAnimateToMapCenter
        ) {
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                LatLng(mapCenter.latitude, mapCenter.longitude), 15f
            )
            cameraPositionState.animate(cameraUpdate)
            lastAnimatedMapCenter = mapCenter
            onMapCenterAnimated()
        }
    }

    val markerBitmap: Bitmap? = remember {
        val resId = context.resources.getIdentifier("marker", "drawable", context.packageName)
        if (resId != 0) {
            BitmapFactory.decodeResource(context.resources, resId)?.let {
                Bitmap.createScaledBitmap(it, 128, 128, true)
            }
        } else null
    }

    LaunchedEffect(zoomToFitBenches, benches, mapProvider) {
        if (zoomToFitBenches && benches.isNotEmpty() && mapProvider == MapProvider.OSM) {
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
    LaunchedEffect(zoomToFitBenches, benches, mapProvider) {
        if (zoomToFitBenches && benches.isNotEmpty() && mapProvider == MapProvider.GOOGLE && cameraPositionState != null) {
            val builder = LatLngBounds.builder()
            benches.forEach { bench ->
                builder.include(LatLng(bench.lat, bench.lng))
            }
            val bounds = builder.build()
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100)
            cameraPositionState.animate(cameraUpdate)
            onZoomToFitHandled()
        }
    }
    LaunchedEffect(recenterOnUser, userLocation, mapProvider) {
        if (recenterOnUser && mapProvider == MapProvider.GOOGLE && cameraPositionState != null) {
            userLocation?.let { location ->
                val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude), 15f
                )
                cameraPositionState.animate(cameraUpdate)
                onRecenterHandled()
            }
        }
    }

    if (mapProvider == MapProvider.GOOGLE && cameraPositionState != null) {
        LaunchedEffect(cameraPositionState.position) {
            val pos = cameraPositionState.position.target
            setMapCenter(org.osmdroid.util.GeoPoint(pos.latitude, pos.longitude))
        }
    }

    Box(modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (error != null) {
            Text("Error: $error", Modifier.align(Alignment.Center))
        } else {
            when (mapProvider) {
                MapProvider.OSM -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                            val mapView = MapView(ctx)
                            mapViewRef = mapView
                            mapView.setTileSource(TileSourceFactory.MAPNIK)
                            mapView.setMultiTouchControls(true)
                            mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            val center = mapCenter
                            if (!initialZoomSet) {
                                mapView.controller.setZoom(zoomLevel)
                                initialZoomSet = true
                            }
                            mapView.controller.setCenter(center)
                            if (userLocation != null) {
                                val userMarker = Marker(mapView)
                                userMarker.position = userLocation
                                userMarker.title = "You are here"
                                userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                mapView.overlays.add(userMarker)
                            }
                            val markerBitmapRaw = BitmapFactory.decodeResource(ctx.resources, ctx.resources.getIdentifier("marker", "drawable", ctx.packageName))
                            val markerBitmap = Bitmap.createScaledBitmap(markerBitmapRaw, 128, 128, true)
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
                            mapView.setMapListener(object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    setMapCenter(mapView.mapCenter as GeoPoint)
                                    onCenterChanged(mapView.mapCenter as GeoPoint)
                                    return true
                                }
                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    zoomLevel = mapView.zoomLevelDouble
                                    setMapCenter(mapView.mapCenter as GeoPoint)
                                    onCenterChanged(mapView.mapCenter as GeoPoint)
                                    return true
                                }
                            })
                            mapView
                        },
                        update = { mapView ->
                            mapViewRef = mapView
                            mapView.overlays.clear()
                            if (userLocation != null) {
                                val userMarker = Marker(mapView)
                                userMarker.position = userLocation
                                userMarker.title = "You are here"
                                userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                mapView.overlays.add(userMarker)
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
                                mapView.controller.setZoom(zoomLevel)
                                mapView.controller.setCenter(mapCenter)
                            }
                            val markerBitmapRaw = BitmapFactory.decodeResource(mapView.context.resources, mapView.context.resources.getIdentifier("marker", "drawable", mapView.context.packageName))
                            val markerBitmap = Bitmap.createScaledBitmap(markerBitmapRaw, 128, 128, true)
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
                            mapView.invalidate()
                        }
                    )
                }
                MapProvider.GOOGLE -> {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState!!,
                        properties = com.google.maps.android.compose.MapProperties(
                            mapType = googleMapType,
                            isMyLocationEnabled = userLocation != null
                        ),
                        uiSettings = MapUiSettings(myLocationButtonEnabled = false),
                        onMapClick = { latLng ->
                            setMapCenter(GeoPoint(latLng.latitude, latLng.longitude))
                            onCenterChanged(GeoPoint(latLng.latitude, latLng.longitude))
                        },
                        onMapLoaded = {
                            mapLoaded = true
                        }
                    ) {
                        userLocation?.let {
                            GoogleMarker(
                                state = com.google.maps.android.compose.MarkerState(position = LatLng(it.latitude, it.longitude)),
                                title = "You are here"
                            )
                        }
                        benches.forEach { bench ->
                            GoogleMarker(
                                state = com.google.maps.android.compose.MarkerState(position = LatLng(bench.lat, bench.lng)),
                                title = "Bench #${bench.id}",
                                snippet = bench.popupContent,
                                icon = markerBitmap?.let { BitmapDescriptorFactory.fromBitmap(it) },
                                onInfoWindowClick = { onBenchPopupClick(bench.id) }
                            )
                        }
                    }
                }
            }
        }
    }
} 