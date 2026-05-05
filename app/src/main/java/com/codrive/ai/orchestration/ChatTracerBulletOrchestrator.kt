package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.PruningOutcome
import com.codrive.ai.contracts.ActionExecutor
import com.codrive.ai.execution.RegistryBinder
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult
import com.codrive.ai.model.PrunedUiMap
import com.codrive.ai.vlm.InternVlRuntime
import java.util.function.BiFunction

class ChatTracerBulletOrchestrator @JvmOverloads constructor(
    private val decisionRunner: BiFunction<String, PrunedUiMap, AgentDecision>,
    private val actionExecutor: ActionExecutor,
    private val registryBinder: RegistryBinder = RegistryBinder { },
    private val vlmRuntime: InternVlRuntime? = null,
) {
    fun run(command: String, pruningOutcome: PruningOutcome): TracerBulletResult {
        if (pruningOutcome.isUnreadable) {
            // Pre-load VLM assets when available so Tier 3 fallback can be fast.
            vlmRuntime?.let { runtime ->
                runCatching { runtime.ensureLoaded() }
            }
            val message = pruningOutcome.unreadableMessage ?: "This screen is unreadable."
            return TracerBulletResult(
                finalFeedback = message,
                decision = AgentDecision(
                    actionType = ActionType.FINISH,
                    voiceFeedback = message,
                    confidenceScore = 0.0,
                ),
                executionResult = null,
                didExecute = false,
            )
        }

        registryBinder.bindRegistry(pruningOutcome.nodeRegistry)
        val decision = decisionRunner.apply(command, pruningOutcome.uiMap)

        if (decision.actionType == ActionType.RESPOND) {
            val message = if (decision.voiceFeedback.isNotBlank()) {
                decision.voiceFeedback
            } else {
                "I am listening."
            }
            return TracerBulletResult(
                finalFeedback = message,
                decision = decision,
                executionResult = null,
                didExecute = false,
            )
        }

        if (decision.requiresClarification()) {
            val message = if (decision.voiceFeedback.isNotBlank()) {
                decision.voiceFeedback
            } else {
                "I need clarification before I act."
            }
            return TracerBulletResult(
                finalFeedback = message,
                decision = decision,
                executionResult = null,
                didExecute = false,
            )
        }

        val execution = actionExecutor.execute(decision, pruningOutcome.uiMap)
        val finalFeedback = buildFeedback(decision, execution)
        return TracerBulletResult(
            finalFeedback = finalFeedback,
            decision = decision,
            executionResult = execution,
            didExecute = execution.success,
        )
    }

    private fun buildFeedback(decision: AgentDecision, executionResult: ExecutionResult): String {
        return when {
            decision.voiceFeedback.isBlank() -> executionResult.message
            executionResult.message.isBlank() -> decision.voiceFeedback
            else -> "${decision.voiceFeedback}\n${executionResult.message}"
        }
    }
}

data class TracerBulletResult(
    val finalFeedback: String,
    val decision: AgentDecision,
    val executionResult: ExecutionResult?,
    val didExecute: Boolean,
)


