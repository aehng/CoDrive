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
    fun runReturnsSingleTurnDecisionWithoutInternalMemoryLoop() {
        val llm = FakeLlmClient(
            ArrayDeque(
                listOf(
                    AgentDecision(
                        actionType = ActionType.SEARCH_MEMORY,
                        toolQuery = "selected",
                        confidenceScore = 0.9,
                    ),
                )
            )
        )

        val runner = InferenceLoopRunner(llmClient = llm, maxAttempts = 1, retryDelayMs = 0L)
        val decision = runner.run("tap next", sampleUiMap())

        assertEquals(ActionType.SEARCH_MEMORY, decision.actionType)
        assertEquals(1, llm.prompts.size)
    }

    @Test
    fun runFallsClosedWhenTheInferenceThrowsRepeatedly() {
        val llm = object : LlmClient {
            var calls = 0
            override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
                calls += 1
                throw IllegalStateException("network down")
            }
        }

        val runner = InferenceLoopRunner(llmClient = llm, maxAttempts = 1, retryDelayMs = 0L)
        val decision = runner.run("start", sampleUiMap())

        assertEquals(ActionType.FINISH, decision.actionType)
        assertTrue(decision.voiceFeedback.contains("trouble", ignoreCase = true))
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

