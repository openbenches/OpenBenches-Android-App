package org.openbenches.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** ~1rem radius used on openbenches.org cards and map frame */
val OpenBenchesShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val OpenBenchesCardShape = RoundedCornerShape(16.dp)
val OpenBenchesHeaderShape = RoundedCornerShape(20.dp)
