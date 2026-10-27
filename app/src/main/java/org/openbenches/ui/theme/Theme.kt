package org.openbenches.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun OpenBenchesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OpenBenchesLightColorScheme,
        typography = Typography,
        shapes = OpenBenchesShapes,
        content = content,
    )
}
