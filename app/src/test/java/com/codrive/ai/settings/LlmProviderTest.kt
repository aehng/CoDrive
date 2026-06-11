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
        assertEquals(LlmProvider.OPENROUTER, LlmProvider.fromStorageValue("openrouter"))
        assertEquals(LlmProvider.OPENAI, LlmProvider.fromStorageValue("OPENAI"))
    }

    @Test
    fun tracerBulletSupportIncludesOpenRouter() {
        assertTrue(LlmProvider.GROQ.isTracerBulletSupported())
        assertTrue(LlmProvider.GEMINI.isTracerBulletSupported())
        assertTrue(LlmProvider.OPENROUTER.isTracerBulletSupported())
        assertFalse(LlmProvider.OPENAI.isTracerBulletSupported())
    }

    @Test
    fun providerDisplayNamesAreStable() {
        assertEquals("Gemini", LlmProvider.GEMINI.displayName())
        assertEquals("OpenRouter", LlmProvider.OPENROUTER.displayName())
    }
}

