package com.codrive.ai.orchestration

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import java.util.function.Consumer

class InferenceLoopRunner @JvmOverloads constructor(
    private val llmClient: LlmClient,
    private val memorySearchTool: MemorySearchTool? = null,
    private val maxTurns: Int = 1,
    private val onMemorySearch: Consumer<String>? = null,
) {
    fun run(initialCommand: String, uiMap: PrunedUiMap): AgentDecision {
        // Single-step inference with retry on transient transport failures.
        var attempt = 0
        while (true) {
            try {
                return llmClient.infer(initialCommand, uiMap)
            } catch (e: Exception) {
                attempt += 1
                if (attempt > 3) {
                    return AgentDecision(
                        actionType = ActionType.FINISH,
                        targetIndex = -1,
                        textToType = "",
                        toolQuery = "",
                        voiceFeedback = if (memorySearchTool == null) {
                            "I'm having trouble connecting right now."
                        } else {
                            "I'm having trouble connecting right now."
                        },
                        confidenceScore = 0.0,
                    )
                }
                try {
                    Thread.sleep(6500L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
