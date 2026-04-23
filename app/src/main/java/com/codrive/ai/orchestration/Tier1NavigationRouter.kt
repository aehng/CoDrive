package com.codrive.ai.orchestration

import android.accessibilityservice.AccessibilityService

data class Tier1NavigationDirective(
    val globalAction: Int,
    val voiceFeedback: String,
)

class Tier1NavigationRouter {
    fun match(command: String): Tier1NavigationDirective? {
        val normalized = normalize(command)
        val stripped = stripPolitePrefix(normalized)

        return when (stripped) {
            in HOME_COMMANDS -> Tier1NavigationDirective(
                globalAction = AccessibilityService.GLOBAL_ACTION_HOME,
                voiceFeedback = "Going home.",
            )
            in BACK_COMMANDS -> Tier1NavigationDirective(
                globalAction = AccessibilityService.GLOBAL_ACTION_BACK,
                voiceFeedback = "Going back.",
            )
            in RECENTS_COMMANDS -> Tier1NavigationDirective(
                globalAction = AccessibilityService.GLOBAL_ACTION_RECENTS,
                voiceFeedback = "Opening recents.",
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
    }
}

