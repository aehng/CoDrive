package com.codrive.ai.orchestration

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap

class InferenceLoopRunner @JvmOverloads constructor(
    private val llmClient: LlmClient,
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 6500L,
) {
    fun run(initialCommand: String, uiMap: PrunedUiMap): AgentDecision {
        // Single-step inference with retry on transient transport failures.
        var attempt = 0
        while (true) {
            try {
                return llmClient.infer(initialCommand, uiMap)
            } catch (e: Exception) {
                attempt += 1
                if (attempt >= maxAttempts) {
                    return AgentDecision(
                        actionType = ActionType.FINISH,
                        targetIndex = -1,
                        textToType = "",
                        toolQuery = "",
                        voiceFeedback = "I'm having trouble connecting right now.",
                        confidenceScore = 0.0,
                    )
                }
                try {
                    Thread.sleep(retryDelayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
