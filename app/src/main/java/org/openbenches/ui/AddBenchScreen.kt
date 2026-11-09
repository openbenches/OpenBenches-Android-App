package org.openbenches.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import org.openbenches.ui.theme.OpenBenchesGoldButton
import org.openbenches.ui.theme.OpenBenchesPlaqueGold
import org.openbenches.ui.theme.OpenBenchesTheme
import org.openbenches.ui.theme.OpenBenchesTopAppBar
import org.openbenches.ui.theme.openBenchesLinkColor
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.openbenches.R
import org.openbenches.util.InscriptionOcr
import org.openbenches.util.rotatePhotoUri90Clockwise
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.openbenches.data.AuthJwt
import org.openbenches.data.PendingBenchUploadStore
import org.openbenches.data.UploadBenchResult
import org.openbenches.data.isGpsExifRelatedUploadFailure
import org.openbenches.data.uploadBench
import androidx.core.net.toUri
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Photo type options
 */
private val photoTypeOptions = listOf(
    "Close-up of the inscription",
    "Long shot of the bench",
    "View from the bench",
    "Panoramic view",
    "VR 360 panoramic",
    "Video of the bench"
)

/**
 * Get default photo type based on photo index (0-based)
 */
private fun getDefaultPhotoType(index: Int): String {
    return when (index) {
        0 -> "Close-up of the inscription"
        1 -> "Long shot of the bench"
        2 -> "View from the bench"
        else -> "Long shot of the bench"
    }
}

/**
 * Opens a picked image without Android 10+ EXIF location redaction when the URI is backed by
 * MediaStore and [Manifest.permission.ACCESS_MEDIA_LOCATION] is granted.
 */
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

private val PhotoUriListSaver = listSaver<List<Uri?>, String>(
    save = { list -> list.map { it?.toString() ?: "" } },
    restore = { strs -> strs.map { s -> if (s.isEmpty()) null else s.toUri() } }
)

private val PhotoTypesListSaver = listSaver<List<String>, String>(
    save = { it.toList() },
    restore = { ArrayList(it) }
)

