package org.openbenches.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openbenches.data.BenchMedia
import org.openbenches.data.UploadBenchResult
import org.openbenches.data.fetchBenchDetails
import org.openbenches.data.updateBench
import org.openbenches.ui.theme.OpenBenchesTopAppBar
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private val editPhotoTypeOptions = listOf(
    "Close-up of the inscription",
    "Long shot of the bench",
    "View from the bench",
    "Panoramic view",
    "VR 360 panoramic",
    "Video of the bench",
)

private fun editDefaultPhotoType(index: Int): String = when (index) {
    0 -> "Close-up of the inscription"
    1 -> "Long shot of the bench"
    2 -> "View from the bench"
    else -> "Long shot of the bench"
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

private val EditPhotoUriListSaver = listSaver<List<Uri?>, String>(
    save = { list -> list.map { it?.toString() ?: "" } },
    restore = { strs -> strs.map { s -> if (s.isEmpty()) null else Uri.parse(s) } },
)

private val EditPhotoTypesListSaver = listSaver<List<String>, String>(
    save = { it.toList() },
    restore = { ArrayList(it) },
)

private fun mediaUrlAbsolute(url: String): String =
    if (url.startsWith("http")) url else "https://openbenches.org$url"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNewPhotoSlot(
    photoNumber: Int,
    photoBitmap: Bitmap?,
    photoType: String,
    onPhotoTypeChanged: (String) -> Unit,
    onSelectGallery: () -> Unit,
    onSelectCamera: () -> Unit,
    onDeletePhoto: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (photoBitmap != null) Color.Transparent else Color.Gray.copy(alpha = 0.3f),
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = "New photo $photoNumber",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = { onDeletePhoto?.invoke() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove photo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onSelectGallery),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gallery",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onSelectCamera),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Camera",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .width(144.dp)
                .padding(top = 4.dp),
        ) {
            OutlinedTextField(
                value = photoType,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.labelSmall,
                singleLine = false,
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    disabledIndicatorColor = MaterialTheme.colorScheme.outline,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                editPhotoTypeOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodySmall,
                                softWrap = true,
                            )
                        },
                        onClick = {
                            onPhotoTypeChanged(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Edit an existing bench: inscription, location pin, optional new photos.
 * Does not allow deleting the bench or existing photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBenchScreen(
    benchId: Int,
    authToken: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    mapProvider: MapProvider = MapProvider.OSM,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var inscriptionText by rememberSaveable { mutableStateOf("") }
    var savedLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var savedLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var existingMedia by remember { mutableStateOf<List<BenchMedia>>(emptyList()) }

    var photoUris by rememberSaveable(stateSaver = EditPhotoUriListSaver) {
        mutableStateOf(listOf<Uri?>(null))
    }
    var photoTypes by rememberSaveable(stateSaver = EditPhotoTypesListSaver) {
        mutableStateOf(listOf(editDefaultPhotoType(0)))
    }
    var photoBitmaps by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var selectedPhotoIndex by rememberSaveable { mutableStateOf(-1) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var uploadSuccess by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(benchId) {
        loading = true
        loadError = null
        try {
            val response = fetchBenchDetails(benchId)
            val feature = response.features?.firstOrNull()
            if (feature == null) {
                loadError = "Bench not found"
            } else {
                val coords = feature.geometry?.coordinates
                if (coords != null && coords.size >= 2) {
                    savedLng = coords[0]
                    savedLat = coords[1]
                }
                val html = feature.properties?.popupContent.orEmpty()
                inscriptionText = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    .toString()
                    .trim()
                existingMedia = feature.properties?.media
                    ?.filter { it.url.isNotEmpty() }
                    .orEmpty()
            }
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load bench"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(photoUris) {
        photoBitmaps = photoUris.map { uri ->
            uri?.let { u ->
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openImageInputStreamPreservingMetadata(u)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    val photoFile = remember { File(context.cacheDir, "edit_temp_photo.jpg") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && selectedPhotoIndex >= 0) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )
            val index = selectedPhotoIndex
            val newUris = photoUris.toMutableList()
            while (newUris.size <= index) newUris.add(null)
            newUris[index] = uri
            if (index == newUris.lastIndex && newUris.count { it != null } < 4) {
                newUris.add(null)
                photoTypes = photoTypes + editDefaultPhotoType(photoTypes.size)
            }
            photoUris = newUris
        }
        selectedPhotoIndex = -1
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && selectedPhotoIndex >= 0) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )
            cameraLauncher.launch(uri)
        } else {
            selectedPhotoIndex = -1
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            if (selectedPhotoIndex >= 0) {
                val index = selectedPhotoIndex
                val newUris = photoUris.toMutableList()
                while (newUris.size <= index) newUris.add(null)
                newUris[index] = it
                if (index == newUris.lastIndex && newUris.count { u -> u != null } < 4) {
                    newUris.add(null)
                    if (photoTypes.size < newUris.size) {
                        photoTypes = photoTypes + editDefaultPhotoType(photoTypes.size)
                    }
                }
                photoUris = newUris
            }
        }
        selectedPhotoIndex = -1
    }

    fun launchCamera() {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
        cameraLauncher.launch(uri)
    }

    fun deleteNewPhoto(index: Int) {
        if (index !in photoUris.indices || photoUris[index] == null) return
        val newUris = photoUris.toMutableList()
        newUris.removeAt(index)
        if (newUris.isEmpty()) newUris.add(null)
        else if (newUris.last() != null && newUris.count { it != null } < 4) newUris.add(null)
        val newTypes = photoTypes.toMutableList()
        if (index in newTypes.indices) newTypes.removeAt(index)
        while (newTypes.size < newUris.size) newTypes.add(editDefaultPhotoType(newTypes.size))
        while (newTypes.size > newUris.size) newTypes.removeAt(newTypes.lastIndex)
        photoUris = newUris
        photoTypes = newTypes
    }

    fun selectPhoto(index: Int, source: String) {
        selectedPhotoIndex = index
        when (source) {
            "gallery" -> galleryLauncher.launch("image/*")
            "camera" -> {
                if (hasCameraPermission) launchCamera()
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun copyUriToTempFile(uri: Uri, index: Int): File {
        val out = File(context.cacheDir, "edit_upload_${benchId}_$index.jpg")
        context.contentResolver.openImageInputStreamPreservingMetadata(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: error("Could not read photo")
        return out
    }

    fun performSave() {
        val lat = savedLat
        val lng = savedLng
        if (lat == null || lng == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Location is required") }
            return
        }
        if (inscriptionText.isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Inscription is required")
            }
            return
        }
        isUploading = true
        uploadProgress = 0f
        coroutineScope.launch {
            try {
                val photos = photoUris.mapIndexedNotNull { index, uri ->
                    uri?.let { index to it }
                }
                val tempFiles = withContext(Dispatchers.IO) {
                    photos.map { (index, uri) -> copyUriToTempFile(uri, index) }
                }
                val mediaTypes = photos.map { (index, _) ->
                    photoTypes.getOrNull(index) ?: editDefaultPhotoType(index)
                }
                val existingTypes = existingMedia.associate { media ->
                    media.mediaId to (media.mediaType ?: "bench")
                }
                val result = updateBench(
                    benchId = benchId,
                    inscription = inscriptionText,
                    latitude = lat,
                    longitude = lng,
                    authToken = authToken,
                    existingMediaTypes = existingTypes,
                    photoFiles = tempFiles,
                    photoTypes = mediaTypes,
                    onUploadProgress = { progress ->
                        mainHandler.post { uploadProgress = progress }
                    },
                )
                when (result) {
                    is UploadBenchResult.Success -> uploadSuccess = true
                    is UploadBenchResult.Failure -> showErrorDialog = result.message
                }
            } catch (e: Exception) {
                showErrorDialog = e.message ?: "Update failed"
            } finally {
                isUploading = false
            }
        }
    }

    Scaffold(
        topBar = {
            OpenBenchesTopAppBar(
                title = { Text(text = "Edit Bench #$benchId") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isUploading) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { performSave() },
                        enabled = !isUploading && !loading && loadError == null,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Upload")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = loadError ?: "Error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (existingMedia.isNotEmpty()) {
                            Text(
                                text = "Existing photos",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                itemsIndexed(existingMedia) { _, media ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = when (media.mediaType?.lowercase()) {
                                                "inscription", "plaque" -> "Inscription"
                                                "bench" -> "The Bench"
                                                "view" -> "View"
                                                else -> "Photo"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(120.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp),
                                                ),
                                        ) {
                                            SubcomposeAsyncImage(
                                                model = mediaUrlAbsolute(media.url),
                                                contentDescription = "Existing photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(
                                                        Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Add photos (optional, up to 4)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        val photosToShow = remember(photoBitmaps, photoTypes) {
                            val photos = photoBitmaps.mapIndexed { index, bitmap ->
                                Triple(
                                    index,
                                    bitmap,
                                    photoTypes.getOrNull(index) ?: editDefaultPhotoType(index),
                                )
                            }.toMutableList()
                            val filled = photos.count { it.second != null }
                            if (filled < 4 && (photos.isEmpty() || photos.last().second != null)) {
                                photos.add(
                                    Triple(
                                        photos.size,
                                        null,
                                        editDefaultPhotoType(photos.size),
                                    ),
                                )
                            }
                            photos.take(4)
                        }
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            itemsIndexed(photosToShow) { _, triple ->
                                val (index, bitmap, type) = triple
                                EditNewPhotoSlot(
                                    photoNumber = index + 1,
                                    photoBitmap = bitmap,
                                    photoType = type,
                                    onPhotoTypeChanged = { newType ->
                                        val updated = photoTypes.toMutableList()
                                        while (updated.size <= index) {
                                            updated.add(editDefaultPhotoType(updated.size))
                                        }
                                        updated[index] = newType
                                        photoTypes = updated
                                    },
                                    onSelectGallery = { selectPhoto(index, "gallery") },
                                    onSelectCamera = { selectPhoto(index, "camera") },
                                    onDeletePhoto = { deleteNewPhoto(index) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = inscriptionText,
                            onValueChange = { inscriptionText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(160.dp),
                            label = { Text("Inscription") },
                            placeholder = { Text("Memorial inscription") },
                        )

                        Text(
                            text = "Drag the pin to adjust location",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                        val lat = savedLat
                        val lng = savedLng
                        if (lat != null && lng != null) {
                            Text(
                                text = "%.5f, %.5f".format(lat, lng),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .padding(horizontal = 16.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clip(RoundedCornerShape(8.dp)),
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        Configuration.getInstance().load(
                                            ctx,
                                            ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE),
                                        )
                                        val mapView = MapView(ctx)
                                        mapView.setTileSource(MapTileSources.tileSourceFor(mapProvider))
                                        mapView.setMultiTouchControls(true)
                                        mapView.zoomController.setVisibility(
                                            CustomZoomButtonsController.Visibility.NEVER,
                                        )
                                        mapView.isTilesScaledToDpi = true
                                        val point = GeoPoint(lat, lng)
                                        mapView.controller.setZoom(18.0)
                                        mapView.controller.setCenter(point)
                                        val marker = Marker(mapView)
                                        marker.position = point
                                        marker.title = "Bench #$benchId"
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        marker.isDraggable = true
                                        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                            override fun onMarkerDrag(marker: Marker) {}
                                            override fun onMarkerDragStart(marker: Marker) {}
                                            override fun onMarkerDragEnd(marker: Marker) {
                                                savedLat = marker.position.latitude
                                                savedLng = marker.position.longitude
                                            }
                                        })
                                        try {
                                            val raw = BitmapFactory.decodeResource(
                                                ctx.resources,
                                                ctx.resources.getIdentifier(
                                                    "marker",
                                                    "drawable",
                                                    ctx.packageName,
                                                ),
                                            )
                                            if (raw != null) {
                                                val scaled = Bitmap.createScaledBitmap(raw, 98, 64, true)
                                                marker.icon = BitmapDrawable(ctx.resources, scaled)
                                            }
                                        } catch (_: Exception) {
                                        }
                                        mapView.overlays.add(marker)
                                        mapView
                                    },
                                    update = { mapView ->
                                        val expected = MapTileSources.tileSourceFor(mapProvider)
                                        if (mapView.tileProvider.tileSource.name() != expected.name()) {
                                            mapView.setTileSource(expected)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            if (isUploading) {
                val progressPercent = (uploadProgress * 100f).toInt().coerceIn(0, 100)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(32.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Saving changes… $progressPercent%",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { uploadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (uploadSuccess) {
                Dialog(
                    onDismissRequest = { },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Bench updated",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Your changes to bench #$benchId were saved.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = onSaved) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            showErrorDialog?.let { message ->
                AlertDialog(
                    onDismissRequest = { showErrorDialog = null },
                    title = { Text("Update failed") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { showErrorDialog = null }) {
                            Text("OK")
                        }
                    },
                )
            }
        }
    }
}
