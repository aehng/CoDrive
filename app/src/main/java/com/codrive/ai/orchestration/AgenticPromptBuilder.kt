package com.codrive.ai.orchestration

import com.codrive.ai.accessibility.PruningOutcome
import java.util.Locale

object AgenticPromptBuilder {
    fun build(
        originalCommand: String,
        scratchpad: List<AgentStep>,
        currentOutcome: PruningOutcome,
        completedIterations: Int,
        maxIterations: Int,
    ): String = buildString {
        append("AGENTIC_BETA_MODE=true\n")
        append(String.format(Locale.US, "COMPLETED_ITERATIONS=%d/%d\n", completedIterations, maxIterations))
        append("USER_COMMAND: ").append(sanitize(originalCommand)).append('\n')
        append("CURRENT_UI_SNAPSHOT_ID=").append(currentOutcome.uiMap.snapshotId).append('\n')
        append("SCRATCHPAD (structured steps, oldest first):\n")
        if (scratchpad.isEmpty()) {
            append("- NONE\n")
        } else {
            for (step in scratchpad) {
                append("- ").append(sanitize(step.toPromptLine())).append('\n')
            }
        }
        if (currentOutcome.isUnreadable) {
            val unreadable = currentOutcome.unreadableMessage ?: "This screen is unreadable."
            append("CURRENT_OUTCOME: ").append(sanitize(unreadable)).append('\n')
        }
        append("Use only the current ui_map for grounding.\n")
        append("Return exactly one JSON action per turn.\n")
        append("If the task is complete, return FINISH or RESPOND. Return strict JSON only.")
    }

    private fun sanitize(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}
