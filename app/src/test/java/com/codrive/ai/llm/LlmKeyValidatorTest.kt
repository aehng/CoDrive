package com.codrive.ai.llm

import com.codrive.ai.settings.LlmProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmKeyValidatorTest {
    @Test
    fun validateRejectsMissingKey() {
        val validator = LlmKeyValidator { _, _, _ -> 200 to "{}" }

        val result = validator.validate(LlmProvider.GROQ, "qwen/qwen3-32b", "")

        assertFalse(result.isValid)
        assertTrue(result.message.contains("required", ignoreCase = true))
    }

    @Test
    fun validateAcceptsGroqSuccess() {
        val validator = LlmKeyValidator { _, _, _ -> 200 to "{}" }

        val result = validator.validate(LlmProvider.GROQ, "qwen/qwen3-32b", "gsk_test")

        assertTrue(result.isValid)
    }

    @Test
    fun validateRejectsUnauthorized() {
        val validator = LlmKeyValidator { _, _, _ -> 401 to "unauthorized" }

        val result = validator.validate(LlmProvider.GROQ, "qwen/qwen3-32b", "bad_key")

        assertFalse(result.isValid)
        assertTrue(result.message.contains("invalid", ignoreCase = true))
    }

    @Test
    fun validateRejectsNonGroqForTracerBullet() {
        val validator = LlmKeyValidator { _, _, _ -> 200 to "{}" }

        val result = validator.validate(LlmProvider.GEMINI, "gemini-2.5-flash", "key")

        assertFalse(result.isValid)
        assertTrue(result.message.contains("not wired", ignoreCase = true))
    }
}


