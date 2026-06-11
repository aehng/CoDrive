package com.codrive.ai.llm

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import com.codrive.ai.settings.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmClientFactoryTest {
    @Test
    fun createForMissingKeyReturnsSafeClient() {
        val client = LlmClientFactory.createFor(LlmProvider.GROQ, "qwen/qwen3-32b", "")

        val decision = client.infer("tap", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.voiceFeedback.contains("not configured", ignoreCase = true))
    }

    @Test
    fun createForUnsupportedProviderReturnsSafeClient() {
        // When GEMINI is selected but no key is provided the factory should return a safe client
        val client = LlmClientFactory.createFor(LlmProvider.GEMINI, "gemini-2.5-flash", "")

        val decision = client.infer("tap", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.voiceFeedback.contains("not configured", ignoreCase = true))
    }

    @Test
    fun createForGroqReturnsGroqClient() {
        val client = LlmClientFactory.createFor(LlmProvider.GROQ, "qwen/qwen3-32b", "key")

        assertTrue(client is GroqLlmClient)
    }

    @Test
    fun createForOpenRouterReturnsOpenRouterClient() {
        val client = LlmClientFactory.createFor(LlmProvider.OPENROUTER, "openrouter/owl-alpha", "key")

        assertTrue(client is OpenRouterLlmClient)
    }

    private fun sampleUiMap(): PrunedUiMap = PrunedUiMap(
        snapshotId = 1L,
        entries = listOf(
            PrunedNodeEntry(
                index = 1,
                role = UiRole.BUTTON,
                bounds = intArrayOf(0, 0, 10, 10),
                text = "Next",
                isInteractive = true,
            )
        )
    )
}

