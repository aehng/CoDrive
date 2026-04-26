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
            ActionType.HOME -> executeHome()
            ActionType.BACK -> executeBack()
            ActionType.RECENTS -> executeRecents()
            ActionType.OPEN_NOTIFICATIONS -> executeOpenNotifications()
            ActionType.OPEN_QUICK_SETTINGS -> executeOpenQuickSettings()
            ActionType.OPEN_POWER_DIALOG -> executePowerDialog()
            ActionType.LOCK_SCREEN -> executeLockScreen()
            ActionType.TAKE_SCREENSHOT -> executeTakeScreenshot()
            ActionType.SWIPE_DOWN -> executeSwipeDown()
            ActionType.SWIPE_UP -> executeSwipeUp()
            ActionType.SWIPE_LEFT -> executeSwipeLeft()
            ActionType.SWIPE_RIGHT -> executeSwipeRight()
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

        val focused = node.isFocused || node.focus()
        if (!focused) {
            return ExecutionResult(
                success = false,
                message = "Failed to focus target ${decision.targetIndex} before typing.",
                performedAction = ActionType.TYPE,
                targetIndex = decision.targetIndex,
            )
        }

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

    private fun executeHome(): ExecutionResult {
        val success = runtime.goHome()
        return ExecutionResult(
            success = success,
            message = if (success) "Navigated to home screen." else "Failed to navigate home.",
            performedAction = ActionType.HOME
        )
    }

    private fun executeBack(): ExecutionResult {
        val success = runtime.goBack()
        return ExecutionResult(
            success = success,
            message = if (success) "Navigated back." else "Failed to navigate back.",
            performedAction = ActionType.BACK
        )
    }

    private fun executeRecents(): ExecutionResult {
        val success = runtime.openRecents()
        return ExecutionResult(
            success = success,
            message = if (success) "Opened recents." else "Failed to open recents.",
            performedAction = ActionType.RECENTS
        )
    }

    private fun executeOpenNotifications(): ExecutionResult {
        val success = runtime.openNotifications()
        return ExecutionResult(
            success = success,
            message = if (success) "Opened notifications." else "Failed to open notifications.",
            performedAction = ActionType.OPEN_NOTIFICATIONS,
        )
    }

    private fun executeOpenQuickSettings(): ExecutionResult {
        val success = runtime.openQuickSettings()
        return ExecutionResult(
            success = success,
            message = if (success) "Opened quick settings." else "Failed to open quick settings.",
            performedAction = ActionType.OPEN_QUICK_SETTINGS,
        )
    }

    private fun executePowerDialog(): ExecutionResult {
        val success = runtime.openPowerDialog()
        return ExecutionResult(
            success = success,
            message = if (success) "Opened power dialog." else "Failed to open power dialog.",
            performedAction = ActionType.OPEN_POWER_DIALOG,
        )
    }

    private fun executeLockScreen(): ExecutionResult {
        val success = runtime.lockScreen()
        return ExecutionResult(
            success = success,
            message = if (success) "Locked the screen." else "Failed to lock the screen.",
            performedAction = ActionType.LOCK_SCREEN,
        )
    }

    private fun executeTakeScreenshot(): ExecutionResult {
        val success = runtime.takeScreenshot()
        return ExecutionResult(
            success = success,
            message = if (success) "Captured screenshot." else "Failed to capture screenshot.",
            performedAction = ActionType.TAKE_SCREENSHOT,
        )
    }

    private fun executeSwipeDown(): ExecutionResult {
        val success = runtime.swipeDown()
        return ExecutionResult(
            success = success,
            message = if (success) "Swiped down." else "Failed to swipe down.",
            performedAction = ActionType.SWIPE_DOWN,
        )
    }

    private fun executeSwipeUp(): ExecutionResult {
        val success = runtime.swipeUp()
        return ExecutionResult(
            success = success,
            message = if (success) "Swiped up." else "Failed to swipe up.",
            performedAction = ActionType.SWIPE_UP,
        )
    }

    private fun executeSwipeLeft(): ExecutionResult {
        val success = runtime.swipeLeft()
        return ExecutionResult(
            success = success,
            message = if (success) "Swiped left." else "Failed to swipe left.",
            performedAction = ActionType.SWIPE_LEFT,
        )
    }

    private fun executeSwipeRight(): ExecutionResult {
        val success = runtime.swipeRight()
        return ExecutionResult(
            success = success,
            message = if (success) "Swiped right." else "Failed to swipe right.",
            performedAction = ActionType.SWIPE_RIGHT,
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
