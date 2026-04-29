package com.codrive.ai.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProviderTest {
    @Test
    fun fromStorageValueFallsBackToGroq() {
        assertEquals(LlmProvider.GROQ, LlmProvider.fromStorageValue(""))
        assertEquals(LlmProvider.GROQ, LlmProvider.fromStorageValue("unknown"))
    }

    @Test
    fun fromStorageValueParsesKnownProvider() {
        assertEquals(LlmProvider.GEMINI, LlmProvider.fromStorageValue("gemini"))
        assertEquals(LlmProvider.OPENAI, LlmProvider.fromStorageValue("OPENAI"))
    }

    @Test
    fun tracerBulletSupportIsGroqOnlyForNow() {
        assertTrue(LlmProvider.GROQ.isTracerBulletSupported())
        // Gemini is now wired in the tracer-bullet path (skeleton transport). OpenAI remains unsupported.
        assertTrue(LlmProvider.GEMINI.isTracerBulletSupported())
        assertFalse(LlmProvider.OPENAI.isTracerBulletSupported())
    }

    @Test
    fun geminiDisplayNameIsModelLabel() {
        assertEquals("Gemini", LlmProvider.GEMINI.displayName())
    }
}

