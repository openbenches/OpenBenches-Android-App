package org.openbenches.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.openbenches.ui.theme.OpenBenchesTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.openbenches.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import org.openbenches.data.fetchBenchDetails
import org.openbenches.data.BenchDetailsFeature
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.core.text.HtmlCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.CustomZoomButtonsController
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.MutableState
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import org.openbenches.util.saveBenchPhotoToGallery

/**
 * Screen displaying details for a bench.
 * Shows a back button at the top left.
 * @param benchId The ID of the bench to show details for
 * @param onBack Callback when the back button is pressed
 * @param authToken When non-null, shows an Edit action next to Share
 * @param onEdit Called when the Edit action is pressed (logged-in users only)
 * @param refreshKey Increment to reload bench details after an edit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchDetailsScreen(
    benchId: Int,
    onBack: (lat: Double?, lng: Double?) -> Unit,
    mapProvider: MapProvider = MapProvider.OSM,
    authToken: String? = null,
    onEdit: (() -> Unit)? = null,
    refreshKey: Int = 0,
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val loading = remember { mutableStateOf(true) }
    val error = remember { mutableStateOf<String?>(null) }
    val benchDetails = remember { mutableStateOf<BenchDetailsFeature?>(null) }
    val fullscreenImageIndex = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(benchId, refreshKey) {
        loading.value = true
        error.value = null
        try {
            println("Fetching bench details for ID: $benchId")
            val response = fetchBenchDetails(benchId)
            println("Response received: ${response.features?.size ?: 0} features")
            if (response.features.isNullOrEmpty()) {
                error.value = "No bench details found for ID: $benchId"
            } else {
                benchDetails.value = response.features.first()
                println("Bench details loaded successfully")
            }
        } catch (e: Exception) {
            println("Error loading bench details: ${e.message}")
            e.printStackTrace()
            error.value = "Failed to load bench details: ${e.message}"
        } finally {
            loading.value = false
        }
    }

    Scaffold(
        topBar = {
            OpenBenchesTopAppBar(
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
                },
                actions = {
                    if (!authToken.isNullOrBlank() && onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit bench"
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val url = "https://openbenches.org/bench/$benchId"
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("OpenBenches bench URL", url)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Copy bench link"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    if (props != null) {
                        val benchImages = props.media
                            ?.filter { it.url.isNotEmpty() }
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
                                        val imageUrl = if (image.url.startsWith("http")) {
                                            image.url
                                        } else {
                                            "https://openbenches.org" + image.url
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .padding(4.dp)
                                                .clickable {
                                                    if (i < benchImages.size) {
                                                        fullscreenImageIndex.value = i
                                                    }
                                                }
                                        ) {
                                            BenchPhotoImage(
                                                imageUrl = imageUrl,
                                                modifier = Modifier.fillMaxSize(),
                                                contentDescription = "Bench photo"
                                            )
                                        }
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
                                                .size(144.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Gray.copy(alpha = 0.3f))
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "No photo",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                    if (i < 2) Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }
                        // Fullscreen image dialog
                        if (fullscreenImageIndex.value != null && 
                            fullscreenImageIndex.value!! >= 0 && 
                            fullscreenImageIndex.value!! < benchImages.size) {
                            FullscreenImageDialog(
                                imageUrls = benchImages.map { 
                                    if (it.url.startsWith("http")) it.url else "https://openbenches.org" + it.url 
                                },
                                imageLabels = benchImages.map { getMediaTypeTitle(it.mediaType) },
                                currentIndex = fullscreenImageIndex,
                                benchId = benchId,
                                onDismiss = { fullscreenImageIndex.value = null }
                            )
                        }
                        // Rest of the content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Description (using popupContent as description)
                            if (!props.popupContent.isNullOrBlank()) {
                                val plainText = HtmlCompat.fromHtml(props.popupContent, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = androidx.compose.foundation.BorderStroke(
                                        3.dp,
                                        MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = plainText,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            
                            // Map showing bench location
                            val coordinates = benchDetails.value?.geometry?.coordinates
                            if (coordinates != null && coordinates.size >= 2) {
                                val benchLat = coordinates[1]
                                val benchLng = coordinates[0]
                                
                                Text(
                                    text = "Bench Location",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    androidx.compose.runtime.key(mapProvider) {
                                    AndroidView(
                                        factory = { ctx ->
                                            // Configure osmdroid
                                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                                            
                                            val mapView = MapView(ctx)
                                            
                                            mapView.setTileSource(MapTileSources.tileSourceFor(mapProvider))
                                            
                                            // Configure map
                                            mapView.setMultiTouchControls(true)
                                            mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                            mapView.isTilesScaledToDpi = true
                                            
                                            // Set center and zoom to bench location
                                            val benchPoint = GeoPoint(benchLat, benchLng)
                                            mapView.controller.setZoom(18.0) // Close zoom to show bench clearly
                                            mapView.controller.setCenter(benchPoint)
                                            
                                            // Add bench marker
                                            val marker = Marker(mapView)
                                            marker.position = benchPoint
                                            marker.title = "Bench #$benchId"
                                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            
                                            // Load marker icon
                                            try {
                                                val markerBitmapRaw = BitmapFactory.decodeResource(ctx.resources, ctx.resources.getIdentifier("marker", "drawable", ctx.packageName))
                                                if (markerBitmapRaw != null) {
                                                    val markerBitmap = Bitmap.createScaledBitmap(markerBitmapRaw, 98, 64, true)
                                                    val markerDrawable = BitmapDrawable(ctx.resources, markerBitmap)
                                                    marker.icon = markerDrawable
                                                }
                                            } catch (e: Exception) {
                                                // Use default marker if custom marker fails to load
                                            }
                                            
                                            mapView.overlays.add(marker)
                                            
                                            mapView
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    }
                                }
                            }
                            if (!props.createdAt.isNullOrBlank()) {
                                val input = props.createdAt
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
                                    val date = zoned?.toLocalDate() ?: local?.toLocalDate()
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
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No details found.", style = MaterialTheme.typography.bodyLarge)
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
}

/**
 * Dialog for displaying a fullscreen image with swipe and arrow navigation.
 *
 * @param imageUrls List of image URLs to display
 * @param currentIndex MutableState of the currently displayed image index
 * @param onDismiss Callback when the dialog is dismissed
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenImageDialog(
    imageUrls: List<String>,
    imageLabels: List<String>,
    currentIndex: MutableState<Int?>,
    benchId: Int,
    onDismiss: () -> Unit
) {
    val startIndex = currentIndex.value ?: return
    if (startIndex !in imageUrls.indices) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { imageUrls.size },
    )
    val configuration = LocalConfiguration.current
    val bannerTopPadding = if (
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        8.dp
    } else {
        56.dp
    }
    val photoLabelBottomPadding = if (
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        24.dp
    } else {
        128.dp
    }
    val coroutineScope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val zoomState = rememberFullscreenZoomState()
    var isDownloading by remember { mutableStateOf(false) }

    fun runDownload() {
        val url = imageUrls.getOrNull(pagerState.currentPage) ?: return
        if (isDownloading) return
        isDownloading = true
        coroutineScope.launch {
            val fileName = "openbenches-bench-${benchId}-photo-${pagerState.currentPage + 1}.jpg"
            val result = saveBenchPhotoToGallery(context, url, fileName)
            isDownloading = false
            Toast.makeText(
                context,
                if (result.isSuccess) {
                    "Photo saved to Pictures/OpenBenches"
                } else {
                    result.exceptionOrNull()?.message ?: "Could not download photo"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runDownload()
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to save photos",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onDownloadClick() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        runDownload()
    }

    LaunchedEffect(pagerState.currentPage) {
        zoomState.reset()
    }

    LaunchedEffect(pagerState.currentPage) {
        if (currentIndex.value != pagerState.currentPage) {
            currentIndex.value = pagerState.currentPage
        }
    }

    LaunchedEffect(currentIndex.value) {
        val target = currentIndex.value ?: return@LaunchedEffect
        if (target in imageUrls.indices && target != pagerState.currentPage) {
            pagerState.scrollToPage(target)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .fullscreenDoubleTapZoom(zoomState),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .fullscreenPagerSwipe(pagerState, coroutineScope, zoomState),
            ) { pagerPage ->
                BenchPhotoImage(
                    imageUrl = imageUrls[pagerPage],
                    contentDescription = "Full screen bench photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .fullscreenZoomGraphics(zoomState)
                        .fullscreenPhotoGestures(zoomState),
                    spinnerSize = 48.dp
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = photoLabelBottomPadding)
                    .zIndex(3f),
                color = Color.White.copy(alpha = 0.92f),
                contentColor = Color.Black,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.35f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = imageLabels.getOrNull(page) ?: "Photo",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            // Left arrow
            if (page > 0) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page - 1)
                        }
                    },
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
            if (page < imageUrls.size - 1) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page + 1)
                        }
                    },
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
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp, end = 4.dp)
                    .zIndex(4f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onDownloadClick() },
                    enabled = !isDownloading,
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download photo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = bannerTopPadding),
                color = Color.White.copy(alpha = 0.92f),
                contentColor = Color.Black,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.35f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Double-tap to zoom in/out",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Swipe or use arrows to browse through photos",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchPhotoImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Bench photo",
    spinnerSize: Dp = 32.dp,
) {
    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        loading = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    strokeWidth = 2.dp
                )
            }
        }
    )
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
