package com.codrive.ai.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModelsTest {
    @Test
    fun policyThresholdMatchesMission() {
        assertEquals(0.8, AgentPolicy.confidenceClarificationThreshold, 0.0)
        assertEquals(0.2, AgentPolicy.hardConfirmationThreshold, 0.0)
        assertEquals(30_000L, AgentPolicy.activeSessionTimeoutMillis)
        assertTrue(AgentPolicy.shouldClarify(0.79))
        assertFalse(AgentPolicy.shouldClarify(0.8))
        assertTrue(AgentPolicy.shouldRequireConfirmation(0.19))
        assertFalse(AgentPolicy.shouldRequireConfirmation(0.2))
    }

    @Test
    fun actionTypeEnumerationContainsTracerBulletActions() {
        assertEquals(ActionType.CLICK, ActionType.valueOf("CLICK"))
        assertEquals(ActionType.OPEN_NOTIFICATIONS, ActionType.valueOf("OPEN_NOTIFICATIONS"))
        assertEquals(ActionType.SWIPE_DOWN, ActionType.valueOf("SWIPE_DOWN"))
        assertEquals(ActionType.SEARCH_MEMORY, ActionType.valueOf("SEARCH_MEMORY"))
        assertEquals(ActionType.RESPOND, ActionType.valueOf("RESPOND"))
        assertEquals(ActionType.FINISH, ActionType.valueOf("FINISH"))
    }

    @Test
    fun uiRoleEnumerationMatchesExplicitMapping() {
        assertEquals(UiRole.INPUT, UiRole.valueOf("INPUT"))
        assertEquals(UiRole.CHECKBOX, UiRole.valueOf("CHECKBOX"))
    }

    @Test
    fun prunedNodeEntryStoresBoundsAndRole() {
        val entry = PrunedNodeEntry(
            index = 7,
            role = UiRole.BUTTON,
            bounds = intArrayOf(10, 20, 30, 40),
            text = "Next",
            contentDescription = "Next page",
            isInteractive = true,
        )

        assertEquals(7, entry.index)
        assertEquals(UiRole.BUTTON, entry.role)
        assertArrayEquals(intArrayOf(10, 20, 30, 40), entry.bounds)
        assertEquals(listOf(10, 20, 30, 40), entry.boundsAsList())
        assertTrue(entry.isInteractive)
    }

    @Test
    fun prunedUiMapFlagsUnreadableScreens() {
        val emptyMap = PrunedUiMap(snapshotId = 1L, entries = emptyList())
        val readableMap = PrunedUiMap(
            snapshotId = 2L,
            entries = listOf(
                PrunedNodeEntry(
                    index = 1,
                    role = UiRole.BUTTON,
                    bounds = intArrayOf(0, 0, 1, 1),
                    text = "Hello",
                    isInteractive = true,
                ),
            ),
        )

        assertTrue(emptyMap.isUnreadable)
        assertFalse(readableMap.isUnreadable)
        assertNotNull(readableMap.entryForIndex(1))
    }

    @Test
    fun agentDecisionUsesClarificationThreshold() {
        val decision = AgentDecision(
            actionType = ActionType.TYPE,
            targetIndex = 4,
            textToType = "555-0100",
            confidenceScore = 0.92,
        )

        assertFalse(decision.requiresClarification())
        assertFalse(decision.requiresConfirmation())
        assertEquals(ActionType.TYPE, decision.actionType)
        assertEquals(4, decision.targetIndex)
    }

    @Test
    fun executionResultExposesOutcomeFields() {
        val result = ExecutionResult(
            success = true,
            message = "Tapped Next",
            performedAction = ActionType.CLICK,
            targetIndex = 2,
        )

        assertTrue(result.success)
        assertFalse(result.isTerminalFailure)
        assertEquals("Tapped Next", result.message)
        assertEquals(ActionType.CLICK, result.performedAction)
    }
}


