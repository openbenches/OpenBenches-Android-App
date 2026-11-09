package org.openbenches.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.openbenches.ui.theme.OpenBenchesHeaderBanner

private data class ColophonEntry(
    val caption: String,
    val url: String? = null,
    /** Asset file under assets/colophon/ (e.g. php.svg). */
    val imageAsset: String? = null,
)

private data class ColophonSection(
    val title: String,
    val entries: List<ColophonEntry>,
)

private val colophonSections = listOf(
    ColophonSection(
        title = "Platform & Language",
        entries = listOf(
            ColophonEntry(caption = "Android (min SDK 28, target/compile SDK 36)"),
            ColophonEntry(caption = "Kotlin 2.2.10"),
            ColophonEntry(caption = "JVM 11"),
            ColophonEntry(caption = "Gradle Kotlin DSL with Android Gradle Plugin 9.2.1"),
        ),
    ),
    ColophonSection(
        title = "User Interface",
        entries = listOf(
            ColophonEntry(caption = "Jetpack Compose (Material 3, Compose BOM 2024.02.00)"),
            ColophonEntry(caption = "Material Icons Extended"),
            ColophonEntry(caption = "Edge-to-edge UI via enableEdgeToEdge()"),
            ColophonEntry(caption = "Custom Compose theme/components (OpenBenchesTheme, gold buttons, header banner, etc.)"),
            ColophonEntry(caption = "AndroidView interop for embedding the native map view inside Compose"),
            ColophonEntry(caption = "Coil 2.4.0 (+ SVG decoder) — image loading in Compose"),
        ),
    ),
    ColophonSection(
        title = "Maps & Locations",
        entries = listOf(
            ColophonEntry(caption = "osmdroid 6.1.16 — OpenStreetMap and Esri World Imagery tiles"),
            ColophonEntry(caption = "OpenStreetMap (Mapnik) and Esri World Imagery satellite tile sources"),
            ColophonEntry(caption = "Custom viewport clustering (ViewportBenchClusterer, MapClusterIcon)"),
            ColophonEntry(caption = "Google Play Services Location — device GPS / fused location"),
            ColophonEntry(caption = "AndroidX ExifInterface & MediaStore — reading GPS from photo metadata"),
        ),
    ),
    ColophonSection(
        title = "Networking & APIs",
        entries = listOf(
            ColophonEntry(caption = "Retrofit 2.9.0 + Gson converter"),
            ColophonEntry(caption = "OkHttp (multipart uploads, streaming bench counts, auth headers)"),
            ColophonEntry(caption = "OpenBenches API — https://openbenches.org/api/ (benches, search, bench details, user benches)"),
            ColophonEntry(caption = "OpenBenches upload — https://openbenches.org/upload (multipart form upload)"),
            ColophonEntry(caption = "OpenBenches update — https://openbenches.org/update (multipart form edit)"),
            ColophonEntry(caption = "OpenBenches leaderboard — scraped/parsed from https://openbenches.org/leaderboard"),
        ),
    ),
    ColophonSection(
        title = "Inscription OCR",
        entries = listOf(
            ColophonEntry(caption = "OpenCV 4.12.0 — photo preprocessing for inscription recognition"),
            ColophonEntry(caption = "PaddleOCR (:ppocr-sdk) with ONNX Runtime — inscription text recognition"),
        ),
    ),
    ColophonSection(
        title = "Authentication",
        entries = listOf(
            ColophonEntry(
                caption = "Auth0 Android SDK 2.11.0 — Universal Login (GitHub, Facebook, LinkedIn, X, WordPress, Discord, OpenStreetMap)",
            ),
            ColophonEntry(caption = "Chrome Custom Tabs (androidx.browser) for Auth0 login flow"),
            ColophonEntry(caption = "Custom JWT payload parsing (AuthJwt.kt) for username/provider/avatar"),
        ),
    ),
)

private fun colophonAssetUri(filename: String): String =
    "file:///android_asset/colophon/$filename"

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColophonScreen(
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Colophon",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Built with these amazing resources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            colophonSections.forEach { section ->
                ColophonSectionBlock(section)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColophonSectionBlock(section: ColophonSection) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = section.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        section.entries.forEach { entry ->
            ColophonEntryRow(entry)
        }
    }
}

@Composable
private fun ColophonEntryRow(entry: ColophonEntry) {
    val context = LocalContext.current
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (entry.imageAsset != null) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val imageModifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (entry.url != null) {
                            Modifier.clickable { openUrl(context, entry.url) }
                        } else {
                            Modifier
                        }
                    )
                AsyncImage(
                    model = colophonAssetUri(entry.imageAsset),
                    contentDescription = entry.caption,
                    modifier = imageModifier,
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = entry.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (entry.url != null) {
            val linkText = buildAnnotatedString {
                pushStringAnnotation(tag = "link", annotation = entry.url)
                withStyle(linkStyle) {
                    append(entry.caption)
                }
                pop()
            }
            ClickableText(
                text = linkText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                onClick = { offset ->
                    linkText.getStringAnnotations(start = offset, end = offset)
                        .firstOrNull()
                        ?.let { annotation -> openUrl(context, annotation.item) }
                },
            )
        } else {
            Text(
                text = entry.caption,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
