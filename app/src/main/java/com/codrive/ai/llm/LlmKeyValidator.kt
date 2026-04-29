package com.codrive.ai.llm

import com.codrive.ai.settings.LlmProvider
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class KeyValidationResult(
    val isValid: Boolean,
    val message: String,
)

fun interface KeyValidationTransport {
    @Throws(IOException::class)
    fun get(url: String, apiKey: String, timeoutMillis: Int): Pair<Int, String>
}

class LlmKeyValidator(
    private val transport: KeyValidationTransport = defaultTransport(),
) {
    fun validate(provider: LlmProvider, model: String, apiKey: String): KeyValidationResult {
        if (apiKey.isBlank()) {
            return KeyValidationResult(false, "API key is required.")
        }

        // Support validation for wired providers (GROQ and GEMINI). Other providers are not wired yet.
        val wiredProviders = setOf(LlmProvider.GROQ, LlmProvider.GEMINI)
        if (provider !in wiredProviders) {
            return KeyValidationResult(
                false,
                "${provider.displayName()} is saved but not wired for tracer bullet yet.",
            )
        }

        if (model.isBlank()) {
            return KeyValidationResult(false, "Model is required.")
        }

        return try {
            when (provider) {
                LlmProvider.GROQ -> {
                    val (statusCode, _) = transport.get(GROQ_MODELS_URL, apiKey, VALIDATION_TIMEOUT_MS)
                    when {
                        statusCode in 200..299 -> KeyValidationResult(true, "Key validated. Returning to chat launcher.")
                        statusCode == 401 || statusCode == 403 -> KeyValidationResult(false, "Invalid Groq API key.")
                        statusCode == 429 -> KeyValidationResult(true, "Key accepted (rate-limited right now).")
                        else -> KeyValidationResult(false, "Validation failed (${statusCode}).")
                    }
                }
                LlmProvider.GEMINI -> {
                    // Use the injected transport for a lightweight validation ping to Gemini's predict endpoint.
                    val (statusCode, _) = transport.get(GEMINI_PREDICT_URL, apiKey, VALIDATION_TIMEOUT_MS)
                    when {
                        statusCode in 200..299 -> KeyValidationResult(true, "Key validated. Returning to chat launcher.")
                        statusCode == 401 || statusCode == 403 -> KeyValidationResult(false, "Invalid Gemini API key.")
                        statusCode == 429 -> KeyValidationResult(true, "Key accepted (rate-limited right now).")
                        else -> KeyValidationResult(false, "Validation failed (${statusCode}).")
                    }
                }
                else -> KeyValidationResult(false, "Provider not supported for validation.")
            }
        } catch (_: IOException) {
            KeyValidationResult(false, "Validation failed. Check network and try again.")
        }
    }

    companion object {
        private const val GROQ_MODELS_URL = "https://api.groq.com/openai/v1/models"
        private const val GEMINI_PREDICT_URL = "https://generativelanguage.googleapis.com/v1beta/models:predict"
        private const val VALIDATION_TIMEOUT_MS = 6_000

        private fun defaultTransport(): KeyValidationTransport = KeyValidationTransport { url, apiKey, timeoutMillis ->
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            connection.disconnect()
            status to body
        }
    }
}
