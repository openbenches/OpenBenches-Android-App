package org.openbenches.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt

/** Base cluster diameter at close zoom (matches legacy 160px markers). */
private const val BASE_CLUSTER_ICON_PX = 160

/**
 * Cluster bubble diameter in screen pixels — larger when zoomed out or when the cluster
 * represents more benches.
 */
fun clusterMarkerSizePx(zoomLevel: Double, clusterBenchCount: Int): Int {
    val zoomSize = when {
        zoomLevel < 5.0 -> 248
        zoomLevel < 7.0 -> 220
        zoomLevel < 9.0 -> 200
        zoomLevel < 11.0 -> 184
        zoomLevel < 13.0 -> 172
        zoomLevel < 15.0 -> 160
        else -> 148
    }
    val countBonus = when {
        clusterBenchCount >= 5_000 -> 32
        clusterBenchCount >= 1_000 -> 24
        clusterBenchCount >= 100 -> 14
        clusterBenchCount >= 50 -> 8
        else -> 0
    }
    return (zoomSize + countBonus).coerceIn(132, 288)
}

fun createClusterIcon(
    count: Int,
    context: Context,
    zoomLevel: Double,
): BitmapDrawable {
    val size = clusterMarkerSizePx(zoomLevel, count)
    val scale = size / BASE_CLUSTER_ICON_PX.toFloat()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    val backgroundColor = when {
        count < 10 -> "#4CAF50".toColorInt()
        count < 50 -> "#FFC107".toColorInt()
        count < 100 -> "#FF9800".toColorInt()
        else -> "#F44336".toColorInt()
    }

    val paint = Paint().apply {
        isAntiAlias = true
    }

    paint.apply {
        color = backgroundColor
        alpha = 220
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    paint.apply {
        color = AndroidColor.BLACK
        alpha = 30
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8f * scale, paint)

    paint.apply {
        color = AndroidColor.WHITE
        alpha = 255
        style = Paint.Style.STROKE
        strokeWidth = 8f * scale
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f * scale, paint)

    paint.apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
        textSize = when {
            count < 10 -> 48f * scale
            count < 100 -> 40f * scale
            count < 1000 -> 32f * scale
            else -> 28f * scale
        }
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    val countText = when {
        count >= 1000 -> "${count / 1000}k"
        else -> count.toString()
    }
    val textBounds = android.graphics.Rect()
    paint.getTextBounds(countText, 0, countText.length, textBounds)
    canvas.drawText(
        countText,
        size / 2f,
        size / 2f + textBounds.height() / 2f,
        paint,
    )

    return bitmap.toDrawable(context.resources)
}
