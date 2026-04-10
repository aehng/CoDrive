package com.codrive.ai.orchestration

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.memory.IdentityDao
import com.codrive.ai.memory.IdentityEntity
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.memory.SessionContextDao
import com.codrive.ai.memory.SessionContextEntity
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedNodeEntry
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceLoopRunnerTest {
    private class FakeLlmClient(
        private val responses: ArrayDeque<AgentDecision>,
    ) : LlmClient {
        val prompts = mutableListOf<String>()

        override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
            prompts += command
            return responses.removeFirst()
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
    fun runHandlesSearchMemoryThenReturnsTerminalAction() {
        val llm = FakeLlmClient(
            ArrayDeque(
                listOf(
                    AgentDecision(
                        actionType = ActionType.SEARCH_MEMORY,
                        toolQuery = "selected",
                        confidenceScore = 0.9,
                    ),
                    AgentDecision(
                        actionType = ActionType.CLICK,
                        targetIndex = 2,
                        voiceFeedback = "Tapped",
                        confidenceScore = 0.95,
                    ),
                )
            )
        )

        val memoryTool = MemorySearchTool(
            identityDaoProvider = { EmptyIdentityDao() },
            sessionContextDaoProvider = { SessionDaoWithOneValue() },
            nowProvider = { 1L },
        )

        val runner = InferenceLoopRunner(llmClient = llm, memorySearchTool = memoryTool, maxTurns = 3)
        val decision = runner.run("tap next", sampleUiMap())

        assertEquals(ActionType.CLICK, decision.actionType)
        assertEquals(2, llm.prompts.size)
        assertTrue(llm.prompts[1].contains("MEMORY_RESULT"))
        assertTrue(llm.prompts[1].contains("Option A"))
    }

    @Test
    fun runFailsClosedWhenLoopExceedsMaxTurns() {
        val llm = FakeLlmClient(
            ArrayDeque(
                listOf(
                    AgentDecision(ActionType.SEARCH_MEMORY, toolQuery = "a", confidenceScore = 0.9),
                    AgentDecision(ActionType.SEARCH_MEMORY, toolQuery = "b", confidenceScore = 0.9),
                    AgentDecision(ActionType.SEARCH_MEMORY, toolQuery = "c", confidenceScore = 0.9),
                )
            )
        )

        val memoryTool = MemorySearchTool(
            identityDaoProvider = { EmptyIdentityDao() },
            sessionContextDaoProvider = { SessionDaoWithOneValue() },
            nowProvider = { 1L },
        )

        val runner = InferenceLoopRunner(llmClient = llm, memorySearchTool = memoryTool, maxTurns = 2)
        val decision = runner.run("start", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.requiresClarification())
        assertEquals(2, llm.prompts.size)
    }

    private fun sampleUiMap(): PrunedUiMap = PrunedUiMap(
        snapshotId = 11L,
        entries = listOf(
            PrunedNodeEntry(
                index = 2,
                role = UiRole.BUTTON,
                bounds = intArrayOf(0, 0, 10, 10),
                text = "Next",
                isInteractive = true,
            ),
        ),
    )
}

