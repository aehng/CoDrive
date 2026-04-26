package com.codrive.ai.orchestration

import com.codrive.ai.model.ActionType

data class Tier1NavigationDirective(
    val actionType: ActionType,
    val voiceFeedback: String,
)

class Tier1NavigationRouter {
    fun match(command: String): Tier1NavigationDirective? {
        val normalized = normalize(command)
        val stripped = stripPolitePrefix(normalized)

        return when (stripped) {
            in HOME_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.HOME,
                voiceFeedback = "Going home.",
            )
            in BACK_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.BACK,
                voiceFeedback = "Going back.",
            )
            in RECENTS_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.RECENTS,
                voiceFeedback = "Opening recents.",
            )
            in NOTIFICATIONS_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.OPEN_NOTIFICATIONS,
                voiceFeedback = "Opening notifications.",
            )
            in QUICK_SETTINGS_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.OPEN_QUICK_SETTINGS,
                voiceFeedback = "Opening quick settings.",
            )
            in SWIPE_DOWN_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.SWIPE_DOWN,
                voiceFeedback = "Swiping down.",
            )
            in SWIPE_UP_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.SWIPE_UP,
                voiceFeedback = "Swiping up.",
            )
            in SWIPE_LEFT_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.SWIPE_LEFT,
                voiceFeedback = "Swiping left.",
            )
            in SWIPE_RIGHT_COMMANDS -> Tier1NavigationDirective(
                actionType = ActionType.SWIPE_RIGHT,
                voiceFeedback = "Swiping right.",
            )
            else -> null
        }
    }

    private fun normalize(command: String): String = command
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun stripPolitePrefix(command: String): String = command
        .replaceFirst(POLITE_PREFIX, "")
        .trim()

    companion object {
        private val POLITE_PREFIX = Regex("^(please|could you|can you|would you|just)\\s+")
        private val HOME_COMMANDS = setOf(
            "home",
            "go home",
            "navigate home",
            "take me home",
            "open home",
        )
        private val BACK_COMMANDS = setOf(
            "back",
            "go back",
            "navigate back",
            "previous",
            "previous screen",
            "back navigation",
        )
        private val RECENTS_COMMANDS = setOf(
            "recents",
            "recent apps",
            "open recents",
            "app switcher",
            "switch apps",
            "open recent apps",
        )
        private val NOTIFICATIONS_COMMANDS = setOf(
            "open notifications",
            "show notifications",
            "notification shade",
            "open notification shade",
            "pull down notifications",
        )
        private val QUICK_SETTINGS_COMMANDS = setOf(
            "open quick settings",
            "show quick settings",
            "open control center",
            "show control center",
        )
        private val SWIPE_DOWN_COMMANDS = setOf(
            "swipe down",
            "scroll down",
            "pull down",
        )
        private val SWIPE_UP_COMMANDS = setOf(
            "swipe up",
            "scroll up",
        )
        private val SWIPE_LEFT_COMMANDS = setOf(
            "swipe left",
            "scroll left",
        )
        private val SWIPE_RIGHT_COMMANDS = setOf(
            "swipe right",
            "scroll right",
        )
    }
}

