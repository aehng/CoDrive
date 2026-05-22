package com.codrive.ai.llm

import com.codrive.ai.BuildConfig
import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.AgentPolicy
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow
import kotlin.random.Random
import android.util.Log

fun interface GroqTransport {
    @Throws(IOException::class)
    fun post(requestBody: String, timeoutMillis: Int): Pair<Int, String>
}

class GroqLlmClient(
    private val apiKey: String = BuildConfig.GROQ_API_KEY,
    private val model: String = "qwen/qwen3-32b",
    private val parser: GroqDecisionParser = GroqDecisionParser(),
    private val transport: GroqTransport = defaultTransport(apiKey),
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterProvider: () -> Long = { Random.nextLong(0, 250) },
) : LlmClient {

    private fun logRequestIfEnabled(requestBody: String) {
        if (BuildConfig.DEBUG) {
            try {
                Log.d("CoDrive.Groq", "Groq Request: " + requestBody)
            } catch (e: Exception) {
                // ignore logging failures
            }
        }
    }

    private fun logResponseIfEnabled(statusCode: Int, responseBody: String) {
        if (BuildConfig.DEBUG) {
            try {
                Log.d("CoDrive.Groq", "Groq Response (" + statusCode + "): " + responseBody)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    constructor(apiKey: String, model: String) : this(
        apiKey = apiKey,
        model = model,
        parser = GroqDecisionParser(),
        transport = defaultTransport(apiKey),
    )

    override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
        if (apiKey.isBlank()) {
            return clarification("Groq is not configured yet.")
        }

        var attempt = 0
        while (attempt <= AgentPolicy.groqRetryAttempts) {
            val requestBody = buildRequestBody(command, uiMap)
            logRequestIfEnabled(requestBody)
            try {
                val (statusCode, responseBody) = transport.post(
                    requestBody = requestBody,
                    timeoutMillis = AgentPolicy.groqRequestTimeoutMillis.toInt(),
                )

                logResponseIfEnabled(statusCode, responseBody)

                if (statusCode in 200..299) {
                    return parser.parse(responseBody)
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
            } catch (_: IOException) {
                if (attempt < AgentPolicy.groqRetryAttempts) {
                    sleepBackoff(attempt)
                    attempt += 1
                    continue
                }
                return clarification("I could not reach Groq right now.")
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
        val root = JSONObject()
        root.put("model", model)
        root.put("temperature", 0)
        root.put("messages", JSONArray().apply {
            put(systemMessage())
            put(userMessage(command, uiMap))
        })
        root.put("response_format", jsonObjectResponseFormat())
        return root.toString()
    }

    private fun systemMessage(): JSONObject = JSONObject()
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
            - If action_type is RESPOND or a global/system gesture action, target_index must be 0.
            - If action_type is CLICK/TYPE/SCROLL, use a valid index from ui_map for target_index. For TYPE, supply text_to_type. For SEARCH_MEMORY, supply tool_query.
            - Prefer SWIPE_UP/SWIPE_DOWN/SWIPE_LEFT/SWIPE_RIGHT for viewport movement gestures.

            UI MAP LEGEND:
            Each entry in ui_map is a 4-item tuple: [index:int, role:char, [center_x:int, center_y:int], text:string]
            - role: 't' = text, 'b' = button, 'i' = input, 'c' = checkbox
            - text MUST be present; use an empty string when no visible text exists.

            Example ui_map entry: [4, "b", [915, 573], "Gallery"]
            """.trimIndent()
        )

    private fun userMessage(command: String, uiMap: PrunedUiMap): JSONObject {
        val payload = JSONObject()
            .put("command", command.trim())
            .put("snapshot_id", uiMap.snapshotId)
            .put("ui_map", uiMap.entries.toBudgetedUiJson())
            .put("token_budget_soft", AgentPolicy.groqSoftTokenBudget)
            .put("token_budget_hard", AgentPolicy.groqHardTokenBudget)
        return JSONObject()
            .put("role", "user")
            .put("content", payload.toString())
    }

    private fun jsonObjectResponseFormat(): JSONObject = JSONObject()
        .put("type", "json_object")

    private fun clarification(message: String): AgentDecision = AgentDecision(
        actionType = ActionType.FINISH,
        voiceFeedback = message,
        confidenceScore = 0.0,
    )

    private fun List<PrunedNodeEntry>.toBudgetedUiJson(): JSONArray = JSONArray().apply {
        for (entry in this@toBudgetedUiJson.take(MAX_UI_ENTRIES)) {
            // Role char mapping: TEXT='t', BUTTON='b', INPUT='i', CHECKBOX='c'
            val roleChar = when (entry.role) {
                UiRole.TEXT -> "t"
                UiRole.BUTTON -> "b"
                UiRole.INPUT -> "i"
                UiRole.CHECKBOX -> "c"
            }

            // Compute center coordinates
            val bounds = entry.boundsAsList()
            val centerX = (bounds[0] + bounds[2]) / 2
            val centerY = (bounds[1] + bounds[3]) / 2

            // Ensure text field is always present (empty string if absent)
            val finalText = (entry.text ?: entry.contentDescription ?: "").take(MAX_TEXT_LENGTH)

            val tuple = JSONArray().apply {
                put(entry.index)
                put(roleChar)
                put(JSONArray().apply { put(centerX); put(centerY) })
                put(finalText)
            }

            put(tuple)
        }
    }

    private fun messageForHttpFailure(statusCode: Int, responseBody: String): String {
        val detail = extractErrorDetail(responseBody)
        return when (statusCode) {
            400 -> "Groq rejected the request. Try a smaller command or verify model name. $detail"
            401, 403 -> "Groq API key was rejected. Update the key in LLM Settings. $detail"
            404 -> "Groq model or endpoint was not found. Check provider/model in LLM Settings. $detail"
            429 -> "I am rate-limited, retrying shortly. $detail"
            in 500..599 -> "Groq is temporarily unavailable. Try again in a moment. $detail"
            else -> "I could not complete that request (status=$statusCode). $detail"
        }.trim()
    }


    private fun extractErrorDetail(responseBody: String): String {
        if (responseBody.isBlank()) {
            return ""
        }
        return runCatching {
            val root = JSONObject(responseBody)
            val message = root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
            message.orEmpty()
        }.getOrDefault(responseBody)
            .replace("\n", " ")
            .trim()
            .take(MAX_ERROR_LENGTH)
    }

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MAX_UI_ENTRIES = 80
        private const val MAX_TEXT_LENGTH = 120
        private const val MAX_ERROR_LENGTH = 220

        fun defaultTransport(apiKey: String): GroqTransport = GroqTransport { requestBody, timeoutMillis ->
            val connection = (URL(GROQ_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(requestBody) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            connection.disconnect()
            status to responseBody
        }
    }
}
