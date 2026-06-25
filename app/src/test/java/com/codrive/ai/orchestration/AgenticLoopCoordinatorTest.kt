package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.NodeRegistry
import com.codrive.ai.accessibility.PruningOutcome
import com.codrive.ai.contracts.ActionExecutor
import com.codrive.ai.memory.IdentityDao
import com.codrive.ai.memory.IdentityEntity
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.memory.SessionContextDao
import com.codrive.ai.memory.SessionContextEntity
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction

class AgenticLoopCoordinatorTest {
    private class FakeExecutor : ActionExecutor {
        var calls = 0

        override fun execute(decision: AgentDecision, uiMap: PrunedUiMap): ExecutionResult {
            calls += 1
            return ExecutionResult(
                success = true,
                message = "Tapped target ${decision.targetIndex}.",
                performedAction = decision.actionType,
                targetIndex = decision.targetIndex,
            )
        }
    }

    private class EmptyIdentityDao : IdentityDao {
        override fun getAll(): MutableList<IdentityEntity> = mutableListOf()
        override fun upsertAll(entries: MutableList<IdentityEntity>) = Unit
        override fun clearAll() = Unit
    }

    private class SessionDaoWithOneValue : SessionContextDao {
        override fun getAll(): MutableList<SessionContextEntity> = mutableListOf(
            SessionContextEntity("s-1", "selected", "Option A", expiresAtMillis = 999_999L)
        )

        override fun upsertAll(entries: MutableList<SessionContextEntity>) = Unit
        override fun purgeExpired(nowMillis: Long) = Unit
        override fun clearAll() = Unit
    }

    @Test
    fun coordinatorPersistsScratchpadAcrossMemoryAndExecutionTurns() {
        val prompts = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val decisionRunner = BiFunction<String, PrunedUiMap, AgentDecision> { prompt, uiMap ->
            prompts += prompt
            when (callIndex.getAndIncrement()) {
                0 -> AgentDecision(
                    actionType = ActionType.SEARCH_MEMORY,
                    toolQuery = "selected",
                    confidenceScore = 0.9,
                )

                1 -> AgentDecision(
                    actionType = ActionType.CLICK,
                    targetIndex = 2,
                    voiceFeedback = "Going ahead",
                    confidenceScore = 0.95,
                )

                else -> AgentDecision(
                    actionType = ActionType.FINISH,
                    voiceFeedback = "Done",
                    confidenceScore = 0.0,
                )
            }
        }

        val memoryTool = MemorySearchTool(
            identityDaoProvider = { EmptyIdentityDao() },
            sessionContextDaoProvider = { SessionDaoWithOneValue() },
            nowProvider = { 1L },
        )
        val executor = FakeExecutor()
        val orchestrator = ChatTracerBulletOrchestrator(
            decisionRunner = { _, _ -> error("should not be called by the coordinator") },
            actionExecutor = executor,
        )

        val coordinator = AgenticLoopCoordinator(
            inferenceRunner = decisionRunner,
            memorySearchTool = memoryTool,
            actionOrchestrator = orchestrator,
            captureOutcome = {
                PruningOutcome(
                    uiMap = PrunedUiMap(
                        snapshotId = 22L,
                        entries = listOf(
                            PrunedNodeEntry(
                                index = 2,
                                role = UiRole.BUTTON,
                                bounds = intArrayOf(0, 0, 20, 20),
                                text = "Next",
                                isInteractive = true,
                            ),
                        ),
                    ),
                    nodeRegistry = NodeRegistry(),
                )
            },
            maxIterations = 4,
            uiWaitTimeoutMs = 0L,
        )

        val result = coordinator.run(
            originalCommand = "tap next",
            initialOutcome = PruningOutcome(
                uiMap = PrunedUiMap(
                    snapshotId = 11L,
                    entries = listOf(
                        PrunedNodeEntry(
                            index = 2,
                            role = UiRole.BUTTON,
                            bounds = intArrayOf(0, 0, 20, 20),
                            text = "Next",
                            isInteractive = true,
                        ),
                    ),
                ),
                nodeRegistry = NodeRegistry(),
            )
        )

        assertEquals(3, prompts.size)
        assertTrue(prompts[1].contains("Option A"))
        assertTrue(prompts[1].contains("SEARCH_MEMORY"))
        assertEquals(1, executor.calls)
        assertEquals(ActionType.FINISH, result.decision.actionType)
        assertEquals("Done", result.finalFeedback)
    }
}
