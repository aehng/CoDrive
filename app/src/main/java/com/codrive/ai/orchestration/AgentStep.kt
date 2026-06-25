package com.codrive.ai.orchestration

import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult

data class AgentStep(
    val turnIndex: Int,
    val prompt: String,
    val uiSnapshotId: Long,
    val decision: AgentDecision,
    val memoryResult: String? = null,
    val executionResult: ExecutionResult? = null,
    val didExecute: Boolean = false,
    val finalFeedback: String = "",
) {
    fun toPromptLine(): String = buildString {
        append("STEP[").append(turnIndex).append("] ")
        append("snapshot=").append(uiSnapshotId).append(' ')
        append("decision=").append(decision.actionType)
        if (decision.targetIndex >= 0) {
            append(" target_index=").append(decision.targetIndex)
        }
        if (decision.textToType.isNotBlank()) {
            append(" text_to_type=").append(decision.textToType.trim())
        }
        if (decision.toolQuery.isNotBlank()) {
            append(" tool_query=").append(decision.toolQuery.trim())
        }
        if (memoryResult != null) {
            append(" memory_result=").append(memoryResult.trim())
        }
        if (executionResult != null) {
            append(" execution=").append(executionResult.message.trim())
        }
        if (finalFeedback.isNotBlank()) {
            append(" feedback=").append(finalFeedback.trim())
        }
        if (prompt.isNotBlank()) {
            append(" prompt=").append(prompt.trim())
        }
        append(" did_execute=").append(didExecute)
    }
}
