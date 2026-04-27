package com.codrive.ai.overlay.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCommandEndpointerTest {
    private val endpointer = SpeechCommandEndpointer()

    @Test
    fun acceptsSpeechLikeCommandWithGoodConfidence() {
        val verdict = endpointer.evaluate("open quick settings", 0.84f)

        assertTrue(verdict.shouldSubmit)
        assertTrue(verdict.command.contains("quick settings"))
    }

    @Test
    fun rejectsVeryShortTranscript() {
        val verdict = endpointer.evaluate("ok", 0.9f)

        assertFalse(verdict.shouldSubmit)
    }

    @Test
    fun rejectsLowConfidenceTranscript() {
        val verdict = endpointer.evaluate("go home", 0.12f)

        assertFalse(verdict.shouldSubmit)
    }

    @Test
    fun rejectsNoiseLikeTranscriptWithoutLetters() {
        val verdict = endpointer.evaluate("12345", 0.9f)

        assertFalse(verdict.shouldSubmit)
    }
}

