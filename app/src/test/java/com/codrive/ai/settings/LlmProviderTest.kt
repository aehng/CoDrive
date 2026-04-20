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
        assertFalse(LlmProvider.GEMINI.isTracerBulletSupported())
        assertFalse(LlmProvider.OPENAI.isTracerBulletSupported())
    }
}

