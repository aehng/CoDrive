package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.NodeRegistry
import com.codrive.ai.accessibility.PruningOutcome
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IncrementalRequestManagerTest {
    @Test
    fun appendMergesPromptAndUsesLatestSnapshot() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val manager = IncrementalRequestManager(executor)
            val prompts = mutableListOf<String>()
            val snapshotIds = mutableListOf<Long>()
            val latch = CountDownLatch(1)

            manager.registerCallback {
                latch.countDown()
            }

            val runner = java.util.function.BiFunction<String, PruningOutcome, TracerBulletResult> { prompt, pruning ->
                prompts += prompt
                snapshotIds += pruning.uiMap.snapshotId
                if (!prompt.contains("CONTINUATION:")) {
                    try {
                        Thread.sleep(120L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                TracerBulletResult(
                    finalFeedback = if (prompt.contains("CONTINUATION:")) "merged" else "initial",
                    decision = AgentDecision(
                        actionType = ActionType.RESPOND,
                        confidenceScore = 0.95,
                    ),
                    executionResult = null,
                    didExecute = false,
                )
            }

            manager.startSession("open quick settings", pruningOutcome(11L), runner)
            manager.appendToActiveRequest("and then go home", pruningOutcome(22L), runner)

            assertTrue("Expected callback from latest generation", latch.await(2, TimeUnit.SECONDS))
            assertTrue(prompts.any { it.contains("CONTINUATION: and then go home") })
            assertTrue(prompts.last().contains("open quick settings"))
            assertTrue(prompts.last().contains("CONTINUATION: and then go home"))
            assertEquals(22L, snapshotIds.last())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun staleGenerationResultIsIgnoredAfterAppend() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val manager = IncrementalRequestManager(executor)
            val callbackCount = AtomicInteger(0)
            val feedbacks = mutableListOf<String>()
            val latch = CountDownLatch(1)

            manager.registerCallback { result ->
                callbackCount.incrementAndGet()
                feedbacks += result.finalFeedback
                latch.countDown()
            }

            val runner = java.util.function.BiFunction<String, PruningOutcome, TracerBulletResult> { prompt, _ ->
                if (prompt.contains("first")) {
                    try {
                        Thread.sleep(150L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                TracerBulletResult(
                    finalFeedback = if (prompt.contains("override")) "latest" else "stale",
                    decision = AgentDecision(
                        actionType = ActionType.RESPOND,
                        confidenceScore = 0.9,
                    ),
                    executionResult = null,
                    didExecute = false,
                )
            }

            manager.startSession("first command", pruningOutcome(1L), runner)
            manager.appendToActiveRequest("override", pruningOutcome(2L), runner)

            assertTrue("Expected callback from current generation", latch.await(3, TimeUnit.SECONDS))
            assertEquals(1, callbackCount.get())
            assertEquals(listOf("latest"), feedbacks)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun pruningOutcome(snapshotId: Long): PruningOutcome = PruningOutcome(
        uiMap = PrunedUiMap(snapshotId = snapshotId, entries = emptyList()),
        nodeRegistry = NodeRegistry(),
    )
}


