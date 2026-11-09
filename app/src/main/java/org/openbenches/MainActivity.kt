package org.openbenches

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import org.openbenches.ui.theme.OpenBenchesBlack
import org.openbenches.ui.theme.OpenBenchesCream
import org.openbenches.ui.theme.OpenBenchesDarkBackground
import org.openbenches.ui.theme.OpenBenchesSkyBlue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import org.openbenches.ui.theme.OpenBenchesTheme
import org.openbenches.ui.theme.openBenchesLinkColor
import androidx.compose.material3.Surface
import org.openbenches.ui.MapScreen
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import android.Manifest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import org.osmdroid.util.GeoPoint
import org.openbenches.data.AuthJwt
import org.openbenches.data.fetchBenchesNearby
import org.openbenches.data.fetchGlobalBenchCount
import org.openbenches.data.GlobalBenchCache
import org.openbenches.data.refreshGlobalBenchesCache
import org.openbenches.util.isNetworkAvailable
import org.openbenches.util.rememberDeviceOffline
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import org.openbenches.ui.theme.OpenBenchesHeaderBanner
import org.openbenches.ui.theme.OpenBenchesHeaderShape
import org.openbenches.ui.theme.OpenBenchesMapLayerFab
import org.openbenches.ui.theme.OpenBenchesMapFab
import org.openbenches.ui.theme.OpenBenchesOfflineMapChip
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import org.openbenches.ui.MapProvider

