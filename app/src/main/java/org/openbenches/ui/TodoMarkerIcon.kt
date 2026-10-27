package org.openbenches.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.content.Context

/** Distinct map marker for "to do" bench locations (seen, not yet photographed). */
fun createTodoMarkerDrawable(context: Context): BitmapDrawable {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Soft shadow
    paint.color = AndroidColor.BLACK
    paint.alpha = 40
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f + 2f, size / 2f + 3f, size / 2f - 4f, paint)

    // Amber fill
    paint.color = AndroidColor.parseColor("#FF9800")
    paint.alpha = 255
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, paint)

    // White ring
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8f, paint)

    // Exclamation mark
    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.WHITE
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 52f
    val text = "!"
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, paint)

    return BitmapDrawable(context.resources, bitmap)
}
