package org.openbenches.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.openbenches.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import org.openbenches.data.fetchBenchDetails
import org.openbenches.data.BenchDetailsFeature
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skydoves.landscapist.glide.GlideImage
import org.openbenches.ui.zoomable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.border
import androidx.core.text.HtmlCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.Locale
import androidx.compose.foundation.layout.height
import org.openbenches.data.fetchAddressFromLatLng
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.CustomZoomButtonsController
import android.content.Context
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.MutableState

/**
 * Screen displaying details for a bench.
 * Shows a back button at the top left.
 * @param benchId The ID of the bench to show details for
 * @param onBack Callback when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchDetailsScreen(benchId: Int, onBack: (lat: Double?, lng: Double?) -> Unit) {
    rememberCoroutineScope()
    val loading = remember { mutableStateOf(true) }
    val error = remember { mutableStateOf<String?>(null) }
    val benchDetails = remember { mutableStateOf<BenchDetailsFeature?>(null) }
    val fullscreenImageIndex = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(benchId) {
        loading.value = true
        error.value = null
        try {
            val response = fetchBenchDetails(benchId)
            benchDetails.value = response.features?.firstOrNull()
        } catch (e: Exception) {
            error.value = e.message
        } finally {
            loading.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Details of Bench ID:$benchId") },
                navigationIcon = {
                    IconButton(onClick = {
                        val details = benchDetails.value
                        val coords = details?.geometry?.coordinates
                        if (coords != null && coords.size >= 2) {
                            onBack(coords[1], coords[0])
                        } else {
                            onBack(null, null)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_button)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                loading.value -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                }
                error.value != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${error.value}", style = MaterialTheme.typography.bodyLarge)
                }
                benchDetails.value != null -> {
                    val props = benchDetails.value!!.properties
                    val benchImages = props?.media
                        ?.filter { it.url.startsWith("/image/") }
                        ?: emptyList()
                    // Images row just below the title
                    if (benchImages.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(top = 4.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(benchImages) { i, image ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val label = getMediaTypeTitle(image.mediaType)
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val imageUrl = "https://openbenches.org" + image.url
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "Bench photo",
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .padding(4.dp)
                                            .clickable { fullscreenImageIndex.value = i }
                                    )
                                    val username = image.username
                                    if (!username.isNullOrBlank()) {
                                        Column(
                                            modifier = Modifier.padding(top = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Photo uploaded by:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = username,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.padding(top = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Awaiting",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray,
                                            )
                                            Text(
                                                text = "Photo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    } else {
                        // No images, show 3 placeholders as before
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(top = 4.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(3) { i ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val label = listOf("plaque", "bench", "view").getOrNull(i)?.let { getMediaTypeTitle(it) } ?: "Photo"
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CameraAlt,
                                            contentDescription = "No photo",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Awaiting",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray,
                                        )
                                        Text(
                                            text = "Photo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                                if (i < 2) Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                    // Fullscreen image dialog
                    if (fullscreenImageIndex.value != null && fullscreenImageIndex.value!! < benchImages.size) {
                        FullscreenImageDialog(
                            imageUrls = benchImages.map { "https://openbenches.org" + it.url },
                            currentIndex = fullscreenImageIndex,
                            onDismiss = { fullscreenImageIndex.value = null }
                        )
                    }
                    // Details below images
                    val coords = benchDetails.value?.geometry?.coordinates
                    if (coords != null && coords.size >= 2) {
                        val lat = coords[1]
                        val lng = coords[0]
                        Text(
                            text = "Bench Location",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(horizontal = 16.dp),
                            factory = { ctx ->
                                // Configure osmdroid
                                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                                
                                // Set tile cache size for better performance
                                Configuration.getInstance().tileFileSystemCacheMaxBytes = 20L * 1024L * 1024L // 20MB cache
                                Configuration.getInstance().tileDownloadThreads = 4 // Download threads
                                Configuration.getInstance().tileDownloadMaxQueueSize = 50 // Download queue
                                
                                val mapView = MapView(ctx)
                                
                                // Set tile source to OpenStreetMap
                                mapView.setTileSource(TileSourceFactory.MAPNIK)
                                
                                // Configure map for bench details view
                                mapView.setMultiTouchControls(true)
                                mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                
                                // Enable tile preloading for smoother experience
                                mapView.isTilesScaledToDpi = true
                                mapView.isHorizontalMapRepetitionEnabled = true
                                mapView.isVerticalMapRepetitionEnabled = true
                                
                                // Enable hardware acceleration for smoother rendering
                                mapView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                
                                // Set tile overlay options for better performance
                                mapView.overlayManager.tilesOverlay?.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
                                mapView.overlayManager.tilesOverlay?.setLoadingLineColor(android.graphics.Color.TRANSPARENT)
                                
                                // Set initial position and zoom
                                val benchLocation = GeoPoint(lat, lng)
                                mapView.controller.setZoom(16.0) // Closer zoom for bench details
                                mapView.controller.setCenter(benchLocation)
                                
                                // Add bench marker
                                val marker = Marker(mapView)
                                marker.position = benchLocation
                                marker.title = "Bench Location"
                                marker.subDescription = "Tap to see bench details"
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                
                                // Add click listener to show info window
                                marker.setOnMarkerClickListener { m, _ ->
                                    if (!m.isInfoWindowShown) {
                                        m.showInfoWindow()
                                    }
                                    true
                                }
                                
                                // Create custom marker icon (optional - you can use default or custom)
                                try {
                                    val markerBitmapRaw = android.graphics.BitmapFactory.decodeResource(
                                        ctx.resources, 
                                        ctx.resources.getIdentifier("marker", "drawable", ctx.packageName)
                                    )
                                    if (markerBitmapRaw != null) {
                                        val markerBitmap = android.graphics.Bitmap.createScaledBitmap(markerBitmapRaw, 98, 64, true)
                                        val markerDrawable = android.graphics.drawable.BitmapDrawable(ctx.resources, markerBitmap)
                                        marker.icon = markerDrawable
                                    }
                                } catch (e: Exception) {
                                    // Use default marker if custom marker fails to load
                                }
                                
                                mapView.overlays.add(marker)
                                mapView
                            },
                            update = { mapView ->
                                // Update map if needed (e.g., if bench location changes)
                                val benchLocation = GeoPoint(lat, lng)
                                mapView.controller.setCenter(benchLocation)
                                
                                // Update marker position
                                mapView.overlays.clear()
                                val marker = Marker(mapView)
                                marker.position = benchLocation
                                marker.title = "Bench Location"
                                marker.subDescription = "Tap to see bench details"
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                
                                // Add click listener to show info window
                                marker.setOnMarkerClickListener { m, _ ->
                                    if (!m.isInfoWindowShown) {
                                        m.showInfoWindow()
                                    }
                                    true
                                }
                                
                                // Recreate marker icon
                                try {
                                    val markerBitmapRaw = android.graphics.BitmapFactory.decodeResource(
                                        mapView.context.resources, 
                                        mapView.context.resources.getIdentifier("marker", "drawable", mapView.context.packageName)
                                    )
                                    if (markerBitmapRaw != null) {
                                        val markerBitmap = android.graphics.Bitmap.createScaledBitmap(markerBitmapRaw, 98, 64, true)
                                        val markerDrawable = android.graphics.drawable.BitmapDrawable(mapView.context.resources, markerBitmap)
                                        marker.icon = markerDrawable
                                    }
                                } catch (e: Exception) {
                                    // Use default marker if custom marker fails to load
                                }
                                
                                mapView.overlays.add(marker)
                                mapView.invalidate()
                            }
                        )
                        // Centered 'Near:' text
                        Text(
                            text = "Near:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 0.dp, bottom = 0.dp)
                        )
                        // Fetch and display address below the map
                        val addressState = remember { mutableStateOf<String?>(null) }
                        val addressLoading = remember { mutableStateOf(false) }
                        val addressError = remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(lat, lng) {
                            addressLoading.value = true
                            addressError.value = null
                            try {
                                addressState.value = fetchAddressFromLatLng(lat, lng)
                            } catch (e: Exception) {
                                addressError.value = "Failed to fetch address"
                            } finally {
                                addressLoading.value = false
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        when {
                            addressLoading.value -> Text("Loading address...", style = MaterialTheme.typography.bodySmall)
                            addressError.value != null -> Text(addressError.value!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                            !addressState.value.isNullOrBlank() -> Text(addressState.value!!, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp))
                        }
                    }
                    if (!props?.popupContent.isNullOrBlank()) {
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = "Inscription Details",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                            val plainText = HtmlCompat.fromHtml(props?.popupContent ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = plainText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (!props.createdAt.isNullOrBlank()) {
                                val input = props.createdAt ?: ""
                                val formattedDate = try {
                                    // Try parsing ISO 8601 with or without time zone
                                    val zoned = try {
                                        ZonedDateTime.parse(input)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val local = try {
                                        LocalDateTime.parse(input)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val date = zoned?.toLocalDate() ?: local?.toLocalDate() ?: null
                                    date?.format(DateTimeFormatter.ofPattern("yyyy MMMM dd", Locale.getDefault())) ?: input
                                } catch (e: DateTimeParseException) {
                                    input
                                }
                                Text(
                                    text = "Created: $formattedDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No details found.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Dialog for displaying a fullscreen image with navigation arrows.
 *
 * @param imageUrls List of image URLs to display
 * @param currentIndex MutableState of the currently displayed image index
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
private fun FullscreenImageDialog(
    imageUrls: List<String>,
    currentIndex: MutableState<Int?>,
    onDismiss: () -> Unit
) {
    val index = currentIndex.value ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Fullscreen image (background)
            AsyncImage(
                model = imageUrls[index],
                contentDescription = "Full screen bench photo",
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(),
            )
            // Left arrow
            if (index > 0) {
                IconButton(
                    onClick = { currentIndex.value = index - 1 },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous photo",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            // Right arrow
            if (index < imageUrls.size - 1) {
                IconButton(
                    onClick = { currentIndex.value = index + 1 },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Next photo",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = 180f }
                    )
                }
            }
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// Helper function to map mediaType to user-friendly title
private fun getMediaTypeTitle(mediaType: String?): String = when (mediaType?.lowercase()) {
    "inscription" -> "Inscription"
    "plaque" -> "Inscription/Plaque"
    "bench" -> "The Bench"
    "view" -> "View from the Bench"
    else -> "Photo"
}

@Preview(showBackground = true)
@Composable
fun BenchDetailsScreenPreview() {
    BenchDetailsScreen(benchId = 1, onBack = { _, _ -> })
} 