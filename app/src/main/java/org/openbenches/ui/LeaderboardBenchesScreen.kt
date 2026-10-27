package org.openbenches.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import org.openbenches.ui.theme.OpenBenchesGoldButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Person
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import org.openbenches.ui.theme.OpenBenchesHeaderBanner
import java.net.URL
import java.util.regex.Pattern

/**
 * Data class representing a leaderboard entry for benches
 */
data class LeaderboardBenchesEntry(
    val rank: Int,
    val username: String,
    val totalBenches: Int,
    val totalPhotos: Int,
    val platformIcon: String, // e.g., "github", "twitter", "facebook", etc.
    val avatarUrl: String? = null, // User's profile image URL
    val userId: String? = null // User ID for linking
)

/**
 * Fetch live leaderboard data from OpenBenches website
 */
suspend fun fetchBenchesLeaderboardData(): List<LeaderboardBenchesEntry> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://openbenches.org/leaderboard")
        val html = url.readText()
        
        // Parse the HTML to extract leaderboard data
        val entries = mutableListOf<LeaderboardBenchesEntry>()
        
        // New format: contributors table with User | Benches | Photos.
        // Parse table rows and extract user + platform + avatar + both numeric columns.
        val tablePattern = Pattern.compile(
            """<tr[^>]*>\s*<td[^>]*>.*?<a\s+href="/user/(\d+)".*?<img[^>]*src="/images/svg/([a-zA-Z0-9_-]+)\.svg"[^>]*>.*?(?:<img[^>]*src="([^"]+)"[^>]*>)?.*?<a\s+href="/user/\1"[^>]*>\s*([^<]+?)\s*</a>.*?</td>\s*<td[^>]*>\s*([\d,]+)\s*</td>\s*<td[^>]*>\s*([\d,]+)\s*</td>\s*</tr>""",
            Pattern.DOTALL
        )
        val tableMatcher = tablePattern.matcher(html)

        var rank = 1
        while (tableMatcher.find()) {
            val userId = tableMatcher.group(1) ?: ""
            val platform = tableMatcher.group(2) ?: "unknown"
            val avatarUrl = tableMatcher.group(3) ?: ""
            val username = tableMatcher.group(4)?.trim() ?: "Unknown User"
            val benchesCount = tableMatcher.group(5)?.replace(",", "")?.toIntOrNull() ?: 0
            val photosCount = tableMatcher.group(6)?.replace(",", "")?.toIntOrNull() ?: 0

            entries.add(
                LeaderboardBenchesEntry(
                    rank = rank,
                    username = username,
                    totalBenches = benchesCount,
                    totalPhotos = photosCount,
                    platformIcon = platform,
                    avatarUrl = if (avatarUrl.isNotEmpty()) avatarUrl else null,
                    userId = if (userId.isNotEmpty()) userId else null
                )
            )
            rank++
        }
        
        // If parsing failed, return fallback data
        if (entries.isEmpty()) {
            listOf(
                LeaderboardBenchesEntry(1, "jrbray1", 4284, 12651, "github", "https://avatars0.githubusercontent.com/u/25801685?v=4&s=48", "6143"),
                LeaderboardBenchesEntry(2, "sjorford", 3232, 9036, "twitter", "https://res.cloudinary.com/openbenches-org/image/twitter/27730624.jpg", "1708"),
                LeaderboardBenchesEntry(3, "ukmoose", 2856, 6875, "twitter", "https://res.cloudinary.com/openbenches-org/image/twitter/222949835.jpg", "1781")
            )
        } else {
            entries
        }
    } catch (e: Exception) {
        // Return fallback data on error
        listOf(
            LeaderboardBenchesEntry(1, "jrbray1", 4284, 12651, "github", "https://avatars0.githubusercontent.com/u/25801685?v=4&s=48", "6143"),
            LeaderboardBenchesEntry(2, "sjorford", 3232, 9036, "twitter", "https://res.cloudinary.com/openbenches-org/image/twitter/27730624.jpg", "1708"),
            LeaderboardBenchesEntry(3, "ukmoose", 2856, 6875, "twitter", "https://res.cloudinary.com/openbenches-org/image/twitter/222949835.jpg", "1781")
        )
    }
}

/** Contributors whose username contains [query] (case-insensitive). Empty query returns all. */
fun filterLeaderboardByUsername(
    entries: List<LeaderboardBenchesEntry>,
    query: String,
): List<LeaderboardBenchesEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return entries
    return entries.filter { entry ->
        entry.username.contains(trimmed, ignoreCase = true)
    }
}

/**
 * LeaderboardBenchesScreen displays a leaderboard of users who have added and edited benches
 * This screen shows users ranked by their total contributions to the OpenBenches project
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardBenchesScreen(
    onBack: () -> Unit,
    onUserSelected: (userId: String) -> Unit,
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean,
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var leaderboardData by remember { mutableStateOf<List<LeaderboardBenchesEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val displayedEntries = remember(leaderboardData, searchQuery) {
        filterLeaderboardByUsername(leaderboardData, searchQuery)
    }
    val coroutineScope = rememberCoroutineScope()

    // Fetch live leaderboard data from OpenBenches website
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                loading = true
                error = null
                
                // Fetch live data from the website
                leaderboardData = fetchBenchesLeaderboardData()
                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

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
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Top Contributors",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search contributors") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear search",
                                    )
                                }
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Users ranked by benches and photos contributed",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error loading leaderboard",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OpenBenchesGoldButton(onClick = {
                                loading = true
                                error = null
                                coroutineScope.launch {
                                    try {
                                        leaderboardData = fetchBenchesLeaderboardData()
                                        loading = false
                                    } catch (e: Exception) {
                                        error = e.message
                                        loading = false
                                    }
                                }
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {
                    if (displayedEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No contributors match your search",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedEntries) { entry ->
                                LeaderboardBenchesItem(
                                    entry = entry,
                                    onUserClick = onUserSelected
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual leaderboard item component
 */
@Composable
private fun LeaderboardBenchesItem(
    entry: LeaderboardBenchesEntry,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val canOpenMap = !entry.userId.isNullOrBlank()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canOpenMap) {
                    Modifier.clickable(role = Role.Button) { onUserClick(entry.userId!!) }
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                text = "#${entry.rank}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(60.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // User avatar or default icon
            if (entry.avatarUrl != null) {
                AsyncImage(
                    model = entry.avatarUrl,
                    contentDescription = "User avatar",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Default avatar",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.platformIcon,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Benches and Photos totals
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${entry.totalBenches}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "benches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${entry.totalPhotos}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
