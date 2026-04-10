package com.codrive.ai.llm

import com.codrive.ai.BuildConfig
import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.AgentPolicy
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow
import kotlin.random.Random

fun interface GroqTransport {
    @Throws(IOException::class)
    fun post(requestBody: String, timeoutMillis: Int): Pair<Int, String>
}

class GroqLlmClient(
    private val apiKey: String = BuildConfig.GROQ_API_KEY,
    private val parser: GroqDecisionParser = GroqDecisionParser(),
    private val transport: GroqTransport = defaultTransport(BuildConfig.GROQ_API_KEY),
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterProvider: () -> Long = { Random.nextLong(0, 250) },
) : LlmClient {

    override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
        if (apiKey.isBlank()) {
            return clarification("Groq is not configured yet.")
        }

        val requestBody = buildRequestBody(command, uiMap)

        var attempt = 0
        while (attempt <= AgentPolicy.groqRetryAttempts) {
            try {
                val (statusCode, responseBody) = transport.post(
                    requestBody = requestBody,
                    timeoutMillis = AgentPolicy.groqRequestTimeoutMillis.toInt(),
                )

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

                if (statusCode == 429) {
                    return clarification("I am rate-limited, retrying shortly.")
                }

                return clarification("I could not complete that request.")
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
        root.put("model", "deepseek-r1-distill-qwen-32b")
        root.put("temperature", 0)
        root.put("messages", JSONArray().apply {
            put(systemMessage())
            put(userMessage(command, uiMap))
        })
        root.put("response_format", strictResponseFormat())
        return root.toString()
    }

    private fun systemMessage(): JSONObject = JSONObject()
        .put("role", "system")
        .put(
            "content",
            "You are CoDrive. Return only strict JSON matching schema. Limit reasoning to 2 sentences max."
        )

    private fun userMessage(command: String, uiMap: PrunedUiMap): JSONObject {
        val payload = JSONObject()
            .put("command", command)
            .put("snapshot_id", uiMap.snapshotId)
            .put("ui_map", uiMap.entries.toUiJson())
            .put("token_budget_soft", AgentPolicy.groqSoftTokenBudget)
            .put("token_budget_hard", AgentPolicy.groqHardTokenBudget)
        return JSONObject()
            .put("role", "user")
            .put("content", payload.toString())
    }

    private fun strictResponseFormat(): JSONObject = JSONObject()
        .put("type", "json_schema")
        .put("json_schema", JSONObject()
            .put("name", "agent_decision")
            .put("strict", true)
            .put("schema", JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("required", JSONArray()
                    .put("action_type")
                    .put("target_index")
                    .put("text_to_type")
                    .put("tool_query")
                    .put("voice_feedback")
                    .put("confidence_score")
                )
                .put("properties", JSONObject()
                    .put("action_type", JSONObject()
                        .put("type", "string")
                        .put("enum", JSONArray()
                            .put("CLICK")
                            .put("TYPE")
                            .put("SCROLL")
                            .put("SEARCH_MEMORY")
                            .put("FINISH")
                        ))
                    .put("target_index", JSONObject().put("type", "integer"))
                    .put("text_to_type", JSONObject().put("type", "string"))
                    .put("tool_query", JSONObject().put("type", "string"))
                    .put("voice_feedback", JSONObject().put("type", "string"))
                    .put("confidence_score", JSONObject().put("type", "number"))
                )
            )
        )

    private fun clarification(message: String): AgentDecision = AgentDecision(
        actionType = ActionType.FINISH,
        voiceFeedback = message,
        confidenceScore = 0.0,
    )

    private fun List<PrunedNodeEntry>.toUiJson(): JSONArray = JSONArray().apply {
        for (entry in this@toUiJson) {
            put(JSONObject()
                .put("index", entry.index)
                .put("role", entry.role.name.lowercase())
                .put("bounds", JSONArray(entry.boundsAsList()))
                .put("text", entry.text ?: "")
                .put("content_description", entry.contentDescription ?: "")
                .put("interactive", entry.isInteractive)
            )
        }
    }

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

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

