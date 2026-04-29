package com.codrive.ai.llm

import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLlmClientTest {

    private fun makeResponseBody(body: String): Response {
        val request = Request.Builder().url("https://example.com/").build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    private fun makeResponse(code: Int, body: String): Response {
        val request = Request.Builder().url("https://example.com/").build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    private class FakeCall(private val response: Response, private val delayMs: Long = 0) : Call {
        private val canceled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)

        override fun execute(): Response {
            executed.set(true)
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs)
                } catch (e: InterruptedException) {
                }
            }
            if (canceled.get()) throw IOException("Canceled")
            return response
        }

        override fun enqueue(responseCallback: Callback) { throw UnsupportedOperationException() }
        override fun isExecuted(): Boolean = executed.get()
        override fun cancel() { canceled.set(true) }
        override fun isCanceled(): Boolean = canceled.get()
        override fun clone(): Call = FakeCall(response, delayMs)
        override fun request(): Request = response.request
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
    }

    @Test
    fun parseSuccessDelegatesToParser() {
        val content = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"action_type\\\":\\\"RESPOND\\\",\\\"voice_feedback\\\":\\\"Hi\\\",\\\"confidence_score\\\":0.9}\"}]}}]}"
        val resp = makeResponseBody(content)

        val transport = GeminiTransport { _, _ -> FakeCall(resp) }
        val client = GeminiLlmClient("fake-key", "gemma-3-27b-instruct", transport = transport)

        val uiMap = PrunedUiMap(1L, listOf())
        val decision = client.infer("say hi", uiMap)

        assertEquals("Hi", decision.voiceFeedback)
        assertEquals(0.9, decision.confidenceScore, 0.0)
    }

    @Test
    fun cancelAbortsLongCall() {
        val content = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"action_type\\\":\\\"RESPOND\\\",\\\"voice_feedback\\\":\\\"pong\\\",\\\"confidence_score\\\":0.5}\"}]}}]}"
        val resp = makeResponseBody(content)
        val longCall = FakeCall(resp, 3000)
        val transport = GeminiTransport { _, _ -> longCall }
        val client = GeminiLlmClient("fake-key", "gemma-3-27b-instruct", transport = transport)

        val uiMap = PrunedUiMap(1L, listOf())

        val thread = Thread {
            val result = client.infer("wait", uiMap)
            // When canceled, the client returns a clarification with voiceFeedback containing "canceled" or similar
            assertEquals(true, result.voiceFeedback.lowercase().contains("cancel"))
        }
        thread.start()

        // wait a bit to ensure the call started
        Thread.sleep(200)
        client.cancel()
        thread.join(4000)
    }

    @Test
    fun fallbackUsedOnAuthFailure() {
        val unauthorized = makeResponse(401, "Unauthorized")

        val successContent = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"action_type\\\":\\\"RESPOND\\\",\\\"voice_feedback\\\":\\\"pong\\\",\\\"confidence_score\\\":0.5}\"}]}}]}"
        val successResp = makeResponse(200, successContent)

        val primary = GeminiTransport { _, _ -> FakeCall(unauthorized) }
        val fallback = GeminiTransport { _, _ -> FakeCall(successResp) }

        val fallbackFactory: (String, String) -> GeminiTransport = { _, _ -> fallback }

        val client = GeminiLlmClient("fake-key", "gemma-3-27b-instruct", transport = primary, fallbackTransportFactory = fallbackFactory)

        val uiMap = PrunedUiMap(1L, listOf<PrunedNodeEntry>())
        val decision = client.infer("please pong", uiMap)

        assertEquals("pong", decision.voiceFeedback)
        assertEquals(0.5, decision.confidenceScore, 0.0)
    }

    @Test
    fun validateApiKeyUsesFallback() {
        val unauthorized = makeResponse(401, "Unauthorized")
        val successContent = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"action_type\\\":\\\"RESPOND\\\",\\\"voice_feedback\\\":\\\"pong\\\",\\\"confidence_score\\\":0.5}\"}]}}]}"
        val successResp = makeResponse(200, successContent)

        val primary = GeminiTransport { _, _ -> FakeCall(unauthorized) }
        val fallback = GeminiTransport { _, _ -> FakeCall(successResp) }

        val result = GeminiLlmClient.validateApiKey(
            "fake-key",
            "gemma-3-27b-instruct",
            2000,
            transportFactory = { _, _ -> primary },
            fallbackFactory = { _, _ -> fallback }
        )

        assertEquals(true, result.first)
    }
}



