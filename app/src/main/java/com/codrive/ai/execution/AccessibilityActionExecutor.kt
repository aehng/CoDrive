package com.codrive.ai.execution

import com.codrive.ai.contracts.ActionExecutor
import com.codrive.ai.model.ActionType
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.ExecutionResult
import com.codrive.ai.model.PrunedUiMap

class AccessibilityActionExecutor(
    private val runtime: UiActionRuntime,
) : ActionExecutor {

    override fun execute(decision: AgentDecision, uiMap: PrunedUiMap): ExecutionResult {
        return when (decision.actionType) {
            ActionType.CLICK -> executeClick(decision)
            ActionType.TYPE -> executeType(decision)
            ActionType.SCROLL -> executeScroll(decision)
            ActionType.RESPOND -> ExecutionResult(
                success = true,
                message = "Responded without UI action.",
                performedAction = ActionType.RESPOND,
            )
            ActionType.FINISH -> ExecutionResult(
                success = true,
                message = "Finished without UI action.",
                performedAction = ActionType.FINISH,
            )
            ActionType.SEARCH_MEMORY -> ExecutionResult(
                success = false,
                message = "SEARCH_MEMORY must be resolved before execution.",
                performedAction = ActionType.SEARCH_MEMORY,
            )
        }
    }

    private fun executeClick(decision: AgentDecision): ExecutionResult {
        val node = resolveFreshVisibleNode(decision.targetIndex)
            ?: return staleNodeFailure(decision.targetIndex)

        val success = runtime.click(node.bounds)
        return ExecutionResult(
            success = success,
            message = if (success) "Tapped target ${decision.targetIndex}." else "Failed to tap target ${decision.targetIndex}.",
            performedAction = ActionType.CLICK,
            targetIndex = decision.targetIndex,
        )
    }

    private fun executeType(decision: AgentDecision): ExecutionResult {
        val node = resolveFreshVisibleNode(decision.targetIndex)
            ?: return staleNodeFailure(decision.targetIndex)

        val success = node.setText(decision.textToType)
        return ExecutionResult(
            success = success,
            message = if (success) "Typed into target ${decision.targetIndex}." else "Failed to type into target ${decision.targetIndex}.",
            performedAction = ActionType.TYPE,
            targetIndex = decision.targetIndex,
        )
    }

    private fun executeScroll(decision: AgentDecision): ExecutionResult {
        val node = resolveFreshVisibleNode(decision.targetIndex)
            ?: return staleNodeFailure(decision.targetIndex)

        val success = node.scrollForward() || node.scrollBackward()
        return ExecutionResult(
            success = success,
            message = if (success) "Scrolled target ${decision.targetIndex}." else "Failed to scroll target ${decision.targetIndex}.",
            performedAction = ActionType.SCROLL,
            targetIndex = decision.targetIndex,
        )
    }

    private fun resolveFreshVisibleNode(targetIndex: Int): UiActionNode? {
        val node = runtime.lookupNode(targetIndex) ?: return null
        if (!node.refresh()) {
            return null
        }
        if (!node.isVisibleToUser) {
            return null
        }
        return node
    }

    private fun staleNodeFailure(targetIndex: Int): ExecutionResult = ExecutionResult(
        success = false,
        message = "Target $targetIndex is stale or not visible.",
        targetIndex = targetIndex,
    )
}

