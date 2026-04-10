package com.codrive.ai.llm

import com.codrive.ai.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqDecisionParserTest {
    @Test
    fun parseReadsStrictDecisionAndLogsReasoning() {
        var loggedReasoning: String? = null
        val parser = GroqDecisionParser(onReasoning = { loggedReasoning = it })

        val response = """
            {
              "choices": [
                {
                  "message": {
                    "reasoning_content": "Need one tap.",
                    "content": "{\"action_type\":\"CLICK\",\"target_index\":3,\"text_to_type\":\"\",\"tool_query\":\"\",\"voice_feedback\":\"Tapping now\",\"confidence_score\":0.92}"
                  }
                }
              ]
            }
        """.trimIndent()

        val decision = parser.parse(response)

        assertEquals(ActionType.CLICK, decision.actionType)
        assertEquals(3, decision.targetIndex)
        assertEquals("Tapping now", decision.voiceFeedback)
        assertEquals(0.92, decision.confidenceScore, 0.0)
        assertEquals("Need one tap.", loggedReasoning)
    }

    @Test
    fun parseFailsClosedWhenSchemaIsMissingRequiredFields() {
        val parser = GroqDecisionParser()
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"action_type\":\"CLICK\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val decision = parser.parse(response)

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.requiresClarification())
    }

    @Test
    fun extractFirstJsonObjectFindsEmbeddedJsonBlock() {
        val parser = GroqDecisionParser()
        val text = "noise <think>hidden</think> {\"a\":1,\"b\":{\"c\":2}} tail"

        val extracted = parser.extractFirstJsonObject(text)

        assertEquals("{\"a\":1,\"b\":{\"c\":2}}", extracted)
    }
}

