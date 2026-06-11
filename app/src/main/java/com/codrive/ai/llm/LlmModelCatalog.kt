package com.codrive.ai.llm

import com.codrive.ai.settings.LlmProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

fun interface ModelListTransport {
    fun get(url: String, headers: Map<String, String>, timeoutMillis: Int): Pair<Int, String>
}

data class LlmModelInfo(
    val id: String,
    val displayName: String,
)

data class LlmModelListResult(
    val success: Boolean,
    val models: List<LlmModelInfo>,
    val message: String,
)

class LlmModelCatalog(
    private val transport: ModelListTransport = defaultTransport(),
) {
    @JvmOverloads
    fun fetch(provider: LlmProvider, apiKey: String, timeoutMillis: Int = 6_000): LlmModelListResult {
        if (apiKey.isBlank()) {
            return LlmModelListResult(false, emptyList(), "API key missing")
        }
        return when (provider) {
            LlmProvider.GROQ -> fetchGroq(apiKey, timeoutMillis)
            LlmProvider.GEMINI -> fetchGemini(apiKey, timeoutMillis)
            LlmProvider.OPENROUTER -> fetchOpenRouter(apiKey, timeoutMillis)
            else -> LlmModelListResult(false, emptyList(), "Provider not supported")
        }
    }

    private fun fetchGroq(apiKey: String, timeoutMillis: Int): LlmModelListResult {
        val url = "https://api.groq.com/openai/v1/models"
        val headers = mapOf("Authorization" to "Bearer $apiKey")
        val (code, body) = transport.get(url, headers, timeoutMillis)
        if (code !in 200..299) {
            return LlmModelListResult(false, emptyList(), "Groq model list failed ($code)")
        }
        return parseGroqModels(body)
    }

    private fun parseGroqModels(body: String): LlmModelListResult {
        return runCatching {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: JSONArray()
            val models = mutableListOf<LlmModelInfo>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isNotEmpty()) {
                    models.add(LlmModelInfo(id = id, displayName = id))
                }
            }
            LlmModelListResult(true, models.sortedBy { it.displayName }, "OK")
        }.getOrElse {
            LlmModelListResult(false, emptyList(), "Groq model list parse failed")
        }
    }

    private fun fetchGemini(apiKey: String, timeoutMillis: Int): LlmModelListResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val (code, body) = transport.get(url, emptyMap(), timeoutMillis)
        if (code !in 200..299) {
            return LlmModelListResult(false, emptyList(), "Gemini model list failed ($code)")
        }
        return parseGeminiModels(body)
    }

    private fun parseGeminiModels(body: String): LlmModelListResult {
        return runCatching {
            val root = JSONObject(body)
            val modelsJson = root.optJSONArray("models") ?: JSONArray()
            val models = mutableListOf<LlmModelInfo>()
            for (i in 0 until modelsJson.length()) {
                val item = modelsJson.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                if (name.isEmpty()) continue
                val methods = item.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = methods?.let { arrayContains(it, "generateContent") } ?: false
                if (!supportsGenerate) continue
                val shortName = name.removePrefix("models/")
                val displayName = item.optString("displayName", "").trim()
                val finalDisplay = when {
                    displayName.isEmpty() -> shortName
                    displayName.equals(shortName, ignoreCase = true) -> shortName
                    else -> "$displayName ($shortName)"
                }
                models.add(LlmModelInfo(id = name, displayName = finalDisplay))
            }
            LlmModelListResult(true, models.sortedBy { it.displayName }, "OK")
        }.getOrElse {
            LlmModelListResult(false, emptyList(), "Gemini model list parse failed")
        }
    }

    private fun fetchOpenRouter(apiKey: String, timeoutMillis: Int): LlmModelListResult {
        val url = "https://openrouter.ai/api/v1/models"
        val headers = mapOf("Authorization" to "Bearer $apiKey")
        val (code, body) = transport.get(url, headers, timeoutMillis)
        if (code !in 200..299) {
            return LlmModelListResult(false, emptyList(), "OpenRouter model list failed ($code)")
        }
        return parseOpenRouterModels(body)
    }

    private fun parseOpenRouterModels(body: String): LlmModelListResult {
        return runCatching {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: JSONArray()
            val models = mutableListOf<LlmModelInfo>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isEmpty()) continue
                val name = item.optString("name", "").trim()
                val description = item.optString("description", "").trim()
                val displayName = when {
                    name.isNotEmpty() -> name
                    description.isNotEmpty() -> "$id - $description"
                    else -> id
                }
                models.add(LlmModelInfo(id = id, displayName = displayName))
            }
            LlmModelListResult(true, models.sortedBy { it.displayName }, "OK")
        }.getOrElse {
            LlmModelListResult(false, emptyList(), "OpenRouter model list parse failed")
        }
    }

    private fun arrayContains(array: JSONArray, value: String): Boolean {
        for (i in 0 until array.length()) {
            if (value.equals(array.optString(i), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    companion object {
        fun defaultTransport(): ModelListTransport = ModelListTransport { url, headers, timeoutMillis ->
            val client = OkHttpClient.Builder()
                .callTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .build()
            val requestBuilder = Request.Builder().url(url)
            for ((key, value) in headers) {
                requestBuilder.addHeader(key, value)
            }
            val response = client.newCall(requestBuilder.build()).execute()
            try {
                val code = response.code
                val body = response.body?.string().orEmpty()
                code to body
            } finally {
                response.close()
            }
        }
    }
}
