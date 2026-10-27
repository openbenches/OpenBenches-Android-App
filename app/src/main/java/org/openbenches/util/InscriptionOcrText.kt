package org.openbenches.util

import kotlin.math.abs

internal data class OcrLineInput(
    val top: Int,
    val left: Int,
    val height: Int,
    val text: String,
)

/**
 * Builds inscription text from ordered line inputs (top-to-bottom, left-to-right).
 */
internal fun assembleInscriptionTextFromLines(lines: List<OcrLineInput>): String {
    if (lines.isEmpty()) return ""

    if (lines.all { it.top == 0 && it.left == 0 }) {
        return lines.joinToString("\n") { it.text }
    }

    val rowThreshold = lines.map { it.height }
        .filter { it > 0 }
        .let { heights ->
            if (heights.isEmpty()) 24 else (heights.average() * 0.55).toInt().coerceAtLeast(12)
        }

    val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
    val rows = mutableListOf<MutableList<OcrLineInput>>()
    for (line in sorted) {
        val lastRow = rows.lastOrNull()
        if (lastRow != null && lastRow.isNotEmpty() &&
            abs(line.top - lastRow.first().top) <= rowThreshold
        ) {
            lastRow.add(line)
        } else {
            rows.add(mutableListOf(line))
        }
    }

    return rows.joinToString("\n") { row ->
        row.sortedBy { it.left }.joinToString(" ") { it.text }
    }.trim()
}
