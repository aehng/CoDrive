package com.codrive.ai.overlay.stt

class SpeechCommandEndpointer(
    private val minChars: Int = 3,
    private val minConfidence: Float = 0.35f,
) {
    data class Verdict(
        val shouldSubmit: Boolean,
        val command: String,
        val reason: String = "",
    )

    fun evaluate(rawText: String?, confidence: Float?): Verdict {
        // New policy: accept essentially any non-empty final transcript as a command.
        // The goal is to send most user utterances; callers can still apply relaxed
        // or stricter checks if needed. Only reject truly empty transcripts.
        val cleaned = rawText
            .orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")

        if (cleaned.isBlank()) {
            return Verdict(false, "", "empty transcript")
        }

        return Verdict(true, cleaned)
    }

    /**
     * Evaluate using relaxed rules for auto-submit fallback. This accepts shorter phrases,
     * lowers the confidence threshold, and whitelists navigation/command keywords.
     */
    fun evaluateRelaxed(rawText: String?): Verdict {
        val cleaned = rawText
            .orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")

        if (cleaned.isBlank()) {
            return Verdict(false, "", "empty transcript")
        }

        // Allow very short commands if they match navigation keywords
        val navWhitelist = setOf(
            "back", "home", "open", "search", "settings", "notifications", "quick settings", "go back", "go home", "scroll", "tap", "click",
        )
        val lower = cleaned.lowercase()
        if (navWhitelist.any { lower == it || lower.startsWith(it + " ") || lower.endsWith(" " + it) }) {
            return Verdict(true, cleaned)
        }

        // Lower thresholds for relaxed mode
        if (cleaned.length < 2) {
            return Verdict(false, "", "too short")
        }

        // Accept with looser confidence
        return Verdict(true, cleaned)
    }
}

