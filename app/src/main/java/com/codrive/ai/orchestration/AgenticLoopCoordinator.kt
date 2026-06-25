package com.codrive.ai.orchestration

import android.text.TextUtils
import com.codrive.ai.accessibility.PruningOutcome
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import java.util.function.BiFunction
import java.util.function.Consumer

class AgenticLoopCoordinator @JvmOverloads constructor(
    private val inferenceRunner: BiFunction<String, PrunedUiMap, AgentDecision>,
    private val memorySearchTool: MemorySearchTool,
    private val actionOrchestrator: ChatTracerBulletOrchestrator,
    private val captureOutcome: () -> PruningOutcome,
    private val maxIterations: Int,
    private val uiWaitTimeoutMs: Long = 2_000L,
    private val onMemorySearch: Consumer<String>? = null,
) {
    fun run(originalCommand: String, initialOutcome: PruningOutcome): TracerBulletResult {
        var currentOutcome = initialOutcome
        val scratchpad = mutableListOf<AgentStep>()
        var lastResult: TracerBulletResult? = null

        if (currentOutcome.isUnreadable) {
            return actionOrchestrator.run(originalCommand, currentOutcome)
        }

        for (iteration in 0 until maxIterations) {
            if (currentOutcome.isUnreadable) {
                return actionOrchestrator.run(originalCommand, currentOutcome)
            }

            val prompt = AgenticPromptBuilder.build(
                originalCommand = originalCommand,
                scratchpad = scratchpad,
                currentOutcome = currentOutcome,
                completedIterations = iteration,
                maxIterations = maxIterations,
            )
            val decision = inferenceRunner.apply(prompt, currentOutcome.uiMap)

            if (decision.actionType == ActionType.SEARCH_MEMORY) {
                onMemorySearch?.accept(decision.toolQuery)
                val memoryResult = memorySearchTool.search(decision.toolQuery)
                scratchpad += AgentStep(
                    turnIndex = iteration + 1,
                    prompt = prompt,
                    uiSnapshotId = currentOutcome.uiMap.snapshotId,
                    decision = decision,
                    memoryResult = memoryResult,
                    finalFeedback = memoryResult,
                )
                continue
            }

            val result = actionOrchestrator.runDecision(decision, currentOutcome)
            lastResult = result
            scratchpad += AgentStep(
                turnIndex = iteration + 1,
                prompt = prompt,
                uiSnapshotId = currentOutcome.uiMap.snapshotId,
                decision = decision,
                executionResult = result.executionResult,
                didExecute = result.didExecute,
                finalFeedback = result.finalFeedback,
            )

            if (!result.didExecute) {
                return result
            }

            if (decision.actionType == ActionType.RESPOND || decision.actionType == ActionType.FINISH) {
                return result
            }

            waitForUiTransition()
            currentOutcome = captureOutcome()
        }

        return lastResult ?: TracerBulletResult(
            finalFeedback = "Agentic run produced no result.",
            decision = AgentDecision(
                actionType = ActionType.FINISH,
                targetIndex = -1,
                textToType = "",
                toolQuery = "",
                voiceFeedback = "",
                confidenceScore = 0.0,
            ),
            executionResult = null,
            didExecute = false,
        )
    }

    private fun waitForUiTransition() {
        val baseline = UiTransitionMonitor.currentVersion()
        UiTransitionMonitor.awaitAdvance(baseline, uiWaitTimeoutMs)
    }
}
