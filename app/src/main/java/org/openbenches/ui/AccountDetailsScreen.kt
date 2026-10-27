package org.openbenches.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import org.openbenches.ui.theme.OpenBenchesTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.openbenches.data.AuthJwt
import org.openbenches.data.fetchUserBenchDateRange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsScreen(
    authToken: String?,
    authUsername: String?,
    authPictureUrl: String?,
    authExpiresAtMs: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val claims = remember(authToken) { AuthJwt.decodePayloadClaims(authToken) }
    val displayName = AuthJwt.displayName(claims, authUsername) ?: "—"
    val pictureUrl = AuthJwt.pictureUrl(claims, authPictureUrl)
    val verifiedVia = AuthJwt.verifiedVia(claims)?.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    } ?: "—"
    val tokenValidFrom = AuthJwt.formatEpochSeconds(AuthJwt.issuedAtEpochSeconds(claims))
    val tokenValidUntil = AuthJwt.formatEpochSeconds(
        AuthJwt.expiresAtEpochSeconds(claims, authExpiresAtMs)
    )

    var loadingStats by remember { mutableStateOf(true) }
    var statsError by remember { mutableStateOf<String?>(null) }
    var totalBenches by remember { mutableStateOf<Int?>(null) }
    var firstBenchDate by remember { mutableStateOf<String?>(null) }
    var latestBenchDate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authToken, authUsername) {
        loadingStats = true
        statsError = null
        totalBenches = null
        firstBenchDate = null
        latestBenchDate = null
        try {
            val leaderboard = fetchBenchesLeaderboardData()
            val entry = findLeaderboardEntryForAccount(
                entries = leaderboard,
                username = AuthJwt.displayName(claims, authUsername),
                authSubUserId = AuthJwt.openBenchesUserIdFromSub(claims),
            )
            if (entry != null) {
                totalBenches = entry.totalBenches
                val userId = entry.userId?.toIntOrNull()
                if (userId != null) {
                    val range = fetchUserBenchDateRange(userId)
                    firstBenchDate = AuthJwt.formatIsoDateTime(range.oldestCreatedAt)
                    latestBenchDate = AuthJwt.formatIsoDateTime(range.newestCreatedAt)
                }
            }
        } catch (e: Exception) {
            statsError = "Failed to load bench statistics: ${e.message}"
        } finally {
            loadingStats = false
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            OpenBenchesTopAppBar(
                title = { Text("Account details") },
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (!pictureUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = pictureUrl,
                        contentDescription = "Profile picture",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile picture",
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AccountDetailLine("You are logged in as $displayName")
            AccountDetailLine("Account verified via $verifiedVia")
            AccountDetailLine("Current token valid from: $tokenValidFrom")
            AccountDetailLine("Current token valid until: $tokenValidUntil")

            if (loadingStats) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
            } else if (statsError != null) {
                Text(
                    text = statsError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val nil = "Nil"
                val benchCount = totalBenches?.let {
                    java.text.NumberFormat.getNumberInstance(Locale.getDefault()).format(it)
                } ?: nil
                val first = firstBenchDate?.takeIf { it != "—" } ?: nil
                val latest = latestBenchDate?.takeIf { it != "—" } ?: nil
                AccountDetailLine("You have a total of $benchCount benches")
                AccountDetailLine("First bench added: $first")
                AccountDetailLine("Latest bench added: $latest")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AccountDetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun findLeaderboardEntryForAccount(
    entries: List<LeaderboardBenchesEntry>,
    username: String?,
    authSubUserId: String?,
): LeaderboardBenchesEntry? {
    authSubUserId?.let { id ->
        entries.firstOrNull { it.userId == id }?.let { return it }
    }
    username?.let { name ->
        entries.firstOrNull { it.username.equals(name, ignoreCase = true) }?.let { return it }
    }
    return null
}
