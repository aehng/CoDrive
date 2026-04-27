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
        val cleaned = rawText
            .orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")

        if (cleaned.isBlank()) {
            return Verdict(false, "", "empty transcript")
        }

        if (cleaned.length < minChars) {
            // Allow short but clearly-question-like phrases (e.g., "what?", "who?")
            val questionWords = setOf("what", "who", "where", "when", "how", "why")
            val tokens = cleaned.lowercase().split(" ").map { it.trim().trimEnd('?', '.') }
            if (tokens.any { it in questionWords } && cleaned.length >= 2) {
                // Accept short question-like phrases
                return Verdict(true, cleaned)
            }
            return Verdict(false, "", "too short")
        }

        val hasLetter = cleaned.any { it.isLetter() }
        if (!hasLetter) {
            return Verdict(false, "", "no speech-like content")
        }

        if (confidence != null && confidence in 0f..1f && confidence < minConfidence) {
            return Verdict(false, "", "low confidence")
        }

        return Verdict(true, cleaned)
    }
}

