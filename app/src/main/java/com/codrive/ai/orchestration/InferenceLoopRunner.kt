package com.codrive.ai.orchestration

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap

class InferenceLoopRunner(
    private val llmClient: LlmClient,
    private val memorySearchTool: MemorySearchTool,
    private val maxTurns: Int = 3,
) {
    fun run(initialCommand: String, uiMap: PrunedUiMap): AgentDecision {
        var prompt = initialCommand

        repeat(maxTurns) {
            val decision = llmClient.infer(prompt, uiMap)
            if (decision.actionType != ActionType.SEARCH_MEMORY) {
                return decision
            }

            val memoryResult = memorySearchTool.search(decision.toolQuery)
            prompt = buildString {
                append(initialCommand)
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

