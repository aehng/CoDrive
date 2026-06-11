package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.NodeRegistry
import com.codrive.ai.accessibility.PruningOutcome
import com.codrive.ai.contracts.ActionExecutor
import com.codrive.ai.execution.RegistryBinder
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTracerBulletOrchestratorTest {
    private class FakeExecutor : ActionExecutor {
        var calls = 0
        var nextResult = ExecutionResult(success = true, message = "Executed", performedAction = ActionType.CLICK)

        override fun execute(decision: AgentDecision, uiMap: PrunedUiMap): ExecutionResult {
            calls += 1
            return nextResult
        }
    }

    @Test
    fun unreadableScreenFailsClosedBeforeInferenceOrExecution() {
        val executor = FakeExecutor()
        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ -> error("should not run") },
            actionExecutor = executor,
        )

        val result = orchestrator.run(
            command = "tap next",
            pruningOutcome = PruningOutcome(
                uiMap = PrunedUiMap(1L, emptyList()),
                nodeRegistry = NodeRegistry(),
                unreadableMessage = "This screen is unreadable.",
            )
        )

        assertFalse(result.didExecute)
        assertEquals("This screen is unreadable.", result.finalFeedback)
        assertEquals(0, executor.calls)
    }

    @Test
    fun veryLowConfidenceDecisionReturnsConfirmationWithoutExecuting() {
        val executor = FakeExecutor()
        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ ->
                AgentDecision(
                    actionType = ActionType.CLICK,
                    targetIndex = 1,
                    voiceFeedback = "Need confirmation",
                    confidenceScore = 0.05,
                )
            },
            actionExecutor = executor,
        )

        val outcome = PruningOutcome(
            uiMap = readableUiMap(snapshotId = 2L),
            nodeRegistry = NodeRegistry(),
            unreadableMessage = null,
        )

        val result = orchestrator.run("tap", outcome)

        assertFalse(result.didExecute)
        assertEquals("Need confirmation", result.finalFeedback)
        assertEquals(0, executor.calls)
    }

    @Test
    fun moderateConfidenceDecisionExecutesWithoutConfirmation() {
        val executor = FakeExecutor().apply {
            nextResult = ExecutionResult(success = true, message = "Tapped target 1.", performedAction = ActionType.CLICK)
        }
        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ ->
                AgentDecision(
                    actionType = ActionType.CLICK,
                    targetIndex = 1,
                    voiceFeedback = "Go ahead",
                    confidenceScore = 0.5,
                )
            },
            actionExecutor = executor,
        )

        val result = orchestrator.run(
            command = "tap next",
            pruningOutcome = PruningOutcome(
                uiMap = readableUiMap(snapshotId = 3L),
                nodeRegistry = NodeRegistry(),
                unreadableMessage = null,
            )
        )

        assertTrue(result.didExecute)
        assertEquals(1, executor.calls)
        assertTrue(result.finalFeedback.contains("Go ahead"))
        assertTrue(result.finalFeedback.contains("Tapped target 1."))
    }

    @Test
    fun successPathBindsRegistryAndExecutesAction() {
        val executor = FakeExecutor().apply {
            nextResult = ExecutionResult(success = true, message = "Tapped target 2.", performedAction = ActionType.CLICK)
        }

        var boundSnapshotId = -1L
        val binder = RegistryBinder { registry ->
            boundSnapshotId = registry.currentSnapshotId()
        }

        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ ->
                AgentDecision(
                    actionType = ActionType.CLICK,
                    targetIndex = 2,
                    voiceFeedback = "Clicking now",
                    confidenceScore = 0.95,
                )
            },
            actionExecutor = executor,
            registryBinder = binder,
        )

        val registry = NodeRegistry().apply { beginSnapshot(42L) }
        val result = orchestrator.run(
            command = "tap next",
            pruningOutcome = PruningOutcome(
                uiMap = readableUiMap(snapshotId = 42L),
                nodeRegistry = registry,
                unreadableMessage = null,
            )
        )

        assertTrue(result.didExecute)
        assertEquals(1, executor.calls)
        assertEquals(42L, boundSnapshotId)
        assertTrue(result.finalFeedback.contains("Clicking now"))
        assertTrue(result.finalFeedback.contains("Tapped target 2."))
    }

    @Test
    fun respondDecisionReturnsFeedbackWithoutExecution() {
        val executor = FakeExecutor()
        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ ->
                AgentDecision(
                    actionType = ActionType.RESPOND,
                    targetIndex = 0,
                    voiceFeedback = "How can I help?",
                    confidenceScore = 0.95,
                )
            },
            actionExecutor = executor,
        )

        val result = orchestrator.run(
            command = "hello",
            pruningOutcome = PruningOutcome(
                uiMap = readableUiMap(snapshotId = 50L),
                nodeRegistry = NodeRegistry().apply { beginSnapshot(50L) },
                unreadableMessage = null,
            )
        )

        assertFalse(result.didExecute)
        assertEquals("How can I help?", result.finalFeedback)
        assertEquals(0, executor.calls)
    }

    @Test
    fun finishDecisionReturnsWithoutExecution() {
        val executor = FakeExecutor()
        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ ->
                AgentDecision(
                    actionType = ActionType.FINISH,
                    voiceFeedback = "Done",
                    confidenceScore = 0.0,
                )
            },
            actionExecutor = executor,
        )

        val result = orchestrator.run(
            command = "stop",
            pruningOutcome = PruningOutcome(
                uiMap = readableUiMap(snapshotId = 60L),
                nodeRegistry = NodeRegistry().apply { beginSnapshot(60L) },
                unreadableMessage = null,
            )
        )

        assertFalse(result.didExecute)
        assertEquals("Done", result.finalFeedback)
        assertEquals(0, executor.calls)
    }

    private fun readableUiMap(snapshotId: Long): PrunedUiMap = PrunedUiMap(
        snapshotId = snapshotId,
        entries = listOf(
            PrunedNodeEntry(
                index = 2,
                role = UiRole.BUTTON,
                bounds = intArrayOf(0, 0, 20, 20),
                text = "Next",
                isInteractive = true,
            )
        ),
    )
}


