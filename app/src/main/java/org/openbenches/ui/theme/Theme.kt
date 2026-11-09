package org.openbenches.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val OpenBenchesLightColorScheme = lightColorScheme(
    primary = OpenBenchesSkyBlue,
    onPrimary = OpenBenchesBlack,
    primaryContainer = OpenBenchesPlaqueGold,
    onPrimaryContainer = OpenBenchesBlack,
    secondary = OpenBenchesGroundBrown,
    onSecondary = OpenBenchesCream,
    secondaryContainer = OpenBenchesGroundBrown,
    onSecondaryContainer = OpenBenchesCream,
    tertiary = OpenBenchesGrassGreen,
    onTertiary = OpenBenchesCream,
    background = OpenBenchesCream,
    onBackground = OpenBenchesBlack,
    surface = OpenBenchesCream,
    onSurface = OpenBenchesBlack,
    surfaceVariant = OpenBenchesSkyBlue,
    onSurfaceVariant = OpenBenchesBlack,
    outline = OpenBenchesBorder,
    error = OpenBenchesDanger,
    onError = OpenBenchesCream,
)

private val OpenBenchesDarkColorScheme = darkColorScheme(
    primary = OpenBenchesSkyBlue,
    onPrimary = OpenBenchesBlack,
    primaryContainer = OpenBenchesPlaqueGold,
    onPrimaryContainer = OpenBenchesBlack,
    secondary = OpenBenchesGroundBrown,
    onSecondary = OpenBenchesCream,
    secondaryContainer = OpenBenchesGroundBrown,
    onSecondaryContainer = OpenBenchesCream,
    tertiary = OpenBenchesGrassGreenLight,
    onTertiary = OpenBenchesBlack,
    background = OpenBenchesDarkBackground,
    onBackground = OpenBenchesCream,
    surface = OpenBenchesDarkSurface,
    onSurface = OpenBenchesCream,
    surfaceVariant = OpenBenchesDarkSurfaceVariant,
    onSurfaceVariant = OpenBenchesCream,
    outline = OpenBenchesDarkOutline,
    error = OpenBenchesDanger,
    onError = OpenBenchesCream,
)

/** Text-link colour with enough contrast on cream (light) and dark surfaces. */
@Composable
fun openBenchesLinkColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        OpenBenchesLinkOnDark
    } else {
        OpenBenchesLink
    }

@Composable
fun OpenBenchesTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) OpenBenchesDarkColorScheme else OpenBenchesLightColorScheme,
        typography = Typography,
        shapes = OpenBenchesShapes,
        content = content,
    )
}
