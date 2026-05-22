package com.codrive.ai.orchestration

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.AgentPolicy

class ActiveSessionManager @JvmOverloads constructor(
    private val timeoutMillis: Long = AgentPolicy.activeSessionTimeoutMillis,
    private val historyEnabled: Boolean = true,
    private val historyDepth: Int = DEFAULT_HISTORY_DEPTH,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val history = mutableListOf<SessionMessage>()
    private var lastActivityTimestamp: Long = 0L

    @Synchronized
    fun beginTurn(userInput: String): String {
        val normalized = userInput.trim()
        if (!historyEnabled || historyDepth <= 0) {
            clear()
            return normalized
        }
        val now = nowProvider()
        expireIfNeeded(now)

        history += SessionMessage(role = "user", content = normalized)
        trimHistoryIfNeeded()
        lastActivityTimestamp = now

        return if (history.size <= 1) {
            normalized
        } else {
            buildPromptWithHistory()
        }
    }

    @Synchronized
    fun onDecision(decision: AgentDecision, didExecute: Boolean) {
        if (!historyEnabled || historyDepth <= 0) {
            clear()
            return
        }
        val now = nowProvider()
        expireIfNeeded(now)

        if (!shouldKeepSession(decision, didExecute)) {
            clear()
            return
        }

        decision.voiceFeedback.trim()
            .takeIf { it.isNotEmpty() }
            ?.let {
                history += SessionMessage(role = "assistant", content = it)
                trimHistoryIfNeeded()
            }

        lastActivityTimestamp = now
    }

    @Synchronized
    fun clear() {
        history.clear()
        lastActivityTimestamp = 0L
    }

    @Synchronized
    fun hasActiveSession(): Boolean = history.isNotEmpty()

    private fun shouldKeepSession(decision: AgentDecision, didExecute: Boolean): Boolean {
        if (didExecute) {
            return false
        }
        if (decision.actionType == ActionType.RESPOND) {
            return true
        }
        if (decision.requiresClarification() && decision.actionType != ActionType.FINISH) {
            return true
        }
        if (didExecute) {
            return false
        }
        return false
    }

    private fun expireIfNeeded(now: Long) {
        if (lastActivityTimestamp == 0L) {
            return
        }
        if (now - lastActivityTimestamp > timeoutMillis) {
            clear()
        }
    }

    private fun trimHistoryIfNeeded() {
        val maxMessages = historyDepth * 2
        if (maxMessages <= 0) {
            history.clear()
            return
        }
        val overflow = history.size - maxMessages
        if (overflow > 0) {
            repeat(overflow) { history.removeAt(0) }
        }
    }

    private fun buildPromptWithHistory(): String = buildString {
        append("CONVERSATION_CONTEXT (most recent last):\n")
        for (message in history) {
            append(message.role.uppercase())
            append(": ")
            append(message.content)
            append('\n')
        }
        append("Use this context to resolve references. Return JSON only.")
    }

    private data class SessionMessage(
        val role: String,
        val content: String,
    )

    companion object {
        private const val DEFAULT_HISTORY_DEPTH = 4
    }
}



