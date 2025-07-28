package org.openbenches

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.openbenches.ui.theme.OpenBenchesTheme
import io.sentry.android.core.SentryAndroid
import androidx.compose.material3.Surface
import org.openbenches.ui.MapScreen
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.BottomAppBar
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import android.Manifest
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import org.osmdroid.util.GeoPoint
import org.openbenches.data.fetchBenchesNearby
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import org.openbenches.ui.MapProvider

import org.openbenches.ui.BenchDetailsScreen
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ViewList
import org.openbenches.data.fetchBenchesBySearch
import androidx.compose.material3.OutlinedTextField

@OptIn(
    ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SentryAndroid.init(this) { options ->
            options.dsn = "https://examplePublicKey@o0.ingest.sentry.io/0" // TODO: Replace with your Sentry DSN
        }
        setContent {
            OpenBenchesTheme {
                var recenterMap by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                @OptIn(ExperimentalPermissionsApi::class)
                val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

                var benches by remember { mutableStateOf<List<org.openbenches.data.Bench>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var mapCenter by remember { mutableStateOf(GeoPoint(51.5, -0.1)) }
                var showAboutDialog by remember { mutableStateOf(false) }
                var showTermsDialog by remember { mutableStateOf(false) }
                var showLeaderboardDialog by remember { mutableStateOf(false) }
                var showColophonDialog by remember { mutableStateOf(false) }
                var mapProvider by remember { mutableStateOf(MapProvider.OSM) }
                var showLayerMenu by remember { mutableStateOf(false) }
                var showBenchDetails by remember { mutableStateOf(false) }
                var selectedBenchId by remember { mutableStateOf<Int?>(null) }
                var showSearchDialog by remember { mutableStateOf(false) }
                var searchText by remember { mutableStateOf("") }
                var zoomToFitBenches by remember { mutableStateOf(false) }
                var shouldAnimateToMapCenter by remember { mutableStateOf(false) }

                fun fetchBenchesForCenter(center: GeoPoint) {
                    coroutineScope.launch {
                        loading = true
                        error = null
                        try {
                            benches = fetchBenchesNearby(center.latitude, center.longitude)
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            loading = false
                        }
                    }
                }

                LaunchedEffect(locationPermissionState.status.isGranted) {
                    if (locationPermissionState.status.isGranted) {
                        // Try to get user location for initial center
                        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this@MainActivity)
                        val location = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                            fusedLocationClient.lastLocation.addOnSuccessListener { cont.resume(it, null) }
                            fusedLocationClient.lastLocation.addOnFailureListener { cont.resume(null, null) }
                        }
                        val initialCenter = if (location != null) GeoPoint(location.latitude, location.longitude) else GeoPoint(51.5, -0.1)
                        mapCenter = initialCenter
                        fetchBenchesForCenter(initialCenter)
                    }
                }

                Scaffold(
                    topBar = {
                        val density = LocalDensity.current
                        val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
                        val statusBarHeightDp = with(density) { statusBarHeightPx.toDp() }
                        TopAppBar(
                            title = {},
                            modifier = Modifier.height(if (statusBarHeightDp > 0.dp) statusBarHeightDp else 24.dp)
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            modifier = Modifier.height(28.dp)
                        ) {}
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showBenchDetails && selectedBenchId != null) {
                            BenchDetailsScreen(benchId = selectedBenchId!!, onBack = { lat, lng ->
                                if (lat != null && lng != null) {
                                    mapCenter = GeoPoint(lat, lng)
                                    shouldAnimateToMapCenter = true
                                }
                                showBenchDetails = false
                            })
                        } else if (locationPermissionState.status.isGranted) {
                            MapScreen(
                                modifier = Modifier.systemBarsPadding().padding(innerPadding),
                                recenterOnUser = recenterMap,
                                onRecenterHandled = { recenterMap = false },
                                benches = benches,
                                loading = loading,
                                error = error,
                                mapCenter = mapCenter,
                                setMapCenter = { mapCenter = it },
                                mapProvider = mapProvider,
                                onBenchPopupClick = { benchId ->
                                    selectedBenchId = benchId
                                    showBenchDetails = true
                                },
                                zoomToFitBenches = zoomToFitBenches,
                                onZoomToFitHandled = { zoomToFitBenches = false },
                                shouldAnimateToMapCenter = shouldAnimateToMapCenter,
                                onMapCenterAnimated = { shouldAnimateToMapCenter = false }
                            )
                            // Bottom left: Other FABs
                            Row(
                                modifier = Modifier
                                    .align(BottomStart)
                                    .systemBarsPadding()
                                    .padding(start = 16.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var menuExpanded by remember { mutableStateOf(false) }
                                FloatingActionButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(text = { Text("About") }, onClick = {
                                        menuExpanded = false
                                        showAboutDialog = true
                                    })
                                    DropdownMenuItem(text = { Text("Account Settings") }, onClick = { menuExpanded = false })
                                    DropdownMenuItem(text = { Text("Terms of Service") }, onClick = {
                                        menuExpanded = false
                                        showTermsDialog = true
                                    })
                                    DropdownMenuItem(text = { Text("Leader Board") }, onClick = {
                                        menuExpanded = false
                                        showLeaderboardDialog = true
                                    })
                                    DropdownMenuItem(text = { Text("Colophon") }, onClick = {
                                        menuExpanded = false
                                        showColophonDialog = true
                                    })
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                // New FAB: Search
                                FloatingActionButton(onClick = { showSearchDialog = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                // New FAB: Display All
                                FloatingActionButton(onClick = { /* TODO: Add display all action */ }) {
                                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Display All")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                if (locationPermissionState.status.isGranted) {
                                    FloatingActionButton(onClick = { recenterMap = true }) {
                                        Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location")
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                FloatingActionButton(onClick = { fetchBenchesForCenter(mapCenter) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh benches in this area")
                                }
                            }
                        } else {
                            // Centralized message and permission request button
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "Location Permission Required",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Location permission is required",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "in order to be able to",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Show your position on the map.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(onClick = { locationPermissionState.launchPermissionRequest() }) {
                                    Text("Grant Location Permission")
                                }
                            }
                        }
                        if (showAboutDialog) {
                            val context = LocalContext.current
                            Dialog(onDismissRequest = { showAboutDialog = false }) {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize()) {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    webViewClient = WebViewClient()
                                                    loadUrl("https://www.openbenches.org/blog/about/")
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Row(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                        ) {
                                            Button(
                                                onClick = { showAboutDialog = false },
                                            ) {
                                                Text("Close")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openbenches.org/blog/about/"))
                                                    context.startActivity(intent)
                                                },
                                            ) {
                                                Text("Open in Browser")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (showTermsDialog) {
                            val context = LocalContext.current
                            Dialog(onDismissRequest = { showTermsDialog = false }) {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize()) {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    webViewClient = WebViewClient()
                                                    loadUrl("https://www.openbenches.org/blog/privacy/")
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Row(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                        ) {
                                            Button(
                                                onClick = { showTermsDialog = false },
                                            ) {
                                                Text("Close")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openbenches.org/blog/privacy/"))
                                                    context.startActivity(intent)
                                                },
                                            ) {
                                                Text("Open in Browser")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (showLeaderboardDialog) {
                            val context = LocalContext.current
                            Dialog(onDismissRequest = { showLeaderboardDialog = false }) {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize()) {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    webViewClient = WebViewClient()
                                                    loadUrl("https://www.openbenches.org/leaderboard")
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Row(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                        ) {
                                            Button(
                                                onClick = { showLeaderboardDialog = false },
                                            ) {
                                                Text("Close")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openbenches.org/leaderboard"))
                                                    context.startActivity(intent)
                                                },
                                            ) {
                                                Text("Open in Browser")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (showColophonDialog) {
                            val context = LocalContext.current
                            Dialog(onDismissRequest = { showColophonDialog = false }) {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize()) {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    webViewClient = WebViewClient()
                                                    loadUrl("https://www.openbenches.org/colophon")
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Row(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                        ) {
                                            Button(
                                                onClick = { showColophonDialog = false },
                                            ) {
                                                Text("Close")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openbenches.org/colophon"))
                                                    context.startActivity(intent)
                                                },
                                            ) {
                                                Text("Open in Browser")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Show search dialog
                        if (showSearchDialog) {
                            AlertDialog(
                                onDismissRequest = { showSearchDialog = false },
                                title = { Text("Search Inscriptions") },
                                text = {
                                    OutlinedTextField(
                                        value = searchText,
                                        onValueChange = { searchText = it },
                                        label = { Text("Enter search text") },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        coroutineScope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                benches = fetchBenchesBySearch(searchText)
                                                // Trigger map to zoom to fit all benches
                                                zoomToFitBenches = true
                                            } catch (e: Exception) {
                                                error = e.message
                                            } finally {
                                                loading = false
                                                showSearchDialog = false
                                            }
                                        }
                                    }) {
                                        Text("Search")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { showSearchDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OpenBenchesTheme {
        Greeting("Android")
    }
}