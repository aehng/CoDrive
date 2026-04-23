package com.codrive.ai.llm

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import org.json.JSONObject

class GroqDecisionParser(
    private val onReasoning: (String) -> Unit = {},
) {
    fun parse(rawResponse: String): AgentDecision {
        return runCatching {
            val root = JSONObject(rawResponse)
            val choices = root.optJSONArray("choices") ?: return failClosed()
            if (choices.length() == 0) {
                return failClosed()
            }

            val message = choices.getJSONObject(0).optJSONObject("message") ?: return failClosed()
            val reasoning = message.optString("reasoning_content").trim()
            if (reasoning.isNotEmpty()) {
                onReasoning(reasoning)
            }

            val content = message.optString("content")
            val decisionJsonText = extractFirstJsonObject(content) ?: return failClosed()
            val json = JSONObject(decisionJsonText)
            if (!hasAllRequiredFields(json)) {
                return failClosed()
            }

            val actionType = parseActionType(json.getString("action_type")) ?: return failClosed()
            val confidenceScore = json.getDouble("confidence_score")
            if (confidenceScore !in 0.0..1.0) {
                return failClosed()
            }

            AgentDecision(
                actionType = actionType,
                targetIndex = json.getInt("target_index"),
                textToType = json.getString("text_to_type"),
                toolQuery = json.getString("tool_query"),
                voiceFeedback = json.getString("voice_feedback"),
                confidenceScore = confidenceScore,
            )
        }.getOrElse { failClosed() }
    }

    private fun hasAllRequiredFields(json: JSONObject): Boolean {
        val required = listOf(
            "action_type",
            "target_index",
            "text_to_type",
            "tool_query",
            "voice_feedback",
            "confidence_score",
        )
        return required.all(json::has)
    }

    private fun parseActionType(value: String): ActionType? = runCatching {
        ActionType.valueOf(value.trim().uppercase())
    }.getOrNull()

    private fun failClosed(): AgentDecision = AgentDecision(
        actionType = ActionType.FINISH,
        targetIndex = -1,
        textToType = "",
        toolQuery = "",
        voiceFeedback = "I need clarification before I act.",
        confidenceScore = 0.0,
    )

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
}

