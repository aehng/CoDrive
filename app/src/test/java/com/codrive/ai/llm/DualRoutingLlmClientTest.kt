package com.codrive.ai.llm

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import org.junit.Assert.assertEquals
import org.junit.Test

class DualRoutingLlmClientTest {
    @Test
    fun routesByConfiguredRatio() {
        val groqStub = CountingClient("groq")
        val geminiStub = CountingClient("gemini")
        val dual = DualRoutingLlmClient(groqStub, geminiStub, 20)

        repeat(10) { dual.infer("cmd", PrunedUiMap(1L, emptyList())) }

        assertEquals(2, groqStub.calls)
        assertEquals(8, geminiStub.calls)
    }

    @Test
    fun routesOnlyGeminiWhenGroqPercentIsZero() {
        val groqStub = CountingClient("groq")
        val geminiStub = CountingClient("gemini")
        val dual = DualRoutingLlmClient(groqStub, geminiStub, 0)

        repeat(5) { dual.infer("cmd", PrunedUiMap(1L, emptyList())) }

        assertEquals(0, groqStub.calls)
        assertEquals(5, geminiStub.calls)
    }

    private class CountingClient(private val label: String) : LlmClient {
        var calls: Int = 0
            private set

        override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
            calls++
            return AgentDecision(
                actionType = ActionType.RESPOND,
                voiceFeedback = label,
                confidenceScore = 1.0,
            )
        }
    }
}
