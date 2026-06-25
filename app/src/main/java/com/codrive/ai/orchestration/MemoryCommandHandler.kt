package com.codrive.ai.orchestration

import com.codrive.ai.memory.MemoryRetentionPolicy
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision

enum class MemoryCommandScope {
    IDENTITY,
    SESSION,
}

data class MemoryCommandDirective(
    val scope: MemoryCommandScope,
    val key: String,
    val value: String,
)

class MemoryCommandRouter {
    fun match(command: String): MemoryCommandDirective? {
        val trimmed = command.trim()
        val rememberMatch = REMEMBER_PREFIX.find(trimmed) ?: return null
        var body = rememberMatch.groupValues[1].trim()
        if (body.isBlank()) {
            return null
        }

        val scope = if (SESSION_MARKER.containsMatchIn(body)) {
            MemoryCommandScope.SESSION
        } else {
            MemoryCommandScope.IDENTITY
        }

        body = SESSION_MARKER.replace(body, " ").trim()
        body = LEADING_FILLER.replaceFirst(body, "").trim()

        val keyValue = parseKeyValue(body) ?: return null
        val key = cleanSegment(keyValue.first)
        val value = cleanSegment(keyValue.second)
        if (key.isBlank() || value.isBlank()) {
            return null
        }

        return MemoryCommandDirective(scope = scope, key = key, value = value)
    }

    private fun parseKeyValue(body: String): Pair<String, String>? {
        val separators = listOf(
            Regex("^(.*?)\\s+(?:is|=|:|as)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.*?)\\s+to be\\s+(.+)$", RegexOption.IGNORE_CASE),
        )
        for (pattern in separators) {
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues[1] to match.groupValues[2]
            }
        }
        return null
    }

    private fun cleanSegment(value: String): String {
        return value
            .trim()
            .trim('"', '\'')
            .replace(Regex("\\s+"), " ")
            .replaceFirst(Regex("^(my|the|this|that|our)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    companion object {
        private val REMEMBER_PREFIX = Regex("^\\s*remember\\s+(.+)$", RegexOption.IGNORE_CASE)
        private val SESSION_MARKER = Regex("\\b(for this session|just for now|temporarily)\\b", RegexOption.IGNORE_CASE)
        private val LEADING_FILLER = Regex("^(my|the|this|that|our)\\s+", RegexOption.IGNORE_CASE)
    }
}

class MemoryCommandHandler @JvmOverloads constructor(
    private val memorySearchTool: MemorySearchTool,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val router: MemoryCommandRouter = MemoryCommandRouter(),
) {
    fun tryHandle(command: String): TracerBulletResult? {
        val directive = router.match(command) ?: return null
        return when (directive.scope) {
            MemoryCommandScope.IDENTITY -> handleIdentity(directive)
            MemoryCommandScope.SESSION -> handleSession(directive)
        }
    }

    private fun handleIdentity(directive: MemoryCommandDirective): TracerBulletResult? {
        val stored = memorySearchTool.rememberIdentity(
            key = directive.key,
            value = directive.value,
            updatedAtMillis = nowProvider(),
        ) ?: return null

        val feedback = "Remembered ${stored.key}."
        return TracerBulletResult(
            finalFeedback = feedback,
            decision = AgentDecision(
                actionType = ActionType.FINISH,
                voiceFeedback = feedback,
                confidenceScore = 1.0,
            ),
            executionResult = null,
            didExecute = false,
        )
    }

    private fun handleSession(directive: MemoryCommandDirective): TracerBulletResult? {
        val stored = memorySearchTool.rememberSessionContext(
            taskKey = directive.key,
            value = directive.value,
            expiresAtMillis = MemoryRetentionPolicy.nextSessionExpiry(nowProvider()),
        ) ?: return null

        val feedback = "Remembered ${stored.taskKey} for this session."
        return TracerBulletResult(
            finalFeedback = feedback,
            decision = AgentDecision(
                actionType = ActionType.FINISH,
                voiceFeedback = feedback,
                confidenceScore = 1.0,
            ),
            executionResult = null,
            didExecute = false,
        )
    }
}
