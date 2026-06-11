package com.codrive.ai.orchestration

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionManagerTest {
    @Test
    fun historyDisabledReturnsRawInputAndDoesNotKeepSession() {
        val manager = ActiveSessionManager(historyEnabled = false, nowProvider = { 1L })

        val prompt = manager.beginTurn("tap submit")

        assertTrue(prompt == "tap submit")
        assertFalse(manager.hasActiveSession())
    }

    @Test
    fun beginTurnReturnsRawInputWhenNoSessionExists() {
        val manager = ActiveSessionManager(nowProvider = { 1L })

        val prompt = manager.beginTurn("tap submit")

        assertTrue(prompt == "tap submit")
        assertTrue(manager.hasActiveSession())
    }

    @Test
    fun beginTurnInjectsHistoryWhenSessionIsActive() {
        var now = 1L
        val manager = ActiveSessionManager(timeoutMillis = 30_000L, nowProvider = { now })

        manager.beginTurn("click submit")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.RESPOND,
                voiceFeedback = "Which submit button?",
                confidenceScore = 0.6,
            ),
            didExecute = false,
        )

        now = 2L
        val followUpPrompt = manager.beginTurn("the top one")

        assertTrue(followUpPrompt.contains("CONVERSATION_CONTEXT"))
        assertTrue(followUpPrompt.contains("USER: click submit"))
        assertTrue(followUpPrompt.contains("ASSISTANT: Which submit button?"))
        assertTrue(followUpPrompt.contains("USER: the top one"))
    }

    @Test
    fun sessionExpiresAfterTimeout() {
        var now = 1L
        val manager = ActiveSessionManager(timeoutMillis = 30_000L, nowProvider = { now })

        manager.beginTurn("click submit")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.RESPOND,
                voiceFeedback = "Which one?",
                confidenceScore = 0.6,
            ),
            didExecute = false,
        )

        now = 40_100L
        val prompt = manager.beginTurn("the top one")

        assertTrue(prompt == "the top one")
    }

    @Test
    fun clearOnSuccessfulActionDecision() {
        val manager = ActiveSessionManager(nowProvider = { 1L })

        manager.beginTurn("click submit")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.CLICK,
                voiceFeedback = "Done",
                confidenceScore = 0.95,
            ),
            didExecute = true,
        )

        assertFalse(manager.hasActiveSession())
    }

    @Test
    fun veryLowConfidenceNonTerminalDecisionKeepsSessionOpenForConfirmation() {
        val manager = ActiveSessionManager(nowProvider = { 1L })

        manager.beginTurn("do something")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.CLICK,
                voiceFeedback = "I need confirmation before I act.",
                confidenceScore = 0.1,
            ),
            didExecute = false,
        )

        assertTrue(manager.hasActiveSession())
    }

    @Test
    fun failClosedFinishDoesNotKeepSession() {
        val manager = ActiveSessionManager(nowProvider = { 1L })

        manager.beginTurn("do something")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.FINISH,
                voiceFeedback = "I need confirmation before I act.",
                confidenceScore = 0.0,
            ),
            didExecute = false,
        )

        assertFalse(manager.hasActiveSession())
    }

    @Test
    fun historyDepthCapsConversationWindow() {
        var now = 1L
        val manager = ActiveSessionManager(historyDepth = 1, nowProvider = { now })

        manager.beginTurn("first")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.RESPOND,
                voiceFeedback = "first-reply",
                confidenceScore = 0.7,
            ),
            didExecute = false,
        )
        now++
        manager.beginTurn("second")
        manager.onDecision(
            AgentDecision(
                actionType = ActionType.RESPOND,
                voiceFeedback = "second-reply",
                confidenceScore = 0.7,
            ),
            didExecute = false,
        )
        now++
        val prompt = manager.beginTurn("third")

        assertFalse(prompt.contains("first-reply"))
        assertTrue(prompt.contains("second-reply"))
    }
}