import org.openbenches.ui.BenchDetailsScreen
import org.openbenches.ui.LeaderboardBenchesScreen
import org.openbenches.ui.LoginScreen
import org.openbenches.ui.warmCustomTabs
import org.openbenches.ui.AddBenchScreen
import org.openbenches.ui.EditBenchScreen
import org.openbenches.ui.AccountDetailsScreen
import org.openbenches.ui.PendingUploadsScreen
import org.openbenches.ui.TermsOfServiceScreen
import org.openbenches.ui.SupportOpenBenchesScreen
import org.openbenches.ui.ColophonScreen
import org.openbenches.data.PendingBenchUploadStore
import org.openbenches.data.TodoBench
import org.openbenches.data.TodoBenchStore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Upload
import org.openbenches.data.fetchBenchesBySearch
import org.openbenches.data.fetchBenchesForUser
import org.openbenches.data.fetchLatestBenches
import androidx.compose.material3.OutlinedTextField
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mapPrefs = remember {
                getSharedPreferences("app_settings", MODE_PRIVATE)
            }
            var darkMode by remember {
                mutableStateOf(mapPrefs.getBoolean("dark_mode", false))
            }
            fun setDarkMode(enabled: Boolean) {
                darkMode = enabled
                mapPrefs.edit { putBoolean("dark_mode", enabled) }
            }
            SideEffect {
                if (darkMode) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(
                            OpenBenchesDarkBackground.toArgb(),
                        ),
                        navigationBarStyle = SystemBarStyle.dark(
                            OpenBenchesDarkBackground.toArgb(),
                        ),
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            OpenBenchesSkyBlue.toArgb(),
                            OpenBenchesSkyBlue.toArgb(),
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            OpenBenchesCream.toArgb(),
                            OpenBenchesCream.toArgb(),
                        ),
                    )
                }
            }
            OpenBenchesTheme(darkTheme = darkMode) {
                var recenterRequest by remember { mutableIntStateOf(0) }
                var refreshLocation by remember { mutableStateOf(false) }
                val nearbyBenchRadiusKm = 10
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val auth0Client = remember {
                    Auth0(
                        BuildConfig.AUTH0_CLIENT_ID,
                        BuildConfig.AUTH0_DOMAIN
                    )
                }
                val authPrefs = remember {
                    this@MainActivity.getSharedPreferences("auth_session", MODE_PRIVATE)
                }
                @OptIn(ExperimentalPermissionsApi::class)
                val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

                var benches by remember { mutableStateOf<List<org.openbenches.data.Bench>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var mapCenter by remember { mutableStateOf<GeoPoint?>(null) }
                var hasCenteredMapOnUser by remember { mutableStateOf(false) }
                var showAboutDialog by remember { mutableStateOf(false) }
                var showTermsScreen by remember { mutableStateOf(false) }
                var showSupportScreen by remember { mutableStateOf(false) }
                var showLeaderboardBenches by remember { mutableStateOf(false) }
                var showColophonScreen by remember { mutableStateOf(false) }
                var showLoginScreen by remember { mutableStateOf(false) }
                var showAccountDetails by remember { mutableStateOf(false) }
                var showPendingUploads by remember { mutableStateOf(false) }
                val pendingUploadStore = remember { PendingBenchUploadStore(this@MainActivity) }
                var pendingUploadCount by remember { mutableIntStateOf(pendingUploadStore.count()) }
                val todoBenchStore = remember { TodoBenchStore(this@MainActivity) }
                var todoBenches by remember { mutableStateOf(todoBenchStore.list()) }
                var todoPendingDelete by remember { mutableStateOf<TodoBench?>(null) }
                /** When true, [searchResultBenches] is the To-Do list shown like a search filter. */
                var todoListFilterActive by remember { mutableStateOf(false) }

                fun refreshPendingUploadCount() {
                    pendingUploadCount = pendingUploadStore.count()
                }

                // Filled after searchResultBenches exists so refresh can update the map filter.
                val syncTodoListMapFilter = remember { arrayOf({}) }
                fun refreshTodoBenches() {
                    todoBenches = todoBenchStore.list()
                    syncTodoListMapFilter[0]()
                }
                var showClearTodoConfirm by remember { mutableStateOf(false) }
                val downloadTodoListLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                contentResolver.openOutputStream(uri)?.use { out ->
                                    out.write(todoBenchStore.exportJson().toByteArray(Charsets.UTF_8))
                                } != null
                            } catch (_: Exception) {
                                false
                            }
                        }
                        snackbarHostState.showSnackbar(
                            if (ok) "To-Do list downloaded."
                            else "Could not download To-Do list.",
                        )
                    }
                }
                val uploadTodoListLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    coroutineScope.launch {
                        try {
                            val added = withContext(Dispatchers.IO) {
                                val json = contentResolver.openInputStream(uri)
                                    ?.bufferedReader()
                                    ?.use { it.readText() }
                                    ?: throw IllegalArgumentException("Could not read file.")
                                todoBenchStore.importAndAppend(json)
                            }
                            refreshTodoBenches()
                            snackbarHostState.showSnackbar(
                                when (added) {
                                    0 -> "No new To-Do items to add."
                                    1 -> "Added 1 To-Do item."
                                    else -> "Added $added To-Do items."
                                },
                            )
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(
                                e.message ?: "Could not upload To-Do list.",
                            )
                        }
                    }
                }
                var mapProvider by remember {
                    mutableStateOf(MapProvider.fromStoredValue(mapPrefs.getString("map_provider", null)))
                }

                fun setMapProvider(provider: MapProvider) {
                    mapProvider = provider
                    mapPrefs.edit { putString("map_provider", provider.name) }
                }
                var showBenchDetails by rememberSaveable { mutableStateOf(false) }
                var selectedBenchId by rememberSaveable { mutableIntStateOf(-1) }
                var showEditBenchScreen by rememberSaveable { mutableStateOf(false) }
                var benchDetailsRefreshKey by rememberSaveable { mutableIntStateOf(0) }
                var showSearchDialog by remember { mutableStateOf(false) }
                var showOfflineBenchDialog by remember { mutableStateOf(false) }
                var searchText by remember { mutableStateOf("") }
                var searchResultBenches by remember { mutableStateOf<List<org.openbenches.data.Bench>?>(null) }
                var zoomToFitBenches by remember { mutableStateOf(false) }
                var mapZoomLevel by remember { mutableDoubleStateOf(11.0) }
                var mapFocusRequest by remember { mutableIntStateOf(0) }
                var showAddBenchScreen by rememberSaveable { mutableStateOf(false) }
                var editingPendingId by remember { mutableStateOf<String?>(null) }
                var authToken by rememberSaveable { mutableStateOf<String?>(null) }
                var authUsername by rememberSaveable { mutableStateOf<String?>(null) }
                var authProvider by rememberSaveable { mutableStateOf<String?>(null) }
                var authPictureUrl by rememberSaveable { mutableStateOf<String?>(null) }
                var authExpiresAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
                var globalBenchCount by remember { mutableStateOf<Int?>(null) }
                var globalBenchCountFailed by remember { mutableStateOf(false) }
                var globalBenches by remember { mutableStateOf<List<org.openbenches.data.Bench>?>(null) }
                var globalBenchesLoading by remember { mutableStateOf(false) }
                var globalBenchesReady by remember { mutableStateOf(false) }
                var usingOfflineBenchData by remember { mutableStateOf(false) }
                var visibleBenchCount by remember { mutableIntStateOf(0) }
                val isDeviceOffline = rememberDeviceOffline()

                fun fetchBenchesForCenter(center: GeoPoint) {
                    if (!globalBenches.isNullOrEmpty()) return
                    if (!isNetworkAvailable(this@MainActivity)) {
                        loading = false
                        error = null
                        return
                    }
                    coroutineScope.launch {
                        loading = true
                        error = null
                        try {
                            benches = fetchBenchesNearby(center.latitude, center.longitude, nearbyBenchRadiusKm)
                        } catch (e: Exception) {
                            if (globalBenches.isNullOrEmpty()) {
                                error = e.message
                            }
                        } finally {
                            loading = false
                        }
                    }
                }

                fun clearMapSearchFilter() {
                    searchResultBenches = null
                    todoListFilterActive = false
                    mapCenter?.let { fetchBenchesForCenter(it) }
                }

                fun showTodoListOnMap() {
                    val todos = todoBenchStore.list()
                    todoBenches = todos
                    if (todos.isEmpty()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("To-Do list is empty.")
                        }
                        return
                    }
                    // Empty non-null search filter hides bench icons; To-Do markers stay as usual.
                    searchResultBenches = emptyList()
                    todoListFilterActive = true
                    zoomToFitBenches = true
                }

                syncTodoListMapFilter[0] = {
                    if (todoListFilterActive) {
                        if (todoBenches.isEmpty()) {
                            clearMapSearchFilter()
                        } else {
                            searchResultBenches = emptyList()
                        }
                    }
                }

                LaunchedEffect(showAddBenchScreen, showPendingUploads) {
                    refreshPendingUploadCount()
                }

                LaunchedEffect(Unit) {
                    globalBenchesLoading = true
                    val online = isNetworkAvailable(this@MainActivity)
                    var globalRefreshSucceeded = false
                    try {
                        val cached = GlobalBenchCache(this@MainActivity).load()
                        if (!cached.isNullOrEmpty()) {
                            globalBenches = cached
                            globalBenchCount = cached.size
                            if (!online) {
                                usingOfflineBenchData = true
                            }
                        }
                        if (online) {
                            try {
                                globalBenchCount = fetchGlobalBenchCount()
                            } catch (_: Exception) {
                                globalBenchCountFailed = globalBenchCount == null
                            }
                            try {
                                val fresh = refreshGlobalBenchesCache(this@MainActivity)
                                globalBenches = fresh
                                globalBenchCount = fresh.size
                                globalBenchCountFailed = false
                                globalRefreshSucceeded = true
                                usingOfflineBenchData = false
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "Global bench refresh failed", e)
                                usingOfflineBenchData = !globalBenches.isNullOrEmpty()
                                if (globalBenches == null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Could not load global benches. Using nearby benches only."
                                        )
                                    }
                                }
                            }
                        } else {
                            usingOfflineBenchData = !globalBenches.isNullOrEmpty()
                            if (globalBenches == null) {
                                globalBenchCountFailed = true
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "No internet and no saved benches. Connect once to download the map."
                                    )
                                }
                            } else if (globalBenchCount == null) {
                                globalBenchCount = globalBenches!!.size
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to load global benches", e)
                        usingOfflineBenchData = !globalBenches.isNullOrEmpty()
                        if (globalBenches == null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Could not load saved benches."
                                )
                            }
                        }
                    } finally {
                        globalBenchesLoading = false
                        globalBenchesReady = true
                        if (!globalRefreshSucceeded && !globalBenches.isNullOrEmpty()) {
                            usingOfflineBenchData = true
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val savedToken = authPrefs.getString("token", null)
                    val savedUsername = authPrefs.getString("username", null)
                    val savedProvider = authPrefs.getString("provider", null)
                    val savedPictureUrl = authPrefs.getString("pictureUrl", null)
                    val savedExpiresAt = if (authPrefs.contains("expiresAtMs")) {
                        authPrefs.getLong("expiresAtMs", 0L).takeIf { it > 0L }
                    } else {
                        null
                    }
                    // Prefer JWT `exp`; clear session if expired or expiry cannot be determined.
                    if (AuthJwt.isTokenStillValid(savedToken, savedExpiresAt)) {
                        val claims = AuthJwt.decodePayloadClaims(savedToken)
                        val expiresAtMs = AuthJwt.expiresAtEpochSeconds(claims, savedExpiresAt)
                            ?.times(1000L)
                        authToken = savedToken
                        authUsername = savedUsername
                        authProvider = savedProvider
                        authPictureUrl = savedPictureUrl
                        authExpiresAtMs = expiresAtMs
                        if (expiresAtMs != null && expiresAtMs != savedExpiresAt) {
                            authPrefs.edit { putLong("expiresAtMs", expiresAtMs) }
                        }
                    } else {
                        authToken = null
                        authUsername = null
                        authProvider = null
                        authPictureUrl = null
                        authExpiresAtMs = null
                        authPrefs.edit { clear() }
                    }
                }

                LaunchedEffect(locationPermissionState.status.isGranted) {
                    if (!locationPermissionState.status.isGranted) return@LaunchedEffect
                    val location = org.openbenches.util.resolveUserLocation(this@MainActivity)
                    val center = if (location != null) {
                        GeoPoint(location.latitude, location.longitude)
                    } else {
                        GeoPoint(51.5, -0.1)
                    }
                    mapCenter = center
                    if (location != null && !hasCenteredMapOnUser) {
                        hasCenteredMapOnUser = true
                        mapZoomLevel = 14.0
                        recenterRequest++
                    }
                }

                LaunchedEffect(globalBenchesReady, locationPermissionState.status.isGranted, mapCenter) {
                    if (!globalBenchesReady || !locationPermissionState.status.isGranted) return@LaunchedEffect
                    val center = mapCenter ?: return@LaunchedEffect
                    fetchBenchesForCenter(center)
                }

                val onMapScreen = locationPermissionState.status.isGranted &&
                    !(showBenchDetails && selectedBenchId != -1) &&
                    !showEditBenchScreen &&
                    !showAboutDialog &&
                    !showTermsScreen &&
                    !showSupportScreen &&
                    !showColophonScreen &&
                    !showAddBenchScreen &&
                    !showLoginScreen &&
                    !showLeaderboardBenches &&
                    !showAccountDetails &&
                    !showPendingUploads

                // System back: match OpenBenches header / in-app back; on the map, do nothing.
                BackHandler {
                    if (onMapScreen) return@BackHandler
                    when {
                        showEditBenchScreen -> showEditBenchScreen = false
                        showBenchDetails && selectedBenchId != -1 -> {
                            showBenchDetails = false
                            selectedBenchId = -1
                            showEditBenchScreen = false
                        }
                        showPendingUploads -> showPendingUploads = false
                        showAddBenchScreen -> {
                            showAddBenchScreen = false
                            if (editingPendingId != null) {
                                showPendingUploads = true
                            }
                            editingPendingId = null
                        }
                        showLoginScreen -> showLoginScreen = false
                        showAccountDetails -> showAccountDetails = false
                        showAboutDialog -> showAboutDialog = false
                        showTermsScreen -> showTermsScreen = false
                        showSupportScreen -> showSupportScreen = false
                        showColophonScreen -> showColophonScreen = false
                        showLeaderboardBenches -> showLeaderboardBenches = false
                    }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (onMapScreen) {
                        // Inset card with rounded corners and a thick outline (similar to openbenches.org header strip).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 0.dp)
                        ) {
                            val globalLoaded = globalBenches?.size ?: 0
                            val searchMatchCount = searchResultBenches?.size ?: 0
                            val shown = when {
                                todoListFilterActive -> todoBenches.size
                                searchMatchCount > 0 -> visibleBenchCount
                                globalLoaded > 0 -> visibleBenchCount
                                else -> benches.size
                            }
                            val shownPart =
                                NumberFormat.getNumberInstance(Locale.getDefault()).format(shown)
                            val mapStatusLine = when {
                                todoListFilterActive -> {
                                    if (todoBenches.size == 1) {
                                        "To-Do: 1 location on map"
                                    } else {
                                        "To-Do: $shownPart locations on map"
                                    }
                                }
                                searchMatchCount > 0 -> {
                                    val matchPart = NumberFormat.getNumberInstance(Locale.getDefault())
                                        .format(searchMatchCount)
                                    if (shown == 1) {
                                        "Search: $shownPart of $matchPart bench on map"
                                    } else {
                                        "Search: $shownPart of $matchPart benches on map"
                                    }
                                }
                                globalBenchesLoading && globalLoaded == 0 ->
                                    "Loading all benches…"
                                isDeviceOffline && globalLoaded == 0 ->
                                    "No internet — benches need one online download"
                                isDeviceOffline ->
                                    if (shown == 1) {
                                        "Showing $shownPart bench on map (offline data)"
                                    } else {
                                        "Showing $shownPart benches on map (offline data)"
                                    }
                                globalLoaded > 0 -> {
                                    if (shown == 1) {
                                        "Showing $shownPart bench on map"
                                    } else {
                                        "Showing $shownPart benches on map"
                                    }
                                }
                                shown == 1 -> "Showing $shownPart bench"
                                else -> "Showing $shownPart benches"
                            }
                            OpenBenchesHeaderBanner(
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                mapStatusLine = mapStatusLine,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                            )
                        }
                        }
                    },
                    bottomBar = {
                        // Reserve a slim bottom inset (replaces an empty BottomAppBar).
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                        )
                    },
                    snackbarHost = { }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showPendingUploads) {
                            PendingUploadsScreen(
                                onBack = { showPendingUploads = false },
                                onEdit = { pending ->
                                    editingPendingId = pending.id
                                    showPendingUploads = false
                                    showAddBenchScreen = true
                                },
                                authToken = authToken,
                                authUsername = authUsername,
                                onPendingChanged = { refreshPendingUploadCount() }
                            )
                        } else if (showAddBenchScreen) {
                            androidx.compose.runtime.key(editingPendingId ?: "new") {
                            AddBenchScreen(
                                onBack = {
                                    showAddBenchScreen = false
                                    if (editingPendingId != null) {
                                        showPendingUploads = true
                                    }
                                    editingPendingId = null
                                },
                                authToken = authToken,
                                authUsername = authUsername,
                                editingPendingId = editingPendingId,
                                onPendingUploadsChanged = { refreshPendingUploadCount() },
                                mapProvider = mapProvider,
                            )
                            }
                        } else if (showLoginScreen) {
                            LoginScreen(
                                onBack = { showLoginScreen = false },
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                                onLoginSuccess = { token, username, provider, pictureUrl, expiresAtMs ->
                                    authToken = token
                                    authUsername = username
                                    authProvider = provider
                                    authPictureUrl = pictureUrl
                                    authExpiresAtMs = expiresAtMs
                                    authPrefs.edit {
                                        putString("token", token)
                                        putString("username", username)
                                        putString("provider", provider)
                                        putString("pictureUrl", pictureUrl)
                                        if (expiresAtMs != null) {
                                            putLong("expiresAtMs", expiresAtMs)
                                        } else {
                                            remove("expiresAtMs")
                                        }
                                    }
                                    showLoginScreen = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (!username.isNullOrBlank()) "Logged in as $username"
                                            else "Login successful."
                                        )
                                    }
                                }
                            )
                        } else if (showAccountDetails) {
                            AccountDetailsScreen(
                                authToken = authToken,
                                authUsername = authUsername,
                                authPictureUrl = authPictureUrl,
                                authExpiresAtMs = authExpiresAtMs,
                                onBack = { showAccountDetails = false }
                            )
                        } else if (showAboutDialog) {
                            AboutScreen(
                                onBack = { showAboutDialog = false },
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                            )
                        } else if (showTermsScreen) {
                            TermsOfServiceScreen(
                                onBack = { showTermsScreen = false },
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                            )
                        } else if (showSupportScreen) {
                            SupportOpenBenchesScreen(
                                onBack = { showSupportScreen = false },
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                            )
                        } else if (showColophonScreen) {
                            ColophonScreen(
                                onBack = { showColophonScreen = false },
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                            )
                        } else if (showLeaderboardBenches) {
                            LeaderboardBenchesScreen(
                                onBack = { showLeaderboardBenches = false },
                                globalBenchCount = globalBenchCount,
                                globalBenchCountFailed = globalBenchCountFailed,
                                authToken = authToken,
                                authProvider = authProvider,
                                authPictureUrl = authPictureUrl,
                                authUsername = authUsername,
                                onUserSelected = { userId ->
                                    val uid = userId.toIntOrNull()
                                    if (uid == null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Invalid user id.")
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            showLeaderboardBenches = false
                                            loading = true
                                            error = null
                                            try {
                                                val results = fetchBenchesForUser(uid)
                                                todoListFilterActive = false
                                                if (results.isEmpty()) {
                                                    searchResultBenches = null
                                                    snackbarHostState.showSnackbar(
                                                        "No benches returned for this user."
                                                    )
                                                } else {
                                                    searchResultBenches = results
                                                    val lats = results.map { it.lat }
                                                    val lngs = results.map { it.lng }
                                                    val centerLat =
                                                        ((lats.maxOrNull() ?: 0.0) + (lats.minOrNull() ?: 0.0)) / 2.0
                                                    val centerLng =
                                                        ((lngs.maxOrNull() ?: 0.0) + (lngs.minOrNull() ?: 0.0)) / 2.0
                                                    mapCenter = GeoPoint(centerLat, centerLng)
                                                    zoomToFitBenches = true
                                                }
                                            } catch (e: Exception) {
                                                error = e.message
                                                snackbarHostState.showSnackbar(
                                                    e.message?.take(80) ?: "Could not load user benches."
                                                )
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                }
                            )
                        } else if (locationPermissionState.status.isGranted) {
                            Box(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .navigationBarsPadding()
                                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                                shape = OpenBenchesHeaderShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp,
                                border = BorderStroke(
                                    width = 4.dp,
                                    color = OpenBenchesBlack,
                                ),
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(OpenBenchesHeaderShape),
                                    contentAlignment = Center,
                                ) {
                                    val center = mapCenter
                                    if (center == null) {
                                        CircularProgressIndicator()
                                    } else {
                                    MapScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        recenterRequest = recenterRequest,
                                        refreshLocation = refreshLocation,
                                        onRefreshLocationHandled = { refreshLocation = false },
                                        benches = benches,
                                        loading = loading && globalBenches.isNullOrEmpty(),
                                        error = if (globalBenches.isNullOrEmpty()) error else null,
                                        mapCenter = center,
                                        setMapCenter = { mapCenter = it },
                                        mapProvider = mapProvider,
                                        onBenchPopupClick = { benchId ->
                                            if (isDeviceOffline) {
                                                showOfflineBenchDialog = true
                                            } else {
                                                selectedBenchId = benchId
                                                showBenchDetails = true
                                            }
                                        },
                                        showBenchDetails = showBenchDetails,
                                        zoomToFitBenches = zoomToFitBenches,
                                        onZoomToFitHandled = { zoomToFitBenches = false },
                                        mapZoomLevel = mapZoomLevel,
                                        onZoomLevelChanged = { newZoomLevel ->
                                            mapZoomLevel = newZoomLevel
                                        },
                                        mapFocusRequest = mapFocusRequest,
                                        globalBenches = globalBenches,
                                        searchResultBenches = searchResultBenches,
                                        todoBenches = todoBenches,
                                        onMapLongHold = { geo ->
                                            todoBenchStore.add(geo.latitude, geo.longitude)
                                            refreshTodoBenches()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Saved to To do",
                                                )
                                            }
                                        },
                                        onTodoClick = { todo ->
                                            todoPendingDelete = todo
                                        },
                                        onVisibleBenchCountChanged = { count ->
                                            visibleBenchCount = count
                                        },
                                    )
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .align(TopEnd)
                                        .zIndex(10f)
                                        .padding(top = 10.dp, end = 10.dp),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OpenBenchesMapLayerFab(
                                        mapProvider = mapProvider,
                                        onMapProviderSelected = { setMapProvider(it) },
                                    )
                                    if (isDeviceOffline) {
                                        OpenBenchesOfflineMapChip()
                                    }
                                }
                                }
                            }
                            // Bottom bar: spread in portrait, grouped in center in landscape
                            val isLandscape =
                                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                            Row(
                                modifier = Modifier
                                    .align(BottomCenter)
                                    .then(
                                        if (isLandscape) Modifier
                                        else Modifier.fillMaxWidth()
                                    )
                                    .systemBarsPadding()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = if (isLandscape) {
                                    Arrangement.spacedBy(8.dp)
                                } else {
                                    Arrangement.SpaceEvenly
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var menuExpanded by remember { mutableStateOf(false) }
                                var todoListMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    OpenBenchesMapFab(
                                        onClick = { menuExpanded = true },
                                        label = "Menu",
                                    ) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Menu")
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = {
                                            menuExpanded = false
                                            todoListMenuExpanded = false
                                        }
                                    ) {
                                        DropdownMenuItem(text = { Text("About") }, onClick = {
                                            menuExpanded = false
                                            todoListMenuExpanded = false
                                            showAboutDialog = true
                                        })
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (darkMode) "Light mode" else "Dark mode")
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                todoListMenuExpanded = false
                                                setDarkMode(!darkMode)
                                            },
                                        )
                                        if (authToken.isNullOrBlank()) {
                                            DropdownMenuItem(
                                                text = { Text("Login") },
                                                onClick = {
                                                    menuExpanded = false
                                                    showLoginScreen = true
                                                },
                                                enabled = !isDeviceOffline,
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Logout") },
                                                onClick = {
                                                    menuExpanded = false
                                                    coroutineScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            warmCustomTabs(applicationContext)
                                                        }
                                                        WebAuthProvider.logout(auth0Client)
                                                            .withScheme(BuildConfig.AUTH0_SCHEME)
                                                            .start(
                                                                this@MainActivity,
                                                                object : Callback<Void?, AuthenticationException> {
                                                                    override fun onSuccess(result: Void?) {
                                                                        authToken = null
                                                                        authUsername = null
                                                                        authProvider = null
                                                                        authPictureUrl = null
                                                                        authExpiresAtMs = null
                                                                        authPrefs.edit { clear() }
                                                                        coroutineScope.launch {
                                                                            snackbarHostState.showSnackbar("Logged out.")
                                                                        }
                                                                    }

                                                                    override fun onFailure(error: AuthenticationException) {
                                                                        authToken = null
                                                                        authUsername = null
                                                                        authProvider = null
                                                                        authPictureUrl = null
                                                                        authExpiresAtMs = null
                                                                        authPrefs.edit { clear() }
                                                                        coroutineScope.launch {
                                                                            snackbarHostState.showSnackbar(
                                                                                "Logged out locally."
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                    }
                                                },
                                                enabled = !isDeviceOffline,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Account details") },
                                                onClick = {
                                                    menuExpanded = false
                                                    showAccountDetails = true
                                                }
                                            )
                                        }
                                        DropdownMenuItem(text = { Text("Terms of Service") }, onClick = {
                                            menuExpanded = false
                                            showTermsScreen = true
                                        })
                                        DropdownMenuItem(text = { Text("Support OpenBenches") }, onClick = {
                                            menuExpanded = false
                                            showSupportScreen = true
                                        })
                                        DropdownMenuItem(
                                            text = { Text("To-Do list") },
                                            onClick = {
                                                todoListMenuExpanded = !todoListMenuExpanded
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = if (todoListMenuExpanded) {
                                                        Icons.Filled.KeyboardArrowUp
                                                    } else {
                                                        Icons.Filled.KeyboardArrowDown
                                                    },
                                                    contentDescription = "To-Do list options",
                                                )
                                            },
                                        )
                                        if (todoListMenuExpanded) {
                                            DropdownMenuItem(
                                                text = { Text("View To-Do list") },
                                                onClick = {
                                                    menuExpanded = false
                                                    todoListMenuExpanded = false
                                                    showTodoListOnMap()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Clear To-Do list") },
                                                onClick = {
                                                    menuExpanded = false
                                                    todoListMenuExpanded = false
                                                    showClearTodoConfirm = true
                                                },
                                                enabled = todoBenches.isNotEmpty(),
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Load To-Do list") },
                                                onClick = {
                                                    menuExpanded = false
                                                    todoListMenuExpanded = false
                                                    uploadTodoListLauncher.launch(
                                                        arrayOf(
                                                            "application/json",
                                                            "text/*",
                                                            "*/*",
                                                        ),
                                                    )
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Save To-Do list") },
                                                onClick = {
                                                    menuExpanded = false
                                                    todoListMenuExpanded = false
                                                    if (todoBenches.isEmpty()) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "To-Do list is empty.",
                                                            )
                                                        }
                                                    } else {
                                                        downloadTodoListLauncher.launch(
                                                            "openbenches-todo-list.json",
                                                        )
                                                    }
                                                },
                                                enabled = todoBenches.isNotEmpty(),
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Contributors Leaderboard") },
                                            onClick = {
                                                menuExpanded = false
                                                todoListMenuExpanded = false
                                                showLeaderboardBenches = true
                                            },
                                            enabled = !isDeviceOffline,
                                        )
                                        DropdownMenuItem(text = { Text("Colophon") }, onClick = {
                                            menuExpanded = false
                                            showColophonScreen = true
                                        })
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OpenBenchesMapFab(
                                        onClick = { showSearchDialog = true },
                                        label = "Search",
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search")
                                    }
                                    OpenBenchesMapFab(
                                        onClick = {
                                            if (searchResultBenches != null) {
                                                clearMapSearchFilter()
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "No search filter is active",
                                                    )
                                                }
                                            }
                                        },
                                        label = "Clear",
                                    ) {
                                        FabIconWithSlash(
                                            imageVector = Icons.Filled.SearchOff,
                                            contentDescription = "Clear search",
                                            showSlash = searchResultBenches == null,
                                        )
                                    }
                                }
                                OpenBenchesMapFab(
                                    onClick = {
                                        editingPendingId = null
                                        showAddBenchScreen = true
                                    },
                                    label = "Add",
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add Bench")
                                }
                                if (locationPermissionState.status.isGranted) {
                                    OpenBenchesMapFab(
                                        onClick = {
                                            refreshLocation = true
                                            recenterRequest++
                                        },
                                        label = "Locate",
                                    ) {
                                        Icon(
                                            Icons.Filled.MyLocation,
                                            contentDescription = "Center on my location",
                                        )
                                    }
                                }
                                OpenBenchesMapFab(
                                    onClick = {
                                        if (pendingUploadCount > 0) {
                                            showPendingUploads = true
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "No benches saved for upload"
                                                )
                                            }
                                        }
                                    },
                                    label = "Upload",
                                ) {
                                    FabIconWithSlash(
                                        imageVector = Icons.Filled.Upload,
                                        contentDescription = "Upload",
                                        showSlash = pendingUploadCount == 0,
                                    )
                                }
                            }
                            if (showBenchDetails && selectedBenchId != -1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(100f),
                                ) {
                                    BenchDetailsScreen(
                                        benchId = selectedBenchId,
                                        onBack = { _, _ ->
                                            showBenchDetails = false
                                            selectedBenchId = -1
                                            showEditBenchScreen = false
                                        },
                                        mapProvider = mapProvider,
                                        authToken = authToken,
                                        onEdit = {
                                            if (!authToken.isNullOrBlank()) {
                                                showEditBenchScreen = true
                                            }
                                        },
                                        refreshKey = benchDetailsRefreshKey,
                                    )
                                }
                            }
                            if (showEditBenchScreen && selectedBenchId != -1 && !authToken.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(101f),
                                ) {
                                    EditBenchScreen(
                                        benchId = selectedBenchId,
                                        authToken = authToken!!,
                                        onBack = { showEditBenchScreen = false },
                                        onSaved = {
                                            showEditBenchScreen = false
                                            benchDetailsRefreshKey++
                                        },
                                        mapProvider = mapProvider,
                                    )
                                }
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
                                org.openbenches.ui.theme.OpenBenchesGoldButton(
                                    onClick = { locationPermissionState.launchPermissionRequest() },
                                ) {
                                    Text("Grant Location Permission")
                                }
                            }
                        }
                        if (showOfflineBenchDialog) {
                            AlertDialog(
                                onDismissRequest = { showOfflineBenchDialog = false },
                                title = { Text("Bench unavailable") },
                                text = {
                                    Text(
                                        "Currently unable to display bench due to being offline."
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = { showOfflineBenchDialog = false }) {
                                        Text("OK")
                                    }
                                },
                            )
                        }
                        todoPendingDelete?.let { todo ->
                            AlertDialog(
                                onDismissRequest = { todoPendingDelete = null },
                                title = { Text("To do location") },
                                text = {
                                    Text(
                                        String.format(
                                            Locale.US,
                                            "Bench seen at %.5f, %.5f.\n\nRemove this to do marker?",
                                            todo.latitude,
                                            todo.longitude,
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            todoBenchStore.delete(todo.id)
                                            refreshTodoBenches()
                                            todoPendingDelete = null
                                        },
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { todoPendingDelete = null }) {
                                        Text("Keep")
                                    }
                                },
                            )
                        }
                        if (showClearTodoConfirm) {
                            AlertDialog(
                                onDismissRequest = { showClearTodoConfirm = false },
                                title = { Text("Clear To-Do list") },
                                text = {
                                    Text(
                                        "Remove all ${todoBenches.size} To-Do location(s)? This cannot be undone.",
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            todoBenchStore.clear()
                                            refreshTodoBenches()
                                            showClearTodoConfirm = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("To-Do list cleared.")
                                            }
                                        },
                                    ) {
                                        Text("Clear")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearTodoConfirm = false }) {
                                        Text("Cancel")
                                    }
                                },
                            )
                        }
                        // Show search dialog
                        if (showSearchDialog) {
                            AlertDialog(
                                onDismissRequest = { showSearchDialog = false },
                                title = { Text("Search Inscriptions") },
                                text = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = searchText,
                                            onValueChange = { searchText = it },
                                            label = { Text("Enter search text") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    showSearchDialog = false
                                                    val max = globalBenchCount
                                                    if (max != null && max > 0) {
                                                        selectedBenchId = (1..max).random()
                                                        showBenchDetails = true
                                                    } else {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "Total bench count is not available yet. Try again in a moment.",
                                                            )
                                                        }
                                                    }
                                                },
                                                enabled = !isDeviceOffline,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("Random Bench", textAlign = TextAlign.Center)
                                            }
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        loading = true
                                                        error = null
                                                        try {
                                                            val results = fetchLatestBenches(5)
                                                            showSearchDialog = false
                                                            todoListFilterActive = false
                                                            if (results.isEmpty()) {
                                                                searchResultBenches = null
                                                                snackbarHostState.showSnackbar(
                                                                    "No newest benches were returned.",
                                                                )
                                                            } else {
                                                                searchResultBenches = results
                                                                val lats = results.map { it.lat }
                                                                val lngs = results.map { it.lng }
                                                                val centerLat =
                                                                    ((lats.maxOrNull() ?: 0.0) + (lats.minOrNull() ?: 0.0)) / 2.0
                                                                val centerLng =
                                                                    ((lngs.maxOrNull() ?: 0.0) + (lngs.minOrNull() ?: 0.0)) / 2.0
                                                                mapCenter = GeoPoint(centerLat, centerLng)
                                                                zoomToFitBenches = true
                                                            }
                                                        } catch (e: Exception) {
                                                            showSearchDialog = false
                                                            error = e.message
                                                            snackbarHostState.showSnackbar(
                                                                e.message ?: "Could not load newest benches.",
                                                            )
                                                        } finally {
                                                            loading = false
                                                        }
                                                    }
                                                },
                                                enabled = !isDeviceOffline,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("Newest 5 benches", textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        coroutineScope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                val results = fetchBenchesBySearch(searchText)
                                                showSearchDialog = false
                                                todoListFilterActive = false
                                                if (results.isEmpty()) {
                                                    searchResultBenches = null
                                                    snackbarHostState.showSnackbar(
                                                        "No benches found which match the search criteria",
                                                    )
                                                } else {
                                                    searchResultBenches = results
                                                    val lats = results.map { it.lat }
                                                    val lngs = results.map { it.lng }
                                                    val centerLat =
                                                        ((lats.maxOrNull() ?: 0.0) + (lats.minOrNull() ?: 0.0)) / 2.0
                                                    val centerLng =
                                                        ((lngs.maxOrNull() ?: 0.0) + (lngs.minOrNull() ?: 0.0)) / 2.0
                                                    mapCenter = GeoPoint(centerLat, centerLng)
                                                    zoomToFitBenches = true
                                                }
                                            } catch (e: Exception) {
                                                showSearchDialog = false
                                                error = e.message
                                            } finally {
                                                loading = false
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
                                },
                            )
                        }
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Center)
                                .zIndex(2000f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean,
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 0.dp),
            ) {
                OpenBenchesHeaderBanner(
                    globalBenchCount = globalBenchCount,
                    globalBenchCountFailed = globalBenchCountFailed,
                    centerCountOnly = true,
                    authToken = authToken,
                    authProvider = authProvider,
                    authPictureUrl = authPictureUrl,
                    authUsername = authUsername,
                    onClick = onBack,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Text(
                text = "About OpenBenches.org",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            AboutContributorLinks()
            Text(
                text = "Usually after someone has passed away, a reminder of their life, may be a gravestone marking their final resting place or a few famous or influential figures may have blue plaques.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Terence and Elizabeth Eden had been contributing to OpenPlaques for a few years - but couldn't find anything for benches so they created OpenBenches.org dedicated to those benches.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Simon King had the same idea, years later, and created the mobile App MemorialBenchfinder.app. Later Simon found OpenBenches.org and opted to put asside MemorialBenchFinder.app and work with Terence and create a mobile app for OpenBenches. MemorialBenchfinder.app now just forwards to Openbenches.org",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How to Help",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Anybody is free to take a photo of a bench and upload it to the site.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "If you wish to share a larger collection of Benches - please feel free to contact us and we can integrate your benches!",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "If you are a coder or designer - feel free to help us out at github.com/openbenches/openbenches.org/",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "App Version",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Your current installed version is: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
            )
            }
            AboutSocialLinksRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

private data class AboutContributorLink(
    val label: String,
    val url: String,
)

private val aboutContributorLinks = listOf(
    AboutContributorLink(
        label = "Terence Eden's Blog",
        url = "https://shkspr.mobi/blog/",
    ),
    AboutContributorLink(
        label = "Elizabeth Eden on LinkedIn",
        url = "https://www.linkedin.com/in/lizeden/",
    ),
    AboutContributorLink(
        label = "Simon King on LinkedIn",
        url = "https://www.linkedin.com/in/simon-king-gcgi-bsc-hons-pgdip-tm-cmgr-mcmi-cism-crisc-a6ba951a/",
    ),
)

@Composable
private fun AboutContributorLinks() {
    val context = LocalContext.current
    val linkColor = openBenchesLinkColor()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        aboutContributorLinks.forEach { link ->
            Text(
                text = link.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = linkColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
                },
            )
        }
    }
}

