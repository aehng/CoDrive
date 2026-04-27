package com.codrive.ai.orchestration

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap

class InferenceLoopRunner @JvmOverloads constructor(
    private val llmClient: LlmClient,
    private val memorySearchTool: MemorySearchTool,
    private val maxTurns: Int = 3,
) {
    fun run(initialCommand: String, uiMap: PrunedUiMap): AgentDecision {
        var prompt = initialCommand

        repeat(maxTurns) {
            // Wrap the LLM call in a small retry loop to avoid immediate crash on transient rate limits
            var attempt = 0
            var resolved: AgentDecision? = null
            try {
                while (true) {
                    try {
                        resolved = llmClient.infer(prompt, uiMap)
                        break
                    } catch (e: Exception) {
                        attempt += 1
                        if (attempt > 3) throw e
                        try {
                            Thread.sleep(6500L)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            } catch (_: Exception) {
                return AgentDecision(
                    actionType = ActionType.FINISH,
                    targetIndex = -1,
                    textToType = "",
                    toolQuery = "",
                    voiceFeedback = "I'm having trouble connecting right now.",
                    confidenceScore = 0.0,
                )
            }

            val resolvedNonNull = resolved ?: return AgentDecision(
                actionType = ActionType.FINISH,
                targetIndex = -1,
                textToType = "",
                toolQuery = "",
                voiceFeedback = "I'm having trouble connecting right now.",
                confidenceScore = 0.0,
            )

            if (resolvedNonNull.actionType != ActionType.SEARCH_MEMORY) {
                return resolvedNonNull
            }

            val memoryResult = try {
                memorySearchTool.search(resolvedNonNull.toolQuery)
            } catch (e: Exception) {
                return AgentDecision(
                    actionType = ActionType.FINISH,
                    targetIndex = -1,
                    textToType = "",
                    toolQuery = "",
                    voiceFeedback = "I'm having trouble searching memory right now.",
                    confidenceScore = 0.0,
                )
            }

            // Preserve the full prompt/context across memory-search iterations
            prompt = buildString {
                append(prompt)
                append("\nMEMORY_RESULT: ")
                append(memoryResult)
            }
        }

        return AgentDecision(
            actionType = ActionType.FINISH,
            targetIndex = -1,
            textToType = "",
            toolQuery = "",
            voiceFeedback = "I need clarification before I continue.",
            confidenceScore = 0.0,
        )
    }
}

