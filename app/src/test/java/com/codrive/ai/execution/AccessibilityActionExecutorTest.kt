package com.codrive.ai.execution

import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityActionExecutorTest {
    private class FakeNode(
        override var isVisibleToUser: Boolean = true,
        override val bounds: IntArray = intArrayOf(0, 0, 100, 100),
        var refreshResult: Boolean = true,
        var typeResult: Boolean = true,
        var scrollForwardResult: Boolean = false,
        var scrollBackwardResult: Boolean = false,
    ) : UiActionNode {
        var typedValue: String? = null

        override fun refresh(): Boolean = refreshResult

        override fun setText(value: String): Boolean {
            typedValue = value
            return typeResult
        }

        override fun scrollForward(): Boolean = scrollForwardResult

        override fun scrollBackward(): Boolean = scrollBackwardResult
    }

    private class FakeRuntime : UiActionRuntime {
        var node: UiActionNode? = null
        var clickedBounds: IntArray? = null
        var clickResult: Boolean = true

        override fun lookupNode(targetIndex: Int): UiActionNode? = node

        override fun click(bounds: IntArray): Boolean {
            clickedBounds = bounds
            return clickResult
        }
    }

    @Test
    fun clickFailsWhenNodeIsStale() {
        val runtime = FakeRuntime().apply {
            node = FakeNode(refreshResult = false)
        }
        val executor = AccessibilityActionExecutor(runtime)

        val result = executor.execute(
            AgentDecision(actionType = ActionType.CLICK, targetIndex = 7, confidenceScore = 0.9),
            PrunedUiMap(1L, emptyList()),
        )

        assertFalse(result.success)
        assertEquals("Target 7 is stale or not visible.", result.message)
    }

    @Test
    fun typeExecutesOnFreshVisibleNode() {
        val fakeNode = FakeNode(typeResult = true)
        val runtime = FakeRuntime().apply { node = fakeNode }
        val executor = AccessibilityActionExecutor(runtime)

        val result = executor.execute(
            AgentDecision(
                actionType = ActionType.TYPE,
                targetIndex = 3,
                textToType = "555-0100",
                confidenceScore = 0.95,
            ),
            PrunedUiMap(1L, emptyList()),
        )

        assertTrue(result.success)
        assertEquals("555-0100", fakeNode.typedValue)
    }

    @Test
    fun scrollFallsBackToBackwardWhenForwardFails() {
        val fakeNode = FakeNode(scrollForwardResult = false, scrollBackwardResult = true)
        val runtime = FakeRuntime().apply { node = fakeNode }
        val executor = AccessibilityActionExecutor(runtime)

        val result = executor.execute(
            AgentDecision(actionType = ActionType.SCROLL, targetIndex = 2, confidenceScore = 0.9),
            PrunedUiMap(1L, emptyList()),
        )

        assertTrue(result.success)
    }

    @Test
    fun clickUsesBoundsFromNode() {
        val runtime = FakeRuntime().apply {
            node = FakeNode(bounds = intArrayOf(1, 2, 3, 4))
        }
        val executor = AccessibilityActionExecutor(runtime)

        val result = executor.execute(
            AgentDecision(actionType = ActionType.CLICK, targetIndex = 1, confidenceScore = 0.9),
            PrunedUiMap(1L, emptyList()),
        )

        assertTrue(result.success)
        assertEquals(listOf(1, 2, 3, 4), runtime.clickedBounds?.toList())
    }

    @Test
    fun respondSkipsUiAction() {
        val runtime = FakeRuntime()
        val executor = AccessibilityActionExecutor(runtime)

        val result = executor.execute(
            AgentDecision(actionType = ActionType.RESPOND, confidenceScore = 0.95),
            PrunedUiMap(1L, emptyList()),
        )

        assertTrue(result.success)
        assertEquals(ActionType.RESPOND, result.performedAction)
        assertEquals(null, runtime.clickedBounds)
    }
}

