package org.openbenches.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed

/**
 * Adds pinch-to-zoom and pan support to a composable.
 * Usage: Modifier.zoomable()
 */
fun Modifier.zoomable(): Modifier = composed {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    this
        .onGloballyPositioned { size = it.size }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val maxX = (size.width * (scale - 1)) / 2f
                val maxY = (size.height * (scale - 1)) / 2f
                val newOffset = offset + pan
                offset = Offset(
                    x = newOffset.x.coerceIn(-maxX, maxX),
                    y = newOffset.y.coerceIn(-maxY, maxY)
                )
            }
        }
        .graphicsLayer {
            translationX = offset.x
            translationY = offset.y
            scaleX = scale
            scaleY = scale
        }
} 