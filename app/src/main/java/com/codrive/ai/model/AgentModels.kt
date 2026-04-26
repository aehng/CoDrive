package com.codrive.ai.model

object AgentPolicy {
    const val confidenceClarificationThreshold: Double = 0.8
    const val activeSessionTimeoutMillis: Long = 30_000L
    const val groqRequestTimeoutMillis: Long = 12_000L
    const val groqRetryAttempts: Int = 1
    const val groqRetryBackoffMillis: Long = 600L
    const val groqSoftTokenBudget: Int = 1_500
    const val groqHardTokenBudget: Int = 2_000

    fun shouldClarify(confidenceScore: Double): Boolean = confidenceScore < confidenceClarificationThreshold
}

enum class ActionType {
    CLICK,
    TYPE,
    SCROLL,
    HOME,
    BACK,
    RECENTS,
    OPEN_NOTIFICATIONS,
    OPEN_QUICK_SETTINGS,
    OPEN_POWER_DIALOG,
    LOCK_SCREEN,
    TAKE_SCREENSHOT,
    SWIPE_DOWN,
    SWIPE_UP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SEARCH_MEMORY,
    RESPOND,
    FINISH,
}

enum class UiRole {
    TEXT,
    BUTTON,
    INPUT,
    CHECKBOX,
}

data class PrunedNodeEntry(
    val index: Int,
    val role: UiRole,
    val bounds: IntArray,
    val text: String? = null,
    val contentDescription: String? = null,
    val isInteractive: Boolean = false,
) {
    init {
        require(bounds.size == 4) { "bounds must contain exactly 4 screen coordinates" }
    }

    fun boundsAsList(): List<Int> = bounds.toList()
}

data class PrunedUiMap(
    val snapshotId: Long,
    val entries: List<PrunedNodeEntry>,
) {
    val hasInteractableNodes: Boolean
        get() = entries.any { it.isInteractive }

    val isUnreadable: Boolean
        get() = entries.isEmpty() || !hasInteractableNodes

    fun entryForIndex(index: Int): PrunedNodeEntry? = entries.firstOrNull { it.index == index }
}

data class AgentDecision(
    val actionType: ActionType,
    val targetIndex: Int = -1,
    val textToType: String = "",
    val toolQuery: String = "",
    val voiceFeedback: String = "",
    val confidenceScore: Double,
) {
    fun requiresClarification(): Boolean = AgentPolicy.shouldClarify(confidenceScore)
}

data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val performedAction: ActionType? = null,
    val targetIndex: Int? = null,
) {
    val isTerminalFailure: Boolean
        get() = !success
}