/**
 * Reusable photo slot component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSlot(
    photoNumber: Int,
    photoBitmap: Bitmap?,
    photoType: String,
    onPhotoTypeChanged: (String) -> Unit,
    onSelectGallery: () -> Unit,
    onSelectCamera: () -> Unit,
    onPhotoClick: ((Int) -> Unit)? = null,
    onDeletePhoto: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        // Photo box (144dp matches BenchDetailsScreen thumbnails)
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (photoBitmap != null) Color.Transparent else Color.Gray.copy(alpha = 0.3f)
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = "Photo $photoNumber",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick?.invoke(photoNumber - 1) },
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
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
        // Dropdown menu for photo type
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .width(144.dp)
                .padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = photoType,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
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
                    disabledIndicatorColor = MaterialTheme.colorScheme.outline
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                photoTypeOptions.forEach { option ->
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
                        }
                    )
                }
            }
        }
    }
}

/**
 * Screen for adding a new bench.
 * Shows photo spaces with gallery/camera selection, GPS location, and inscription input.
 * @param onBack Callback when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBenchScreen(
    onBack: () -> Unit,
    authToken: String? = null,
    authUsername: String? = null,
    /** When set, loads a saved pending bench for editing (replaces on save, deletes on upload). */
    editingPendingId: String? = null,
    onPendingUploadsChanged: () -> Unit = {},
    mapProvider: MapProvider = MapProvider.OSM,
) {
    val context = LocalContext.current
    val uploadAsUsername = remember(authToken, authUsername) {
        if (authToken.isNullOrBlank()) {
            "Anon"
        } else {
            AuthJwt.displayName(AuthJwt.decodePayloadClaims(authToken), authUsername) ?: "Anon"
        }
    }
    var inscriptionText by rememberSaveable { mutableStateOf("") }
    var inscriptionFocused by remember { mutableStateOf(false) }
    var photoUris by rememberSaveable(stateSaver = PhotoUriListSaver) {
        mutableStateOf(listOf<Uri?>(null, null))
    }
    var photoTypes by rememberSaveable(stateSaver = PhotoTypesListSaver) {
        mutableStateOf(
            listOf(
                getDefaultPhotoType(0),
                getDefaultPhotoType(1)
            )
        )
    }
    var photoBitmaps by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var savedLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var savedLng by rememberSaveable { mutableStateOf<Double?>(null) }
    val userLocation = run {
        val lat = savedLat
        val lng = savedLng
        if (lat != null && lng != null) GeoPoint(lat, lng) else null
    }
    var locationLoading by rememberSaveable { mutableStateOf(true) }
    var selectedPhotoIndex by rememberSaveable { mutableIntStateOf(-1) }
    var fullscreenImageIndex by rememberSaveable { mutableIntStateOf(-1) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isSavingForLater by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var uploadSuccessBenchId by remember { mutableStateOf<String?>(null) }
    var showGpsUploadFailureDialog by remember { mutableStateOf(false) }
    var showMissingPhotoGpsDialog by remember { mutableStateOf(false) }
    var showSaveChoiceDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val pendingUploadStore = remember { PendingBenchUploadStore(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val hasStartedBenchDetails = remember(inscriptionText, photoUris) {
        inscriptionText.isNotBlank() || photoUris.any { it != null }
    }

    fun requestExit() {
        if (hasStartedBenchDetails) {
            showDiscardConfirmDialog = true
        } else {
            onBack()
        }
    }

    BackHandler { requestExit() }

    // Warm OCR off the critical path of the first photo — deferred from Application.onCreate.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            InscriptionOcr.ensureEngine(context.applicationContext)
        }
    }

    LaunchedEffect(editingPendingId) {
        val pendingId = editingPendingId ?: return@LaunchedEffect
        val pending = pendingUploadStore.get(pendingId) ?: return@LaunchedEffect
        inscriptionText = pending.inscription
        savedLat = pending.latitude
        savedLng = pending.longitude
        locationLoading = false
        val photoFiles = pendingUploadStore.getPhotoFiles(pending)
        if (photoFiles.isNotEmpty()) {
            val uris = photoFiles.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
            photoUris = uris + listOf(null)
            val loadedTypes = pending.photoTypes.toMutableList()
            while (loadedTypes.size < uris.size) {
                loadedTypes.add(getDefaultPhotoType(loadedTypes.size))
            }
            photoTypes = loadedTypes
        }
    }

    fun updateLocationFromPhotoExif(uri: Uri, photoIndex: Int = -1) {
        coroutineScope.launch {
            val latLng = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openImageInputStreamPreservingMetadata(uri)?.use { stream ->
                        val latLong = ExifInterface(stream).latLong
                        if (latLong != null && latLong.size >= 2) {
                            latLong[0] to latLong[1]
                        } else {
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
            var hasUsableGps = false
            if (latLng != null) {
                val lat = latLng.first
                val lng = latLng.second
                val isValidRange = lat in -90.0..90.0 && lng in -180.0..180.0
                val isZeroPoint = lat == 0.0 && lng == 0.0
                if (isValidRange && !isZeroPoint) {
                    savedLat = lat
                    savedLng = lng
                    locationLoading = false
                    hasUsableGps = true
                }
            }
            // First photo is what the server uses for location — warn early if EXIF GPS is missing.
            if (photoIndex == 0 && !hasUsableGps) {
                showMissingPhotoGpsDialog = true
            }
        }
    }

    fun updateInscriptionFromPhotoText(uri: Uri) {
        coroutineScope.launch {
            val extractedText = withContext(Dispatchers.IO) {
                InscriptionOcr.recognizeInscription(context, uri)
            }
            // Do not overwrite an existing inscription; only fill when empty.
            if (extractedText.isNotEmpty() && inscriptionText.isBlank()) {
                inscriptionText = extractedText
            }
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
    
    // Check for camera permission
    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Persist path across rotation while the camera app is open (remember alone is cleared).
    var pendingCameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun newCameraPhotoFile(): File {
        val file = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
        pendingCameraFilePath = file.absolutePath
        return file
    }

    fun cameraPhotoUri(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedFile = pendingCameraFilePath?.let { File(it) }
        pendingCameraFilePath = null
        if (success && selectedPhotoIndex >= 0 && capturedFile != null && capturedFile.exists()) {
            val uri = cameraPhotoUri(capturedFile)
            val index = selectedPhotoIndex
            val newUris = photoUris.toMutableList()
            while (newUris.size <= index) {
                newUris.add(null)
            }
            newUris[index] = uri
            if (index == newUris.lastIndex) {
                newUris.add(null)
                photoTypes = photoTypes + getDefaultPhotoType(photoTypes.size)
            }
            photoUris = newUris
            updateLocationFromPhotoExif(uri, photoIndex = index)
            updateInscriptionFromPhotoText(uri)
        } else if (capturedFile != null && capturedFile.exists()) {
            capturedFile.delete()
        }
        selectedPhotoIndex = -1
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && selectedPhotoIndex >= 0) {
            cameraLauncher.launch(cameraPhotoUri(newCameraPhotoFile()))
        } else {
            selectedPhotoIndex = -1
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (selectedPhotoIndex >= 0) {
                val index = selectedPhotoIndex
                val newUris = photoUris.toMutableList()
                while (newUris.size <= index) {
                    newUris.add(null)
                }
                newUris[index] = it
                if (index == newUris.lastIndex) {
                    newUris.add(null)
                }
                photoUris = newUris
                updateLocationFromPhotoExif(it, photoIndex = index)
                updateInscriptionFromPhotoText(it)
            }
        }
        selectedPhotoIndex = -1
    }

    val mediaLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (selectedPhotoIndex < 0) return@rememberLauncherForActivityResult
        galleryLauncher.launch("image/*")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Allow photo location access to read GPS from gallery picks (Files / Photos)."
                )
            }
        }
    }
    
    // Function to launch camera
    fun launchCamera() {
        cameraLauncher.launch(cameraPhotoUri(newCameraPhotoFile()))
    }

    fun rotatePhotoAt(index: Int) {
        val uri = photoUris.getOrNull(index) ?: return
        coroutineScope.launch {
            val rotatedUri = rotatePhotoUri90Clockwise(context, uri) ?: run {
                snackbarHostState.showSnackbar("Could not rotate photo.")
                return@launch
            }
            val newUris = photoUris.toMutableList()
            if (index in newUris.indices) {
                newUris[index] = rotatedUri
                photoUris = newUris
            }
        }
    }

    fun deletePhoto(index: Int) {
        if (index !in photoUris.indices || photoUris[index] == null) return

        val newUris = photoUris.toMutableList()
        newUris.removeAt(index)
        if (newUris.isEmpty()) {
            newUris.add(null)
        } else if (newUris.last() != null) {
            newUris.add(null)
        }

        val newTypes = photoTypes.toMutableList()
        if (index in newTypes.indices) {
            newTypes.removeAt(index)
        }
        while (newTypes.size < newUris.size) {
            newTypes.add(getDefaultPhotoType(newTypes.size))
        }
        while (newTypes.size > newUris.size) {
            newTypes.removeAt(newTypes.lastIndex)
        }

        photoUris = newUris
        photoTypes = newTypes
        when {
            fullscreenImageIndex == index -> fullscreenImageIndex = -1
            fullscreenImageIndex > index -> fullscreenImageIndex -= 1
        }
    }
    
    // Function to handle photo selection
    fun selectPhoto(index: Int, source: String) {
        selectedPhotoIndex = index
        when (source) {
            "gallery" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val hasMediaLocation = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_MEDIA_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMediaLocation) {
                        galleryLauncher.launch("image/*")
                    } else {
                        mediaLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }
                } else {
                    galleryLauncher.launch("image/*")
                }
            }
            "camera" -> {
                if (hasCameraPermission) {
                    launchCamera()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
    
    // Get user location (skip when editing a saved bench — coords load from pending store)
    LaunchedEffect(Unit) {
        if (editingPendingId != null) {
            return@LaunchedEffect
        }
        if (savedLat != null && savedLng != null) {
            locationLoading = false
            return@LaunchedEffect
        }
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) {
            val fusedLocationClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val location = suspendCancellableCoroutine { cont ->
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { cont.resume(it, null) }
                    fusedLocationClient.lastLocation.addOnFailureListener { cont.resume(null, null) }
                } catch (_: SecurityException) {
                    cont.resume(null, null)
                }
            }
            if (location != null) {
                savedLat = location.latitude
                savedLng = location.longitude
            }
        }
        locationLoading = false
    }

    fun validateForSave(): List<Pair<Int, Uri>>? {
        if (userLocation == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location is required")
            }
            return null
        }
        val photosToUpload = photoUris.mapIndexedNotNull { index, uri ->
            uri?.let { index to it }
        }
        if (photosToUpload.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("At least one photo is required")
            }
            return null
        }
        return photosToUpload
    }

    fun performUpload(photosToUpload: List<Pair<Int, Uri>>) {
        val location = userLocation ?: return
        isUploading = true
        uploadProgress = 0f
        coroutineScope.launch {
            try {
                val tempFiles = photosToUpload.map { (index, uri) ->
                    val file = File(context.cacheDir, "upload_photo_$index.jpg")
                    context.contentResolver.openImageInputStreamPreservingMetadata(uri)
                        ?.use { input ->
                            FileOutputStream(file).use { out ->
                                input.copyTo(out)
                            }
                        } ?: throw IllegalStateException("Could not open selected photo for upload.")
                    file
                }

                val mediaTypes = photosToUpload.map { (index, _) ->
                    photoTypes.getOrNull(index) ?: getDefaultPhotoType(index)
                }

                val result = uploadBench(
                    inscription = inscriptionText,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    photoFiles = tempFiles,
                    photoTypes = mediaTypes,
                    authToken = authToken,
                    onUploadProgress = { progress ->
                        mainHandler.post { uploadProgress = progress }
                    },
                )

                when (result) {
                    is UploadBenchResult.Success -> {
                        editingPendingId?.let { pendingUploadStore.delete(it) }
                        onPendingUploadsChanged()
                        uploadSuccessBenchId = result.benchId
                        delay(3.seconds)
                        uploadSuccessBenchId = null
                        onBack()
                    }
                    is UploadBenchResult.Failure -> {
                        if (isGpsExifRelatedUploadFailure(result.message)) {
                            showGpsUploadFailureDialog = true
                        } else {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
                tempFiles.forEach { it.delete() }
            } catch (e: Exception) {
                val err = e.message ?: "Unknown error"
                if (isGpsExifRelatedUploadFailure(err)) {
                    showGpsUploadFailureDialog = true
                } else {
                    snackbarHostState.showSnackbar("Error: $err")
                }
                e.printStackTrace()
            } finally {
                isUploading = false
                uploadProgress = 0f
            }
        }
    }

    fun saveForUploadLater(photosToUpload: List<Pair<Int, Uri>>) {
        val location = userLocation ?: return
        coroutineScope.launch {
            isSavingForLater = true
            try {
                val result = if (editingPendingId != null) {
                    pendingUploadStore.replace(
                        id = editingPendingId,
                        inscription = inscriptionText,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        photos = photosToUpload,
                        photoTypes = photoTypes,
                    )
                } else {
                    pendingUploadStore.save(
                        inscription = inscriptionText,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        photos = photosToUpload,
                        photoTypes = photoTypes,
                    )
                }
                result.fold(
                    onSuccess = {
                        onPendingUploadsChanged()
                        snackbarHostState.showSnackbar(
                            if (editingPendingId != null) {
                                "Saved bench updated"
                            } else {
                                "Bench saved for upload later"
                            }
                        )
                        onBack()
                    },
                    onFailure = { error ->
                        snackbarHostState.showSnackbar(
                            error.message ?: "Could not save bench for later upload"
                        )
                    }
                )
            } finally {
                isSavingForLater = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            OpenBenchesTopAppBar(
                title = {
                    Text(text = if (editingPendingId != null) "Edit bench" else "Add Bench")
                },
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (validateForSave() != null) {
                                showSaveChoiceDialog = true
                            }
                        },
                        enabled = !isUploading && !isSavingForLater,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 6.dp,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Upload")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Photo section - scrollable horizontal row
            // Always show all photos plus one empty box at the end
            val photosToShow = remember(photoBitmaps, photoTypes) {
                val photos = photoBitmaps.mapIndexed { index, bitmap -> 
                    Triple(index, bitmap, photoTypes.getOrNull(index) ?: getDefaultPhotoType(index))
                }.toMutableList()
                // Ensure there's always at least one empty box
                if (photos.isEmpty() || photos.last().second != null) {
                    val nextIndex = photos.size
                    photos.add(Triple(nextIndex, null, getDefaultPhotoType(nextIndex)))
                }
                photos
            }
            
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(photosToShow) { _, photoTriple ->
                    val (index, photoBitmap, photoType) = photoTriple
                    PhotoSlot(
                        photoNumber = index + 1,
                        photoBitmap = photoBitmap,
                        photoType = photoType,
                        onPhotoTypeChanged = { newType ->
                            val updatedTypes = photoTypes.toMutableList()
                            while (updatedTypes.size <= index) {
                                updatedTypes.add(getDefaultPhotoType(updatedTypes.size))
                            }
                            updatedTypes[index] = newType
                            photoTypes = updatedTypes
                        },
                        onSelectGallery = { selectPhoto(index, "gallery") },
                        onSelectCamera = { selectPhoto(index, "camera") },
                        onPhotoClick = { clickedIndex ->
                            fullscreenImageIndex = clickedIndex
                        },
                        onDeletePhoto = { deletePhoto(index) },
                    )
                }
            }
            
            // Fullscreen image dialog
            // Map all photos with bitmaps, keeping track of original indices
            val photosWithBitmaps = photoBitmaps.mapIndexedNotNull { index, bitmap -> 
                bitmap?.let { index to it }
            }
            // Find the actual index in the filtered list when a photo is clicked
            if (fullscreenImageIndex >= 0) {
                val actualIndex = photosWithBitmaps.indexOfFirst { it.first == fullscreenImageIndex }
                if (actualIndex >= 0 && actualIndex < photosWithBitmaps.size) {
                    FullscreenImageDialog(
                        photoBitmaps = photosWithBitmaps.map { it.second },
                        currentIndex = actualIndex,
                        onIndexChanged = { newIndex ->
                            if (newIndex >= 0 && newIndex < photosWithBitmaps.size) {
                                fullscreenImageIndex = photosWithBitmaps[newIndex].first
                            }
                        },
                        onDismiss = { fullscreenImageIndex = -1 },
                        onRotateClockwise = { rotatePhotoAt(fullscreenImageIndex) },
                        onRegionTextRecognized = { text ->
                            if (text.isNotBlank()) {
                                inscriptionText = if (inscriptionText.isBlank()) {
                                    text
                                } else {
                                    inscriptionText.trimEnd() + "\n" + text
                                }
                            }
                        },
                    )
                }
            }
            
            // Content section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Inscription input (above map, matching BenchDetailsScreen style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                ) {
                    OutlinedTextField(
                        value = inscriptionText,
                        onValueChange = { inscriptionText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .onFocusChanged { inscriptionFocused = it.isFocused },
                        placeholder = {
                            if (!inscriptionFocused) {
                                Text(
                                    "Enter inscription text...",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                        minLines = 3,
                        maxLines = 10,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    if (inscriptionText.isNotEmpty()) {
                        IconButton(
                            onClick = { inscriptionText = "" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear inscription",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                
                // Map showing location
                val mapLocation = userLocation
                if (mapLocation != null) {
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
                        AndroidView(
                            factory = { ctx ->
                                Configuration.getInstance().load(
                                    ctx,
                                    ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                                )
                                
                                val mapView = MapView(ctx)
                                mapView.setTileSource(MapTileSources.tileSourceFor(mapProvider))
                                mapView.setMultiTouchControls(true)
                                mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                mapView.isTilesScaledToDpi = true
                                
                                mapView.controller.setZoom(18.0)
                                mapView.controller.setCenter(mapLocation)
                                
                                // Add location marker
                                val marker = Marker(mapView)
                                marker.position = mapLocation
                                marker.title = "Bench Location"
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                
                                // Load marker icon
                                try {
                                    val markerBitmapRaw = BitmapFactory.decodeResource(
                                        ctx.resources,
                                        R.drawable.marker,
                                    )
                                    if (markerBitmapRaw != null) {
                                        marker.icon = markerBitmapRaw.scale(98, 64).toDrawable(ctx.resources)
                                    }
                                } catch (_: Exception) {
                                    // Use default marker if custom marker fails to load
                                }
                                
                                mapView.overlays.add(marker)
                                mapView
                            },
                            update = { mapView ->
                                val expectedTileSource = MapTileSources.tileSourceFor(mapProvider)
                                if (mapView.tileProvider.tileSource.name() != expectedTileSource.name()) {
                                    mapView.setTileSource(expectedTileSource)
                                }

                                mapView.controller.setCenter(mapLocation)
                                val marker = mapView.overlays.filterIsInstance<Marker>().firstOrNull()
                                    ?: Marker(mapView).apply {
                                        title = "Bench Location"
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        mapView.overlays.add(this)
                                    }
                                marker.position = mapLocation
                                mapView.invalidate()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (locationLoading) {
                    Text(
                        text = "Loading location...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Text(
                        text = "Location not available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                BenchUploadLegalNotice(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                )
            }
        }
        uploadSuccessBenchId?.let { benchId ->
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Upload successful",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bench ID: $benchId",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        if (isUploading) {
            val progressPercent = (uploadProgress * 100f).toInt().coerceIn(0, 100)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Uploading bench… $progressPercent%",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
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

        if (isSavingForLater) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Saving bench for later upload",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (showSaveChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showSaveChoiceDialog = false },
                title = { Text("Save bench") },
                text = {
                    Text(
                        "Would you like to upload now or save to upload later?\n" +
                            "Uploads will use username: $uploadAsUsername",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSaveChoiceDialog = false
                            validateForSave()?.let { performUpload(it) }
                        }
                    ) {
                        Text("Upload now")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSaveChoiceDialog = false
                            validateForSave()?.let { saveForUploadLater(it) }
                        }
                    ) {
                        Text("Save to upload later")
                    }
                }
            )
        }

        if (showDiscardConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = false },
                title = { Text("Discard bench?") },
                text = {
                    Text(
                        "The current bench details will be lost. Are you sure you want to exit?",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardConfirmDialog = false
                            onBack()
                        },
                    ) {
                        Text("Exit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showGpsUploadFailureDialog) {
            AlertDialog(
                onDismissRequest = { showGpsUploadFailureDialog = false },
                title = { Text("Upload failed") },
                text = {
                    Text(
                        "Upload has failed. Please ensure the first photo contains GPS data, then try again."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGpsUploadFailureDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showMissingPhotoGpsDialog) {
            AlertDialog(
                onDismissRequest = { showMissingPhotoGpsDialog = false },
                title = { Text("GPS data required") },
                text = {
                    Text(
                        "The provided photo do not contain GPS data which is required to locate the bench. " +
                            "Please ensure your camera has location permissions",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showMissingPhotoGpsDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
        }
    }
}

/**
 * Dialog for displaying a fullscreen image with swipe and arrow navigation.
 * Includes optional region selection for OCR of a plaque area.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenImageDialog(
    photoBitmaps: List<Bitmap>,
    currentIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRotateClockwise: () -> Unit = {},
    onRegionTextRecognized: (String) -> Unit = {},
) {
    if (currentIndex !in photoBitmaps.indices) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { photoBitmaps.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val zoomState = rememberFullscreenZoomState()
    var selectingRegion by remember { mutableStateOf(false) }
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isRecognizingRegion by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun clearSelection() {
        selectionStart = null
        selectionEnd = null
    }

    fun exitSelectionMode() {
        selectingRegion = false
        clearSelection()
        isRecognizingRegion = false
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(2200.milliseconds)
            statusMessage = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        zoomState.reset()
        if (selectingRegion) {
            exitSelectionMode()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (currentIndex != pagerState.currentPage) {
            onIndexChanged(pagerState.currentPage)
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex in photoBitmaps.indices && currentIndex != pagerState.currentPage) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    val selectionRect = remember(selectionStart, selectionEnd) {
        val start = selectionStart
        val end = selectionEnd
        if (start == null || end == null) null else Rect(
            left = minOf(start.x, end.x),
            top = minOf(start.y, end.y),
            right = maxOf(start.x, end.x),
            bottom = maxOf(start.y, end.y),
        )
    }
    val hasUsableSelection = selectionRect?.let { it.width >= 24f && it.height >= 24f } == true

    Dialog(
        onDismissRequest = {
            if (selectingRegion) exitSelectionMode() else onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        OpenBenchesTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(
                    if (!selectingRegion) Modifier.fullscreenDoubleTapZoom(zoomState)
                    else Modifier
                )
                .onSizeChanged { viewportSize = it },
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .then(
                        if (!selectingRegion) {
                            Modifier.fullscreenPagerSwipe(pagerState, coroutineScope, zoomState)
                        } else {
                            Modifier
                        }
                    ),
            ) { pagerPage ->
                Image(
                    bitmap = photoBitmaps[pagerPage].asImageBitmap(),
                    contentDescription = "Full screen bench photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .fullscreenZoomGraphics(zoomState)
                        .then(
                            if (!selectingRegion) Modifier.fullscreenPhotoGestures(zoomState)
                            else Modifier
                        ),
                )
            }

            if (selectingRegion) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .pointerInput(isRecognizingRegion) {
                            if (isRecognizingRegion) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    selectionStart = offset
                                    selectionEnd = offset
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    selectionEnd = change.position
                                },
                            )
                        },
                ) {
                    val dim = Color.Black.copy(alpha = 0.55f)
                    if (selectionRect != null && selectionRect.width > 0f && selectionRect.height > 0f) {
                        // Dim the four bands around the selection (keeps the crop area clear).
                        drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, selectionRect.top))
                        drawRect(
                            dim,
                            topLeft = Offset(0f, selectionRect.bottom),
                            size = Size(size.width, size.height - selectionRect.bottom),
                        )
                        drawRect(
                            dim,
                            topLeft = Offset(0f, selectionRect.top),
                            size = Size(selectionRect.left, selectionRect.height),
                        )
                        drawRect(
                            dim,
                            topLeft = Offset(selectionRect.right, selectionRect.top),
                            size = Size(size.width - selectionRect.right, selectionRect.height),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(selectionRect.left, selectionRect.top),
                            size = Size(selectionRect.width, selectionRect.height),
                            style = Stroke(width = 3f),
                        )
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.35f))
                    }
                }
            }

            // Controls above the photo/pager. Keep primary actions at the TOP —
            // bottom placement was getting clipped under the system gesture/nav bar
            // inside this fullscreen Dialog on some devices.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Left / right arrows (hidden while selecting a region)
                if (!selectingRegion && page > 0) {
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
                if (!selectingRegion && page < photoBitmaps.size - 1) {
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

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isRecognizingRegion) {
                            CircularProgressIndicator(
                                color = OpenBenchesPlaqueGold,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                            )
                        } else if (!selectingRegion) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OpenBenchesGoldButton(
                                    onClick = {
                                        zoomState.reset()
                                        onRotateClockwise()
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Rotate")
                                }
                                OpenBenchesGoldButton(
                                    onClick = {
                                        zoomState.reset()
                                        clearSelection()
                                        selectingRegion = true
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Crop,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select area to read")
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { exitSelectionMode() }) {
                                    Text("Cancel", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        val rect = selectionRect ?: return@Button
                                        val bitmap = photoBitmaps.getOrNull(page) ?: return@Button
                                        if (viewportSize.width <= 0 || viewportSize.height <= 0) {
                                            return@Button
                                        }
                                        val crop = cropBitmapForContentScaleFit(
                                            source = bitmap,
                                            viewWidth = viewportSize.width,
                                            viewHeight = viewportSize.height,
                                            selection = rect,
                                        )
                                        if (crop == null) return@Button
                                        isRecognizingRegion = true
                                        coroutineScope.launch {
                                            val text = try {
                                                withContext(Dispatchers.IO) {
                                                    InscriptionOcr.recognizeFromBitmap(context, crop)
                                                }
                                            } finally {
                                                if (!crop.isRecycled) crop.recycle()
                                            }
                                            isRecognizingRegion = false
                                            exitSelectionMode()
                                            statusMessage = if (text.isBlank()) {
                                                "No text found in that area"
                                            } else {
                                                "Added to inscription"
                                            }
                                            onRegionTextRecognized(text)
                                        }
                                    },
                                    enabled = hasUsableSelection,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OpenBenchesPlaqueGold,
                                        contentColor = Color.Black,
                                        disabledContainerColor = OpenBenchesPlaqueGold.copy(alpha = 0.4f),
                                        disabledContentColor = Color.Black.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use selection")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (selectingRegion) exitSelectionMode() else onDismiss()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = if (selectingRegion) {
                                    "Cancel selection"
                                } else {
                                    "Close"
                                },
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    if (selectingRegion && !isRecognizingRegion) {
                        Text(
                            text = if (hasUsableSelection) {
                                "Adjust the box, then tap Use selection"
                            } else {
                                "Drag on the photo to select text"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                statusMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(OpenBenchesPlaqueGold, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
        }
    }
}

/**
 * Maps a selection rectangle in a [ContentScale.Fit] viewport onto [source] bitmap pixels.
 */
private fun cropBitmapForContentScaleFit(
    source: Bitmap,
    viewWidth: Int,
    viewHeight: Int,
    selection: Rect,
): Bitmap? {
    if (viewWidth <= 0 || viewHeight <= 0 || source.width <= 0 || source.height <= 0) return null
    val viewW = viewWidth.toFloat()
    val viewH = viewHeight.toFloat()
    val bmpW = source.width.toFloat()
    val bmpH = source.height.toFloat()
    val scale = minOf(viewW / bmpW, viewH / bmpH)
    val displayedW = bmpW * scale
    val displayedH = bmpH * scale
    val offsetX = (viewW - displayedW) / 2f
    val offsetY = (viewH - displayedH) / 2f

    val leftView = selection.left.coerceIn(offsetX, offsetX + displayedW)
    val topView = selection.top.coerceIn(offsetY, offsetY + displayedH)
    val rightView = selection.right.coerceIn(offsetX, offsetX + displayedW)
    val bottomView = selection.bottom.coerceIn(offsetY, offsetY + displayedH)
    if (rightView - leftView < 8f || bottomView - topView < 8f) return null

    val left = ((leftView - offsetX) / scale).toInt().coerceIn(0, source.width - 1)
    val top = ((topView - offsetY) / scale).toInt().coerceIn(0, source.height - 1)
    val right = ((rightView - offsetX) / scale).toInt().coerceIn(left + 1, source.width)
    val bottom = ((bottomView - offsetY) / scale).toInt().coerceIn(top + 1, source.height)
    val width = right - left
    val height = bottom - top
    if (width < 8 || height < 8) return null
    return try {
        Bitmap.createBitmap(source, left, top, width, height)
    } catch (_: Exception) {
        null
    }
}

private const val OPENBENCHES_PRIVACY_POLICY_URL = "https://www.openbenches.org/blog/privacy/"

/**
 * Copyright and privacy notice shown on [AddBenchScreen], matching [openbenches.org/add](https://openbenches.org/add).
 */
@Composable
private fun BenchUploadLegalNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        // Neutral panel (not sky-blue) so body text and links keep WCAG AA contrast.
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "By adding a bench, you agree that you own the copyright of the photo and that you are " +
                    "making it freely available under the Creative Commons Attribution-ShareAlike 4.0 International " +
                    "(CC BY-SA 4.0) license. This means other people can use the photo and its data without having " +
                    "to ask permission, but they will have to give appropriate credit. Thanks!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val privacyText = buildAnnotatedString {
                append("See our ")
                pushStringAnnotation(tag = "privacy", annotation = OPENBENCHES_PRIVACY_POLICY_URL)
                withStyle(
                    SpanStyle(
                        color = openBenchesLinkColor(),
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold,
                    )
                ) {
                    append("privacy policy")
                }
                pop()
                append(" to understand how your photo's data is used.")
            }

            ClickableText(
                text = privacyText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                onClick = { offset ->
                    privacyText.getStringAnnotations(tag = "privacy", start = offset, end = offset)
                        .firstOrNull()
                        ?.let { annotation ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            )
                        }
                },
            )
        }
    }
}

