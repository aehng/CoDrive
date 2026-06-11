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

class OpenRouterLlmClientTest {
    @Test
    fun inferReturnsActionableMessageForUnauthorizedKey() {
        val client = OpenRouterLlmClient(
            apiKey = "bad-key",
            model = "openrouter/owl-alpha",
            transport = OpenRouterTransport { _, _ ->
                401 to "{\"error\":{\"message\":\"invalid_api_key\"}}"
            },
            sleeper = {},
            jitterProvider = { 0L },
        )

        val decision = client.infer("tap", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.voiceFeedback.contains("API key was rejected"))
        assertTrue(decision.voiceFeedback.contains("invalid_api_key"))
    }

    @Test
    fun inferBudgetsUiPayloadBeforeSending() {
        var sentRequest = ""
        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            model = "openrouter/owl-alpha",
            transport = OpenRouterTransport { requestBody, _ ->
                sentRequest = requestBody
                401 to "{\"error\":{\"message\":\"invalid\"}}"
            },
            sleeper = {},
            jitterProvider = { 0L },
        )

        client.infer("tap", largeUiMap())

        val root = JSONObject(sentRequest)
        val messages = root.getJSONArray("messages")
        val userContent = messages.getJSONObject(1).getString("content")
        val payload = JSONObject(userContent)
        val uiArray = payload.getJSONArray("ui_map")
        val responseFormat = root.getJSONObject("response_format")

        assertTrue(uiArray.length() <= 80)
        assertEquals("json_object", responseFormat.getString("type"))
        assertEquals("openrouter/owl-alpha", root.getString("model"))
    }

    @Test
    fun inferFailsClosedWhenApiKeyMissing() {
        var calls = 0
        val client = OpenRouterLlmClient(
            apiKey = "",
            model = "openrouter/owl-alpha",
            transport = OpenRouterTransport { _, _ ->
                calls += 1
                200 to "{}"
            },
        )

        val decision = client.infer("tap next", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.requiresClarification())
        assertEquals(0, calls)
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

    private fun largeUiMap(): PrunedUiMap = PrunedUiMap(
        snapshotId = 2L,
        entries = (0 until 120).map { index ->
            PrunedNodeEntry(
                index = index,
                role = UiRole.BUTTON,
                bounds = intArrayOf(0, 0, 10, 10),
                text = "x".repeat(300),
                contentDescription = "desc".repeat(80),
                isInteractive = true,
            )
        }
    )

    @Suppress("unused")
    private fun responseContent(content: String): String = JSONObject()
        .put("choices", JSONArray().put(
            JSONObject().put(
                "message",
                JSONObject().put("content", content),
            )
        ))
        .toString()
}
