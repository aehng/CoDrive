package com.codrive.ai.llm

import com.codrive.ai.settings.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmModelCatalogTest {

    @Test
    fun groqModelListParsesIds() {
        val json = """
            {
              "data": [
                {"id": "llama-3.1-8b"},
                {"id": "qwen-2.5-32b"}
              ]
            }
        """.trimIndent()
        val transport = ModelListTransport { _, _, _ -> 200 to json }
        val catalog = LlmModelCatalog(transport)

        val result = catalog.fetch(LlmProvider.GROQ, "gsk_test")

        assertTrue(result.success)
        assertEquals(2, result.models.size)
        assertEquals("llama-3.1-8b", result.models[0].id)
    }

    @Test
    fun geminiModelListFiltersGenerateContent() {
        val json = """
            {
              "models": [
                {
                  "name": "models/gemini-1.5-flash",
                  "displayName": "Gemini 1.5 Flash",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/embedding-001",
                  "displayName": "Embedding",
                  "supportedGenerationMethods": ["embedContent"]
                }
              ]
            }
        """.trimIndent()
        val transport = ModelListTransport { _, _, _ -> 200 to json }
        val catalog = LlmModelCatalog(transport)

        val result = catalog.fetch(LlmProvider.GEMINI, "key")

        assertTrue(result.success)
        assertEquals(1, result.models.size)
        assertEquals("models/gemini-1.5-flash", result.models[0].id)
        assertTrue(result.models[0].displayName.contains("Flash"))
    }
}

