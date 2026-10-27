package org.openbenches.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InscriptionOcrTextTest {

    @Test
    fun assembleInscriptionTextFromLines_emptyWhenNoLines() {
        assertEquals("", assembleInscriptionTextFromLines(emptyList()))
    }

    @Test
    fun assembleInscriptionTextFromLines_sortsTopToBottom() {
        val lines = listOf(
            OcrLineInput(top = 120, left = 10, height = 40, text = "world"),
            OcrLineInput(top = 10, left = 10, height = 40, text = "Hello"),
        )
        assertEquals("Hello\nworld", assembleInscriptionTextFromLines(lines))
    }

    @Test
    fun assembleInscriptionTextFromLines_joinsSameRowLeftToRight() {
        val lines = listOf(
            OcrLineInput(top = 50, left = 200, height = 30, text = "memory"),
            OcrLineInput(top = 52, left = 10, height = 30, text = "In"),
        )
        assertEquals("In memory", assembleInscriptionTextFromLines(lines))
    }
}
