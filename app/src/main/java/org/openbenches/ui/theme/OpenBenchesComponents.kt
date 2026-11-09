package org.openbenches.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.openbenches.R
import org.openbenches.ui.MapProvider
import java.text.NumberFormat
import java.util.Locale

/** Maps Auth0 `sub` provider segment (e.g. `facebook`, `github`) to a brand icon. */
@DrawableRes
fun authProviderIconRes(authProvider: String?): Int? {
    val key = authProvider?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return null
    return when {
        key.startsWith("github") -> R.drawable.ic_social_github
        key.startsWith("facebook") -> R.drawable.ic_provider_facebook
        key.startsWith("linkedin") -> R.drawable.ic_provider_linkedin
        key.startsWith("twitter") || key == "x" || key.startsWith("x-") -> R.drawable.ic_provider_x
        key.startsWith("wordpress") -> R.drawable.ic_provider_wordpress
        key.startsWith("discord") -> R.drawable.ic_provider_discord
        key.startsWith("osm") || key.contains("openstreetmap") -> R.drawable.ic_provider_osm
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBenchesTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun OpenBenchesBorderedSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = OpenBenchesHeaderShape,
        color = color,
        shadowElevation = 4.dp,
        border = BorderStroke(4.dp, OpenBenchesBlack),
        content = content,
    )
}

fun formatMemorialBenchCountLine(
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean = false,
): String {
    val countPart = when {
        globalBenchCount != null ->
            NumberFormat.getNumberInstance(Locale.getDefault()).format(globalBenchCount)
        globalBenchCountFailed -> "—"
        else -> "…"
    }
    return "A map of $countPart memorial benches"
}

/**
 * Header strip matching the map screen: logo left, optional auth right.
 * [mapStatusLine] is shown under the title on the map; omit on About (center count only).
 */
@Composable
fun OpenBenchesHeaderBanner(
    globalBenchCount: Int?,
    modifier: Modifier = Modifier,
    globalBenchCountFailed: Boolean = false,
    mapStatusLine: String? = null,
    centerCountOnly: Boolean = false,
    authToken: String? = null,
    authProvider: String? = null,
    authPictureUrl: String? = null,
    authUsername: String? = null,
    /** When set, tapping the header returns to the map (menu screens). */
    onClick: (() -> Unit)? = null,
) {
    val benchCountLine = formatMemorialBenchCountLine(globalBenchCount, globalBenchCountFailed)

    OpenBenchesBorderedSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Back to map"
                        }
                } else {
                    Modifier
                }
            ),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Image(
                painter = painterResource(R.drawable.openbenches_header_icon),
                contentDescription = "OpenBenches logo",
                modifier = Modifier.size(48.dp),
            )

            if (centerCountOnly) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = benchCountLine,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = "OpenBenches",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = benchCountLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                    )
                    if (!mapStatusLine.isNullOrBlank()) {
                        Text(
                            text = mapStatusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                        )
                    }
                }
            }

            OpenBenchesHeaderAuthActions(
                authToken = authToken,
                authProvider = authProvider,
                authPictureUrl = authPictureUrl,
                authUsername = authUsername,
            )
        }
    }
}

@Composable
private fun OpenBenchesHeaderAuthActions(
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
) {
    if (authToken.isNullOrBlank()) return

    val providerIconRes = authProviderIconRes(authProvider)
    val providerLabel = authProvider
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        ?: "Signed in"

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
        ) {
            if (providerIconRes != null) {
                Image(
                    painter = painterResource(providerIconRes),
                    contentDescription = "$providerLabel account",
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp),
                )
            } else {
                Text(
                    text = providerLabel.take(1).ifBlank { "?" },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Color.White,
        ) {
            if (!authPictureUrl.isNullOrBlank()) {
                AsyncImage(
                    model = authPictureUrl,
                    contentDescription = authUsername?.let { "$it avatar" } ?: "User avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "User avatar",
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun OpenBenchesGoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = OpenBenchesShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        content = content,
    )
}

/** High-contrast badge shown on the map when there is no internet (e.g. airplane mode). */
@Composable
fun OpenBenchesOfflineMapChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = OpenBenchesPlaqueGold,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(2.dp, OpenBenchesBorder),
    ) {
        Text(
            text = "Offline map",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = OpenBenchesBlack,
        )
    }
}

@Composable
fun OpenBenchesMapFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = if (label != null) {
            RoundedCornerShape(16.dp)
        } else {
            FloatingActionButtonDefaults.shape
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
    ) {
        if (label == null) {
            content()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                content()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun OpenBenchesMapLayerFab(
    mapProvider: MapProvider,
    onMapProviderSelected: (MapProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layersMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OpenBenchesMapFab(onClick = { layersMenuExpanded = true }) {
            Icon(Icons.Filled.Layers, contentDescription = "Map layers")
        }
        DropdownMenu(
            expanded = layersMenuExpanded,
            onDismissRequest = { layersMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(MapProvider.OSM.menuLabel()) },
                onClick = {
                    layersMenuExpanded = false
                    onMapProviderSelected(MapProvider.OSM)
                },
                enabled = mapProvider != MapProvider.OSM,
            )
            DropdownMenuItem(
                text = { Text(MapProvider.ESRI_SATELLITE.menuLabel()) },
                onClick = {
                    layersMenuExpanded = false
                    onMapProviderSelected(MapProvider.ESRI_SATELLITE)
                },
                enabled = mapProvider != MapProvider.ESRI_SATELLITE,
            )
        }
    }
}
