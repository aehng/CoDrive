package com.codrive.ai.llm

import com.codrive.ai.BuildConfig
import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.AgentPolicy
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.math.pow

/**
 * Gemini LLM client (Phase A tracer-bullet).
 * - Uses an OkHttp-based transport for cancellable requests.
 * - Reuses GroqDecisionParser for extracting the JSON decision from model output.
 * - Provides a simple retry/backoff loop using AgentPolicy settings.
 */
fun interface GeminiTransport {
    /** Return an OkHttp Call which the caller may execute or cancel. */
    @Throws(IOException::class)
    fun postCall(requestBody: String, timeoutMillis: Int): Call
}

class GeminiLlmClient @JvmOverloads constructor(
    private val apiKey: String,
    private val model: String,
    private val parser: GroqDecisionParser = GroqDecisionParser(),
    private val transport: GeminiTransport = defaultTransportWithQueryKey(apiKey, model),
    private val fallbackTransportFactory: (String, String) -> GeminiTransport = ::defaultTransportWithHeaderKey,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterProvider: () -> Long = { Random.nextLong(0, 250) },
) : LlmClient {

    @Volatile
    private var activeCall: Call? = null

    private fun logIfDebug(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            try { Log.d(tag, message) } catch (_: Exception) { }
        }
    }

    override fun cancel() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) { }
    }

    override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
        if (apiKey.isBlank()) {
            return clarification("Gemini is not configured yet.")
        }

        var attempt = 0
        while (attempt <= AgentPolicy.groqRetryAttempts) {
            val requestBody = buildRequestBody(command, uiMap)
            logIfDebug("CoDrive.Gemini", "Request: ${requestBody.take(400)}")
            try {
                val call = transport.postCall(requestBody, AgentPolicy.groqRequestTimeoutMillis.toInt())
                activeCall = call
                val response = call.execute()
                try {
                    val statusCode = response.code
                    val responseBody = response.body?.string().orEmpty()
                    logIfDebug("CoDrive.Gemini", "Response ($statusCode): ${responseBody.take(400)}")

                    if (statusCode in 200..299) {
                        return parseGeminiResponse(responseBody)
                    }

                    // If auth is rejected, try the x-goog-api-key header transport once before failing.
                    if ((statusCode == 401 || statusCode == 403) && attempt == 0) {
                        logIfDebug("CoDrive.Gemini", "Auth rejected, attempting x-goog-api-key header fallback (no key logged)")
                        try {
                            val fallbackCall = fallbackTransportFactory(apiKey, model).postCall(requestBody, AgentPolicy.groqRequestTimeoutMillis.toInt())
                            activeCall = fallbackCall
                            val fbResponse = fallbackCall.execute()
                            try {
                                val fbStatus = fbResponse.code
                                val fbBody = fbResponse.body?.string().orEmpty()
                                logIfDebug("CoDrive.Gemini", "Fallback Response ($fbStatus): ${fbBody.take(200)}")
                                if (fbStatus in 200..299) {
                                    return parseGeminiResponse(fbBody)
                                }
                                if ((fbStatus == 429 || fbStatus in 500..599) && attempt < AgentPolicy.groqRetryAttempts) {
                                    sleepBackoff(attempt)
                                    attempt += 1
                                    continue
                                }
                                // If fallback also fails, fall through to normal error handling below.
                            } finally {
                                fbResponse.close()
                                activeCall = null
                            }
                        } catch (e: IOException) {
                            if (activeCall?.isCanceled() == true) {
                                return clarification("Request canceled")
                            }
                            // treat fallback IO errors like the primary transport IO errors
                            if (attempt < AgentPolicy.groqRetryAttempts) {
                                sleepBackoff(attempt)
                                attempt += 1
                                continue
                            }
                            return clarification("I could not reach Gemini right now.")
                        }
                    }

                    if (statusCode == 429 && attempt < AgentPolicy.groqRetryAttempts) {
                        sleepBackoff(attempt)
                        attempt += 1
                        continue
                    }

                    if (statusCode in 500..599 && attempt < AgentPolicy.groqRetryAttempts) {
                        sleepBackoff(attempt)
                        attempt += 1
                        continue
                    }

                    return clarification(messageForHttpFailure(statusCode, responseBody))
                } finally {
                    response.close()
                    activeCall = null
                }
            } catch (e: IOException) {
                if (activeCall?.isCanceled() == true) {
                    return clarification("Request canceled")
                }
                if (attempt < AgentPolicy.groqRetryAttempts) {
                    sleepBackoff(attempt)
                    attempt += 1
                    continue
                }
                return clarification("I could not reach Gemini right now.")
            }
        }

        return clarification("I need clarification before I act.")
    }

    private fun sleepBackoff(attempt: Int) {
        val factor = 2.0.pow(attempt.toDouble()).toLong()
        val delay = AgentPolicy.groqRetryBackoffMillis * factor + jitterProvider()
        sleeper(delay)
    }

    private fun buildRequestBody(command: String, uiMap: PrunedUiMap): String {
        // Gemini generateContent schema (v1) does not accept systemInstruction/responseMimeType.
        val root = org.json.JSONObject()
        root.put("contents", org.json.JSONArray().apply {
            put(
                org.json.JSONObject()
                    .put("role", "user")
                    .put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", systemMessage().getString("content"))))
            )
            put(
                org.json.JSONObject()
                    .put("role", "user")
                    .put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", userMessage(command, uiMap).getString("content"))))
            )
        })
        root.put(
            "generationConfig",
            org.json.JSONObject()
                .put("temperature", 0)
        )
        return root.toString()
    }

    private fun systemMessage(): org.json.JSONObject = org.json.JSONObject()
        .put("role", "system")
        .put(
            "content",
            """
            You are CoDrive, an Android automation assistant. CRITICAL RULE: Return ONLY the raw JSON object and nothing else. Do NOT output markdown, explanations, chain-of-thought, or any text outside the JSON object.

            Mandatory Schema (must be valid JSON exactly):
            {
              "action_type": "CLICK|TYPE|SCROLL|HOME|BACK|RECENTS|OPEN_NOTIFICATIONS|OPEN_QUICK_SETTINGS|OPEN_POWER_DIALOG|LOCK_SCREEN|TAKE_SCREENSHOT|SWIPE_DOWN|SWIPE_UP|SWIPE_LEFT|SWIPE_RIGHT|SEARCH_MEMORY|RESPOND|FINISH",
              "target_index": 0,
              "text_to_type": "",
              "tool_query": "",
              "voice_feedback": "",
              "confidence_score": 0.0
            }

            Rules:
            - If action_type is RESPOND or a global/system gesture action, set target_index to 0.
            - If action_type is CLICK/TYPE/SCROLL, use a valid index from ui_map for target_index.
            - Prefer SWIPE_UP/SWIPE_DOWN/SWIPE_LEFT/SWIPE_RIGHT for viewport movement gestures.

            UI MAP LEGEND:
            Each entry in ui_map is a 4-item tuple: [index:int, role:char, [center_x:int, center_y:int], text:string]
            - role: 't' = text, 'b' = button, 'i' = input, 'c' = checkbox
            - text MUST be present; use an empty string when no visible text exists.

            Example ui_map entry: [4, "b", [915, 573], "Gallery"]
            """.trimIndent()
        )

    private fun userMessage(command: String, uiMap: PrunedUiMap): org.json.JSONObject {
        val payload = org.json.JSONObject()
            .put("command", command.trim())
            .put("snapshot_id", uiMap.snapshotId)
            .put("ui_map", uiMap.entries.toBudgetedUiJson())
            .put("token_budget_soft", AgentPolicy.groqSoftTokenBudget)
            .put("token_budget_hard", AgentPolicy.groqHardTokenBudget)
        return org.json.JSONObject()
            .put("role", "user")
            .put("content", payload.toString())
    }

    private fun parseGeminiResponse(responseBody: String): AgentDecision {
        val extracted = extractGeminiText(responseBody)
        if (extracted.isNullOrBlank()) {
            return clarification("Gemini returned an unexpected response.")
        }

        // Wrap Gemini text output into a Groq/OpenAI-style response so the parser can reuse logic.
        val wrapped = org.json.JSONObject().apply {
            put("choices", org.json.JSONArray().put(
                org.json.JSONObject().put("message", org.json.JSONObject().put("content", extracted))
            ))
        }
        return parser.parse(wrapped.toString())
    }

    private fun extractGeminiText(responseBody: String): String? {
        return runCatching<String?> {
            val root = org.json.JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return@runCatching null
            if (candidates.length() == 0) return@runCatching null
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i)
                    val text = part?.optString("text", "") ?: ""
                    sb.append(text)
                }
                return@runCatching sb.toString()
            }
            // Fallback if the API returns a single text field directly
            first.optString("text", "").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun List<com.codrive.ai.model.PrunedNodeEntry>.toBudgetedUiJson(): org.json.JSONArray = org.json.JSONArray().apply {
        val MAX_UI_ENTRIES = 80
        val MAX_TEXT_LENGTH = 120
        for (entry in this@toBudgetedUiJson.take(MAX_UI_ENTRIES)) {
            val roleChar = when (entry.role) {
                com.codrive.ai.model.UiRole.TEXT -> "t"
                com.codrive.ai.model.UiRole.BUTTON -> "b"
                com.codrive.ai.model.UiRole.INPUT -> "i"
                com.codrive.ai.model.UiRole.CHECKBOX -> "c"
            }

            val bounds = entry.boundsAsList()
            val centerX = (bounds[0] + bounds[2]) / 2
            val centerY = (bounds[1] + bounds[3]) / 2

            val finalText = (entry.text ?: entry.contentDescription ?: "").take(MAX_TEXT_LENGTH)

            val tuple = org.json.JSONArray().apply {
                put(entry.index)
                put(roleChar)
                put(org.json.JSONArray().apply { put(centerX); put(centerY) })
                put(finalText)
            }

            put(tuple)
        }
    }

    private fun messageForHttpFailure(statusCode: Int, responseBody: String): String {
        val detail = responseBody.take(200).replace("\n", " ")
        return when (statusCode) {
            400 -> "Gemini rejected the request. Try a smaller command or verify model name. $detail"
            401, 403 -> "Gemini API key was rejected. Update the key in LLM Settings. $detail"
            404 -> "Gemini model or endpoint was not found. Check provider/model in LLM Settings. $detail"
            429 -> "I am rate-limited, retrying shortly. $detail"
            in 500..599 -> "Gemini is temporarily unavailable. Try again in a moment. $detail"
            else -> "I could not complete that request (status=$statusCode). $detail"
        }.trim()
    }

    private fun clarification(message: String): AgentDecision = AgentDecision(
        actionType = ActionType.FINISH,
        targetIndex = -1,
        textToType = "",
        toolQuery = "",
        voiceFeedback = message,
        confidenceScore = 0.0,
    )

    companion object {
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        private fun normalizeModelName(model: String): String {
            val trimmed = model.trim()
            return if (trimmed.startsWith("models/")) trimmed else "models/$trimmed"
        }

        private fun buildGenerateContentUrl(model: String): String {
            val safeModel = normalizeModelName(model)
            return "$GEMINI_BASE_URL/$safeModel:generateContent"
        }

        fun defaultTransportWithHeaderKey(apiKey: String, model: String): GeminiTransport = GeminiTransport { requestBody, timeoutMillis ->
            val client = OkHttpClient.Builder()
                .callTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .build()

            val mediaType = "application/json".toMediaTypeOrNull()
            val body = requestBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(buildGenerateContentUrl(model))
                .post(body)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .build()

            client.newCall(request)
        }

        /** Transport that sends the apiKey as a query parameter instead of Authorization header. */
        fun defaultTransportWithQueryKey(apiKey: String, model: String): GeminiTransport = GeminiTransport { requestBody, timeoutMillis ->
            val client = OkHttpClient.Builder()
                .callTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .build()

            val mediaType = "application/json".toMediaTypeOrNull()
            val body = requestBody.toRequestBody(mediaType)

            // Append the key as a query parameter. Do not log the full URL in production.
            val urlWithKey = "${buildGenerateContentUrl(model)}?key=$apiKey"

            val request = Request.Builder()
                .url(urlWithKey)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request)
        }

        /** Lightweight API key validation: returns Pair(success,message). */
        @JvmOverloads
        fun validateApiKey(
            apiKey: String,
            model: String,
            timeoutMillis: Int = 6_000,
            transportFactory: (String, String) -> GeminiTransport = ::defaultTransportWithQueryKey,
            fallbackFactory: (String, String) -> GeminiTransport = ::defaultTransportWithHeaderKey,
        ): Pair<Boolean, String> {
            fun sanitizeForLogs(raw: String?): String {
                if (raw == null) return ""
                return raw
                    .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "bearer [redacted]")
                    .replace(Regex("(?i)\\?key=[A-Za-z0-9._-]+"), "?key=[redacted]")
                    .replace(Regex("[\\r\\n\\t]+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(800)
            }
            fun makePingBody(): String = org.json.JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(
                        org.json.JSONObject()
                            .put("role", "user")
                            .put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", "You are a ping. Reply with a simple JSON object.")))
                    )
                    put(
                        org.json.JSONObject()
                            .put("role", "user")
                            .put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", "{\"action_type\":\"RESPOND\",\"voice_feedback\":\"pong\",\"confidence_score\":0.5}")))
                    )
                })
                put(
                    "generationConfig",
                    org.json.JSONObject()
                        .put("temperature", 0)
                )
            }.toString()

            fun tryTransport(tf: (String, String) -> GeminiTransport, modelToUse: String): Pair<Int, String> {
                val transport = tf(apiKey, modelToUse)
                val body = makePingBody()
                val call = transport.postCall(body, timeoutMillis)
                val response = call.execute()
                try {
                    val code = response.code
                    val respBody = response.body?.string().orEmpty()
                    return Pair(code, respBody)
                } finally {
                    response.close()
                }
            }

            fun tryValidationModels(tf: (String, String) -> GeminiTransport): Pair<Boolean, String>? {
                val normalizedPrimary = normalizeModelName(model)
                val candidates = linkedSetOf(
                    normalizedPrimary,
                    "models/gemini-1.5-flash",
                    "models/gemini-1.5-flash-8b",
                )
                for (candidate in candidates) {
                    try {
                        val (candidateCode, candidateBody) = tryTransport(tf, candidate)
                        if (BuildConfig.DEBUG) {
                            try {
                                android.util.Log.d(
                                    "CoDrive.Gemini.Validate",
                                    "Candidate model=$candidate response code=$candidateCode body=${sanitizeForLogs(candidateBody)}"
                                )
                            } catch (_: Exception) { }
                        }
                        if (candidateCode in 200..299) {
                            return Pair(true, "OK ($candidate)")
                        }
                        if (candidateCode == 401 || candidateCode == 403) {
                            return Pair(false, "API key rejected (401/403)")
                        }
                    } catch (_: Exception) {
                        // Keep trying candidate models.
                    }
                }
                return null
            }

            return try {
                if (BuildConfig.DEBUG) {
                    try {
                        android.util.Log.d("CoDrive.Gemini.Validate", "Validating Gemini API key (model=$model) - primary attempt using configured transport")
                    } catch (_: Exception) { }
                }
                val (code, body) = tryTransport(transportFactory, model)
                if (BuildConfig.DEBUG) {
                    try {
                        android.util.Log.d("CoDrive.Gemini.Validate", "Primary response code=$code body=${sanitizeForLogs(body)}")
                    } catch (_: Exception) { }
                }
                when (code) {
                    in 200..299 -> Pair(true, "OK")
                    401, 403 -> {
                        if (BuildConfig.DEBUG) {
                            try { android.util.Log.d("CoDrive.Gemini.Validate", "Primary auth rejected (401/403), attempting fallback transport") } catch (_: Exception) { }
                        }
                        try {
                            val (fbCode, fbBody) = tryTransport(fallbackFactory, model)
                            if (BuildConfig.DEBUG) {
                                try { android.util.Log.d("CoDrive.Gemini.Validate", "Fallback response code=$fbCode body=${sanitizeForLogs(fbBody)}") } catch (_: Exception) { }
                            }
                            when (fbCode) {
                                in 200..299 -> Pair(true, "OK (query-key)")
                                401, 403 -> Pair(false, "API key rejected (401/403)")
                                else -> Pair(false, "Unexpected status on fallback: $fbCode")
                            }
                        } catch (_: Exception) {
                            Pair(false, "API key rejected (401/403)")
                        }
                    }
                    404 -> {
                        val altModel = if (model.startsWith("models/")) model else "models/$model"
                        if (BuildConfig.DEBUG) {
                            try { android.util.Log.d("CoDrive.Gemini.Validate", "Received 404; attempting alt model format: $altModel") } catch (_: Exception) { }
                        }
                        try {
                            val (altCode, altBody) = tryTransport(transportFactory, altModel)
                            if (BuildConfig.DEBUG) {
                                try { android.util.Log.d("CoDrive.Gemini.Validate", "Alt primary response code=$altCode body=${sanitizeForLogs(altBody)}") } catch (_: Exception) { }
                            }
                            if (altCode in 200..299) return Pair(true, "OK (alt-model)")
                            val (altFbCode, altFbBody) = tryTransport(fallbackFactory, altModel)
                            if (BuildConfig.DEBUG) {
                                try { android.util.Log.d("CoDrive.Gemini.Validate", "Alt fallback response code=$altFbCode body=${sanitizeForLogs(altFbBody)}") } catch (_: Exception) { }
                            }
                            when (altFbCode) {
                                in 200..299 -> Pair(true, "OK (alt-model query-key)")
                                401, 403 -> Pair(false, "API key rejected (401/403)")
                                404 -> Pair(false, "Model or endpoint not found (404)")
                                else -> Pair(false, "Unexpected status on alt fallback: $altFbCode")
                            }
                        } catch (_: Exception) {
                            Pair(false, "Model or endpoint not found (404)")
                        }
                    }
                    429 -> Pair(false, "Rate limited (429)")
                    in 500..599 -> {
                        tryValidationModels(transportFactory)
                            ?: tryValidationModels(fallbackFactory)
                            ?: Pair(false, "Server error: $code")
                    }
                    else -> Pair(false, "Unexpected status: $code")
                }
            } catch (_: Exception) {
                Pair(false, "Unknown error during validation")
            }
        }

        // Java-friendly overload is generated by @JvmOverloads above; no explicit wrapper needed.
    }
}
