package org.openbenches.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class FullscreenZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var layoutSize by mutableStateOf(IntSize.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

@Composable
fun rememberFullscreenZoomState(): FullscreenZoomState = remember { FullscreenZoomState() }

fun Modifier.fullscreenZoomGraphics(zoomState: FullscreenZoomState): Modifier = graphicsLayer {
    translationX = zoomState.offset.x
    translationY = zoomState.offset.y
    scaleX = zoomState.scale
    scaleY = zoomState.scale
}

/** Double-tap on the fullscreen container to zoom in/out without blocking pager swipes. */
fun Modifier.fullscreenDoubleTapZoom(zoomState: FullscreenZoomState): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onDoubleTap = {
            if (zoomState.scale > 1.001f) {
                zoomState.reset()
            } else {
                zoomState.scale = 2.5f
            }
        },
    )
}

/**
 * Drag to pan while zoomed in. Not applied at scale 1 so [androidx.compose.foundation.pager.HorizontalPager] can swipe.
 */
fun Modifier.fullscreenPhotoGestures(zoomState: FullscreenZoomState): Modifier =
    if (zoomState.scale > 1.001f) {
        onGloballyPositioned { zoomState.layoutSize = it.size }
            .pointerInput(zoomState.scale, zoomState.layoutSize) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    applyPanChange(zoomState, dragAmount)
                }
            }
    } else {
        this
    }

/**
 * Swipe between photos via horizontal drag (used when pager built-in scroll is blocked by the dialog/zoom layer).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.fullscreenPagerSwipe(
    pagerState: PagerState,
    scope: CoroutineScope,
    zoomState: FullscreenZoomState,
): Modifier =
    if (pagerState.pageCount <= 1) {
        this
    } else {
        pointerInput(pagerState.currentPage, pagerState.pageCount, zoomState.scale) {
            if (zoomState.scale > 1.001f) return@pointerInput
            var dragTotal = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, delta -> dragTotal += delta },
                onDragEnd = {
                    val page = pagerState.currentPage
                    when {
                        dragTotal > 80f && page > 0 ->
                            scope.launch { pagerState.animateScrollToPage(page - 1) }
                        dragTotal < -80f && page < pagerState.pageCount - 1 ->
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }
                    dragTotal = 0f
                },
                onDragCancel = { dragTotal = 0f },
            )
        }
    }

private fun applyPanChange(zoomState: FullscreenZoomState, panChange: Offset) {
    if (zoomState.scale > 1f) {
        val maxX = (zoomState.layoutSize.width * (zoomState.scale - 1)) / 2f
        val maxY = (zoomState.layoutSize.height * (zoomState.scale - 1)) / 2f
        val newOffset = zoomState.offset + panChange
        zoomState.offset = Offset(
            x = newOffset.x.coerceIn(-maxX, maxX),
            y = newOffset.y.coerceIn(-maxY, maxY),
        )
    }
}
