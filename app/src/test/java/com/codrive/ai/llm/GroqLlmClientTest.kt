package com.codrive.ai.llm

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GroqLlmClientTest {
    @Test
    fun inferFailsClosedWhenApiKeyMissing() {
        var calls = 0
        val client = GroqLlmClient(
            apiKey = "",
            transport = GroqTransport { _, _ ->
                calls += 1
                200 to "{}"
            },
        )

        val decision = client.infer("tap next", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.requiresClarification())
        assertEquals(0, calls)
    }

    @Test
    fun inferRetriesOn429ThenParsesSuccess() {
        var calls = 0
        var sleptFor = 0L
        val response = groqResponseContent(
            "{\"action_type\":\"CLICK\",\"target_index\":1,\"text_to_type\":\"\",\"tool_query\":\"\",\"voice_feedback\":\"Done\",\"confidence_score\":0.9}"
        )

        val client = GroqLlmClient(
            apiKey = "test-key",
            transport = GroqTransport { _, _ ->
                calls += 1
                if (calls == 1) 429 to "rate limited" else 200 to response
            },
            sleeper = { sleptFor = it },
            jitterProvider = { 0L },
        )

        val decision = client.infer("tap", sampleUiMap())

        assertEquals(2, calls)
        assertTrue(sleptFor >= 600L)
        assertEquals(ActionType.CLICK, decision.actionType)
        assertEquals(1, decision.targetIndex)
    }

    @Test
    fun inferRetriesOnIOExceptionThenFailsClosed() {
        var calls = 0
        val client = GroqLlmClient(
            apiKey = "test-key",
            transport = GroqTransport { _, _ ->
                calls += 1
                throw IOException("network down")
            },
            sleeper = {},
            jitterProvider = { 0L },
        )

        val decision = client.infer("tap", sampleUiMap())

        assertTrue(calls >= 2)
        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.requiresClarification())
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

    private fun groqResponseContent(content: String): String = JSONObject()
        .put("choices", JSONArray().put(
            JSONObject().put(
                "message",
                JSONObject().put("content", content),
            )
        ))
        .toString()
}


