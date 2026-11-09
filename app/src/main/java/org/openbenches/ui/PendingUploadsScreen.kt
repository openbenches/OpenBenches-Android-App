package org.openbenches.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.openbenches.ui.theme.OpenBenchesTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openbenches.data.AuthJwt
import org.openbenches.data.PendingBenchUpload
import org.openbenches.data.PendingBenchUploadStore
import org.openbenches.data.UploadBenchResult
import org.openbenches.data.isGpsExifRelatedUploadFailure
import org.openbenches.data.uploadPendingBench
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingUploadsScreen(
    onBack: () -> Unit,
    onEdit: (PendingBenchUpload) -> Unit,
    authToken: String?,
    authUsername: String? = null,
    onPendingChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { PendingBenchUploadStore(context) }
    var pending by remember { mutableStateOf(store.list()) }
    var uploadingId by remember { mutableStateOf<String?>(null) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isUploadingAll by remember { mutableStateOf(false) }
    var uploadAllIndex by remember { mutableIntStateOf(0) }
    var uploadAllTotal by remember { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var uploadSuccessBenchId by remember { mutableStateOf<String?>(null) }
    var uploadSuccessBulkIds by remember { mutableStateOf<List<String>?>(null) }
    var showGpsFailureDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingSingleUpload by remember { mutableStateOf<PendingBenchUpload?>(null) }
    var showUploadAllConfirm by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val uploadAsUsername = remember(authToken, authUsername) {
        if (authToken.isNullOrBlank()) {
            "Anon"
        } else {
            AuthJwt.displayName(AuthJwt.decodePayloadClaims(authToken), authUsername) ?: "Anon"
        }
    }

    val isBusy = uploadingId != null || isUploadingAll

    fun refresh() {
        pending = store.list()
        onPendingChanged()
    }

    fun dismissUploadSuccessAndMaybeLeave() {
        uploadSuccessBenchId = null
        uploadSuccessBulkIds = null
        if (store.list().isEmpty()) {
            onBack()
        }
    }

    fun handleUploadFailure(result: UploadBenchResult.Failure) {
        if (isGpsExifRelatedUploadFailure(result.message)) {
            showGpsFailureDialog = true
        } else {
            errorMessage = result.message
        }
    }

    suspend fun performUpload(item: PendingBenchUpload): UploadBenchResult {
        uploadingId = item.id
        uploadProgress = 0f
        return uploadPendingBench(
            context = context,
            pending = item,
            authToken = authToken,
            onUploadProgress = { progress ->
                mainHandler.post { uploadProgress = progress }
            },
        )
    }

    fun uploadSingle(item: PendingBenchUpload) {
        coroutineScope.launch {
            errorMessage = null
            when (val result = performUpload(item)) {
                is UploadBenchResult.Success -> {
                    uploadSuccessBenchId = result.benchId
                    refresh()
                }
                is UploadBenchResult.Failure -> handleUploadFailure(result)
            }
            uploadingId = null
            uploadProgress = 0f
        }
    }

    fun uploadAllBenches() {
        coroutineScope.launch {
            isUploadingAll = true
            errorMessage = null
            uploadAllTotal = store.list().size
            uploadAllIndex = 0
            val uploadedIds = mutableListOf<String>()
            try {
                while (true) {
                    val queue = store.list()
                    if (queue.isEmpty()) break
                    uploadAllIndex += 1
                    val item = queue.first()
                    when (val result = performUpload(item)) {
                        is UploadBenchResult.Success -> {
                            uploadedIds.add(result.benchId)
                            refresh()
                        }
                        is UploadBenchResult.Failure -> {
                            handleUploadFailure(result)
                            break
                        }
                    }
                    uploadingId = null
                    uploadProgress = 0f
                }
                if (uploadedIds.isNotEmpty()) {
                    uploadSuccessBulkIds = uploadedIds.toList()
                }
            } finally {
                isUploadingAll = false
                uploadingId = null
                uploadProgress = 0f
                uploadAllIndex = 0
                uploadAllTotal = 0
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LaunchedEffect(uploadSuccessBenchId) {
        val benchId = uploadSuccessBenchId ?: return@LaunchedEffect
        delay(3.seconds)
        if (uploadSuccessBenchId == benchId) {
            dismissUploadSuccessAndMaybeLeave()
        }
    }

    LaunchedEffect(uploadSuccessBulkIds, errorMessage, showGpsFailureDialog) {
        val ids = uploadSuccessBulkIds ?: return@LaunchedEffect
        if (errorMessage != null || showGpsFailureDialog) return@LaunchedEffect
        delay(5.seconds)
        if (uploadSuccessBulkIds == ids) {
            dismissUploadSuccessAndMaybeLeave()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            OpenBenchesTopAppBar(
                title = { Text("Saved benches to upload") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (pending.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No benches saved for upload.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pending.size > 1) {
                    item(key = "upload_all") {
                        Button(
                            onClick = { showUploadAllConfirm = true },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Upload all benches")
                        }
                    }
                }
                items(pending, key = { it.id }) { item ->
                    PendingUploadItem(
                        item = item,
                        isUploading = uploadingId == item.id,
                        actionsEnabled = !isBusy,
                        onEdit = { onEdit(item) },
                        onUpload = { pendingSingleUpload = item },
                        onDelete = {
                            store.delete(item.id)
                            refresh()
                        }
                    )
                }
            }
        }
    }

    pendingSingleUpload?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingSingleUpload = null },
            title = { Text("Upload bench") },
            text = {
                Text("Upload bench with username: $uploadAsUsername")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSingleUpload = null
                        uploadSingle(item)
                    },
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSingleUpload = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showUploadAllConfirm) {
        AlertDialog(
            onDismissRequest = { showUploadAllConfirm = false },
            title = { Text("Upload all benches") },
            text = {
                Text("Upload all saved benches with username: $uploadAsUsername")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUploadAllConfirm = false
                        uploadAllBenches()
                    },
                ) {
                    Text("Upload all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadAllConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Upload failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (showGpsFailureDialog) {
        AlertDialog(
            onDismissRequest = { showGpsFailureDialog = false },
            title = { Text("Upload failed") },
            text = {
                Text(
                    "Upload has failed. Please ensure the first photo contains GPS data, then try again."
                )
            },
            confirmButton = {
                TextButton(onClick = { showGpsFailureDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (uploadingId != null) {
        val progressPercent = (uploadProgress * 100f).toInt().coerceIn(0, 100)
        val statusText = if (isUploadingAll && uploadAllTotal > 0) {
            "Uploading bench $uploadAllIndex of $uploadAllTotal… $progressPercent%"
        } else {
            "Uploading bench… $progressPercent%"
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

    uploadSuccessBenchId?.let { benchId ->
        AlertDialog(
            onDismissRequest = { dismissUploadSuccessAndMaybeLeave() },
            title = { Text("Upload successful") },
            text = { Text("Bench #$benchId was uploaded.") },
            confirmButton = {
                TextButton(onClick = { dismissUploadSuccessAndMaybeLeave() }) {
                    Text("OK")
                }
            }
        )
    }

    // After a partial bulk upload failure, show this once the error dialog is dismissed.
    if (errorMessage == null && !showGpsFailureDialog) {
        uploadSuccessBulkIds?.let { benchIds ->
            val count = benchIds.size
            val numbers = benchIds.joinToString(separator = "\n") { "#$it" }
            AlertDialog(
                onDismissRequest = { dismissUploadSuccessAndMaybeLeave() },
                title = { Text("Upload successful") },
                text = {
                    Text(
                        if (count == 1) {
                            "1 bench was uploaded.\n\n$numbers"
                        } else {
                            "$count benches were uploaded.\n\n$numbers"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { dismissUploadSuccessAndMaybeLeave() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun PendingUploadItem(
    item: PendingBenchUpload,
    isUploading: Boolean,
    actionsEnabled: Boolean,
    onEdit: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
) {
    val savedLabel = remember(item.savedAtMs) {
        val formatter = DateTimeFormatter.ofPattern("yyyy MMM dd HH:mm", Locale.getDefault())
        Instant.ofEpochMilli(item.savedAtMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
    val inscriptionPreview = item.inscription.trim().ifBlank { "(No inscription)" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = inscriptionPreview,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Saved: $savedLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${item.photoFileNames.size} photo(s) · ${"%.5f".format(item.latitude)}, ${"%.5f".format(item.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEdit, enabled = actionsEnabled && !isUploading) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit saved bench"
                    )
                }
                IconButton(onClick = onDelete, enabled = actionsEnabled && !isUploading) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete saved bench"
                    )
                }
                IconButton(onClick = onUpload, enabled = actionsEnabled && !isUploading) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "Upload now"
                    )
                }
            }
        }
    }
}
