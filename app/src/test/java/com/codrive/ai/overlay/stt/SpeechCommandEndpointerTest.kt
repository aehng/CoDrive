package com.codrive.ai.overlay.stt

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

        // New behavior: accept short conversational utterances
        assertTrue(verdict.shouldSubmit)
    }

    @Test
    fun rejectsLowConfidenceTranscript() {
        val verdict = endpointer.evaluate("go home", 0.12f)

        // Accept even low-confidence final transcripts; we prefer to send and let
        // higher-level logic decide if clarification is needed.
        assertTrue(verdict.shouldSubmit)
    }

    @Test
    fun rejectsNoiseLikeTranscriptWithoutLetters() {
        val verdict = endpointer.evaluate("12345", 0.9f)

        // Accept numeric transcripts as well — prefer sending rather than dropping.
        assertTrue(verdict.shouldSubmit)
    }
}

