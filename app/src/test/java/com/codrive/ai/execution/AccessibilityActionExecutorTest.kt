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
        override var isFocused: Boolean = false,
        override val bounds: IntArray = intArrayOf(0, 0, 100, 100),
        var refreshResult: Boolean = true,
        var focusResult: Boolean = true,
        var typeResult: Boolean = true,
        var scrollForwardResult: Boolean = false,
        var scrollBackwardResult: Boolean = false,
    ) : UiActionNode {
        var typedValue: String? = null
        var focusCalls: Int = 0

        override fun refresh(): Boolean = refreshResult

        override fun focus(): Boolean {
            focusCalls += 1
            if (focusResult) {
                isFocused = true
            }
            return focusResult
        }

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
        var goHomeResult: Boolean = true
        var goBackResult: Boolean = true
        var openRecentsResult: Boolean = true

        override fun lookupNode(targetIndex: Int): UiActionNode? = node

        override fun click(bounds: IntArray): Boolean {
            clickedBounds = bounds
            return clickResult
        }

        override fun goHome(): Boolean = goHomeResult

        override fun goBack(): Boolean = goBackResult

        override fun openRecents(): Boolean = openRecentsResult
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
        assertEquals(1, fakeNode.focusCalls)
    }

    @Test
    fun typeFailsWhenNodeCannotBeFocused() {
        val fakeNode = FakeNode(focusResult = false)
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

        assertFalse(result.success)
        assertEquals("Failed to focus target 3 before typing.", result.message)
        assertEquals(null, fakeNode.typedValue)
    }

    @Test
    fun typeSkipsFocusActionWhenNodeAlreadyFocused() {
        val fakeNode = FakeNode(isFocused = true, focusResult = false)
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
        assertEquals(0, fakeNode.focusCalls)
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

    @Test
    fun homeUsesGlobalNavigationRuntimePath() {
        val runtime = FakeRuntime().apply { goHomeResult = true }
        val executor = AccessibilityActionExecutor(runtime)

        val result = executor.execute(
            AgentDecision(actionType = ActionType.HOME, confidenceScore = 0.95),
            PrunedUiMap(1L, emptyList()),
        )

        assertTrue(result.success)
        assertEquals(ActionType.HOME, result.performedAction)
        assertTrue(result.message.contains("home", ignoreCase = true))
    }
}

