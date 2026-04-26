package com.codrive.ai.llm

import android.util.Log
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import org.json.JSONObject

class GroqDecisionParser(
    private val onReasoning: (String) -> Unit = {},
    private val onParseFailure: (reason: String, sanitizedRaw: String) -> Unit = { _, _ -> },
) {
    fun parse(rawResponse: String): AgentDecision {
        return runCatching {
            val root = JSONObject(rawResponse)
            val choices = root.optJSONArray("choices") ?: return failClosed("Missing 'choices' array", rawResponse)
            if (choices.length() == 0) {
                return failClosed("Empty 'choices' array", rawResponse)
            }

            val message = choices.getJSONObject(0).optJSONObject("message") ?: return failClosed("Missing 'message' object", rawResponse)
            val reasoning = message.optString("reasoning_content").trim()
            if (reasoning.isNotEmpty()) {
                onReasoning(reasoning)
            }

            val content = message.optString("content")
            val decisionJsonText = extractFirstJsonObject(content) ?: return failClosed("No JSON object found in content", content)
            val json = JSONObject(decisionJsonText)

            val actionTypeStr = json.optString("action_type", "")
            val actionType = parseActionType(actionTypeStr)

            // Soft-default for RESPOND: allow missing fields if it's a simple response
            if (actionType == ActionType.RESPOND) {
                val confidenceScore = json.optDouble("confidence_score", 1.0)
                    .coerceIn(0.0, 1.0)
                return AgentDecision(
                    actionType = ActionType.RESPOND,
                    targetIndex = json.optInt("target_index", 0),
                    textToType = json.optString("text_to_type", ""),
                    toolQuery = json.optString("tool_query", ""),
                    voiceFeedback = json.optString("voice_feedback", "I'm not sure how to respond."),
                    confidenceScore = confidenceScore,
                )
            }

            if (!hasAllRequiredFields(json)) {
                val missing = getMissingFields(json)
                return failClosed("Missing required fields: $missing", decisionJsonText)
            }

            if (actionType == null) {
                return failClosed("Invalid action_type: $actionTypeStr", decisionJsonText)
            }

            val confidenceScore = json.getDouble("confidence_score")
            if (confidenceScore !in 0.0..1.0) {
                return failClosed("Confidence score out of bounds: $confidenceScore", decisionJsonText)
            }

            AgentDecision(
                actionType = actionType,
                targetIndex = json.getInt("target_index"),
                textToType = json.getString("text_to_type"),
                toolQuery = json.getString("tool_query"),
                voiceFeedback = json.getString("voice_feedback"),
                confidenceScore = confidenceScore,
            )
        }.getOrElse { e ->
            failClosed("Exception during parsing: ${e.message}", rawResponse)
        }
    }

    private fun hasAllRequiredFields(json: JSONObject): Boolean {
        return REQUIRED_FIELDS.all(json::has)
    }

    private fun getMissingFields(json: JSONObject): List<String> {
        return REQUIRED_FIELDS.filter { !json.has(it) }
    }

    private fun parseActionType(value: String): ActionType? = runCatching {
        ActionType.valueOf(value.trim().uppercase())
    }.getOrNull()

    private fun failClosed(reason: String, raw: String): AgentDecision {
        val sanitizedRaw = sanitizeForTelemetry(raw)
        onParseFailure(reason, sanitizedRaw)
        runCatching {
            Log.e("GroqDecisionParser", "Parse failed: $reason. Raw content: $sanitizedRaw")
        }
        return AgentDecision(
            actionType = ActionType.FINISH,
            targetIndex = -1,
            textToType = "",
            toolQuery = "",
            voiceFeedback = "I encountered an error parsing the decision.",
            confidenceScore = 0.0,
        )
    }

    private fun sanitizeForTelemetry(raw: String): String {
        return raw
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "bearer [redacted]")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_TELEMETRY_RAW_CHARS)
    }

    internal fun extractFirstJsonObject(content: String): String? {
        val start = content.indexOf('{')
        if (start < 0) {
            return null
        }

        var depth = 0
        var inQuotes = false
        var escape = false

        for (index in start until content.length) {
            val ch = content[index]
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\') {
                escape = true
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (inQuotes) {
                continue
            }
            if (ch == '{') {
                depth += 1
            } else if (ch == '}') {
                depth -= 1
                if (depth == 0) {
                    return content.substring(start, index + 1)
                }
            }
        }

        return null
    }

    companion object {
        private const val MAX_TELEMETRY_RAW_CHARS = 500
        private val REQUIRED_FIELDS = listOf(
            "action_type",
            "target_index",
            "text_to_type",
            "tool_query",
            "voice_feedback",
            "confidence_score",
        )
    }
}
