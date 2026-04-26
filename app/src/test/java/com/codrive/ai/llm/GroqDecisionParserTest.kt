package com.codrive.ai.llm

import com.codrive.ai.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun parseAcceptsLowercaseRespondActionType() {
        val parser = GroqDecisionParser()
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"action_type\":\"respond\",\"target_index\":0,\"text_to_type\":\"\",\"tool_query\":\"\",\"voice_feedback\":\"Hi there\",\"confidence_score\":0.93}"
                  }
                }
              ]
            }
        """.trimIndent()

        val decision = parser.parse(response)

        assertEquals(ActionType.RESPOND, decision.actionType)
        assertEquals("Hi there", decision.voiceFeedback)
    }

    @Test
    fun parseRespondSoftDefaultsMissingOptionalFields() {
        val parser = GroqDecisionParser()
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"action_type\":\"RESPOND\",\"voice_feedback\":\"Hello!\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val decision = parser.parse(response)

        assertEquals(ActionType.RESPOND, decision.actionType)
        assertEquals(0, decision.targetIndex)
        assertEquals("", decision.textToType)
        assertEquals("", decision.toolQuery)
        assertEquals("Hello!", decision.voiceFeedback)
        assertEquals(1.0, decision.confidenceScore, 0.0)
    }

    @Test
    fun parseFailureTelemetrySanitizesRawContent() {
        var reason = ""
        var sanitizedRaw = ""
        val parser = GroqDecisionParser(
            onParseFailure = { r, raw ->
                reason = r
                sanitizedRaw = raw
            }
        )

        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"action_type\":\"CLICK\",\"note\":\"Bearer secret-token\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val decision = parser.parse(response)

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(reason.contains("Missing required fields"))
        assertFalse(sanitizedRaw.contains("secret-token"))
        assertTrue(sanitizedRaw.contains("bearer [redacted]", ignoreCase = true))
    }
}