private data class AboutSocialLink(
    @param:DrawableRes val iconRes: Int,
    val url: String,
    val contentDescription: String,
)

private val aboutSocialLinks = listOf(
    AboutSocialLink(
        iconRes = R.drawable.ic_social_mastodon,
        url = "https://bot.openbenches.org/",
        contentDescription = "OpenBenches on Mastodon",
    ),
    AboutSocialLink(
        iconRes = R.drawable.ic_social_bluesky,
        url = "https://bsky.app/profile/openbenches.org",
        contentDescription = "OpenBenches on Bluesky",
    ),
    AboutSocialLink(
        iconRes = R.drawable.ic_social_github,
        url = "https://github.com/openbenches/openbenches.org",
        contentDescription = "OpenBenches on GitHub",
    ),
)

private const val CC_BY_SA_LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
private const val CC_BY_SA_BADGE_ASSET = "file:///android_asset/colophon/cc-by-sa.svg"

@Composable
private fun AboutSocialLinksRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            aboutSocialLinks.forEach { link ->
                IconButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
                    },
                ) {
                    Image(
                        painter = painterResource(link.iconRes),
                        contentDescription = link.contentDescription,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        AsyncImage(
            model = CC_BY_SA_BADGE_ASSET,
            contentDescription = "Creative Commons Attribution-ShareAlike 4.0 International",
            modifier = Modifier
                .width(120.dp)
                .height(42.dp)
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, CC_BY_SA_LICENSE_URL.toUri()),
                    )
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun FabIconWithSlash(
    imageVector: ImageVector,
    contentDescription: String,
    showSlash: Boolean,
) {
    Box(modifier = Modifier.size(24.dp)) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
        if (showSlash) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color.Red,
                    start = Offset(x = size.width * 0.05f, y = size.height * 0.95f),
                    end = Offset(x = size.width * 0.95f, y = size.height * 0.05f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
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
