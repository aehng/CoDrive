package com.codrive.ai.memory

import kotlin.math.sqrt
import java.util.Locale

class MemorySearchTool @JvmOverloads constructor(
    private val identityDaoProvider: () -> IdentityDao?,
    private val sessionContextDaoProvider: () -> SessionContextDao?,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val embedder: TextEmbedder = HashingTextEmbedder(),
    private val topK: Int = 3,
    private val minimumSimilarity: Float = 0.35f,
) {
    fun search(toolQuery: String): String {
        val query = toolQuery.trim()
        if (query.isEmpty()) {
            return "NO_QUERY"
        }

        val identityDao = identityDaoProvider()
        val sessionDao = sessionContextDaoProvider()
        sessionDao?.purgeExpired(nowProvider())

        val queryVector = embedder.embed(query)
        val queryTokens = tokenize(query)

        val scoredResults = buildList {
            identityDao?.getAll().orEmpty().forEach { entry ->
                add(scoreIdentityEntry(entry, queryVector, queryTokens))
            }
            sessionDao?.getAll().orEmpty().forEach { entry ->
                add(scoreSessionEntry(entry, queryVector, queryTokens))
            }
        }
            .filter { it.score >= minimumSimilarity }
            .sortedByDescending { it.score }
            .take(topK)

        if (scoredResults.isEmpty()) {
            return "NO_MATCH"
        }

        return buildString {
            append("MEMORY_RESULT:\n")
            scoredResults.forEach { result ->
                append("- ")
                append(result.kind)
                append(": ")
                append(result.text)
                append(" [score=")
                append(String.format(Locale.US, "%.2f", result.score))
                append("]\n")
            }
        }.trim()
    }

    fun rememberIdentity(key: String, value: String, updatedAtMillis: Long = nowProvider()): IdentityEntity? {
        val normalizedKey = key.trim()
        val normalizedValue = value.trim()
        if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
            return null
        }

        val entry = IdentityEntity(
            id = buildIdentityId(normalizedKey),
            key = normalizedKey,
            value = normalizedValue,
            updatedAtMillis = updatedAtMillis,
            embedding = MemoryEmbeddingCodec.encode(embedder.embed("$normalizedKey $normalizedValue")),
        )
        identityDaoProvider()?.upsertAll(mutableListOf(entry))
        return entry
    }

    fun rememberSessionContext(
        taskKey: String,
        value: String,
        expiresAtMillis: Long = MemoryRetentionPolicy.nextSessionExpiry(nowProvider()),
    ): SessionContextEntity? {
        val normalizedTaskKey = taskKey.trim()
        val normalizedValue = value.trim()
        if (normalizedTaskKey.isEmpty() || normalizedValue.isEmpty()) {
            return null
        }

        val now = nowProvider()
        val entry = SessionContextEntity(
            id = buildSessionId(normalizedTaskKey, now),
            taskKey = normalizedTaskKey,
            value = normalizedValue,
            expiresAtMillis = expiresAtMillis,
            embedding = MemoryEmbeddingCodec.encode(embedder.embed("$normalizedTaskKey $normalizedValue")),
        )
        sessionContextDaoProvider()?.upsertAll(mutableListOf(entry))
        return entry
    }

    private fun scoreIdentityEntry(
        entry: IdentityEntity,
        queryVector: FloatArray,
        queryTokens: Set<String>,
    ): ScoredMemoryResult {
        return scoreResult(
            kind = "identity",
            text = entry.semanticText(),
            queryVector = queryVector,
            queryTokens = queryTokens,
            storedVector = MemoryEmbeddingCodec.decode(entry.embedding),
        )
    }

    private fun scoreSessionEntry(
        entry: SessionContextEntity,
        queryVector: FloatArray,
        queryTokens: Set<String>,
    ): ScoredMemoryResult {
        return scoreResult(
            kind = "session",
            text = entry.semanticText(),
            queryVector = queryVector,
            queryTokens = queryTokens,
            storedVector = MemoryEmbeddingCodec.decode(entry.embedding),
        )
    }

    private fun scoreResult(
        kind: String,
        text: String,
        queryVector: FloatArray,
        queryTokens: Set<String>,
        storedVector: FloatArray?,
    ): ScoredMemoryResult {
        val candidateVector = storedVector ?: embedder.embed(text)
        val semanticScore = cosineSimilarity(queryVector, candidateVector)
        val lexicalScore = lexicalOverlap(queryTokens, tokenize(text))
        val score = (semanticScore * 0.85f) + (lexicalScore * 0.15f)
        return ScoredMemoryResult(kind = kind, text = text, score = score)
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        val length = minOf(v1.size, v2.size)
        if (length == 0) {
            return 0f
        }
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (index in 0 until length) {
            dotProduct += v1[index] * v2[index]
            normA += v1[index] * v1[index]
            normB += v2[index] * v2[index]
        }
        return if (normA == 0.0f || normB == 0.0f) 0.0f else dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun lexicalOverlap(left: Set<String>, right: Set<String>): Float {
        if (left.isEmpty() || right.isEmpty()) {
            return 0f
        }
        val intersection = left.intersect(right).size.toFloat()
        val union = left.union(right).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }

    private fun tokenize(text: String): Set<String> {
        return text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun buildIdentityId(key: String): String {
        return "identity:${key.lowercase().trim()}"
    }

    private fun buildSessionId(taskKey: String, createdAtMillis: Long): String {
        return "session:${taskKey.lowercase().trim()}:$createdAtMillis"
    }

    private data class ScoredMemoryResult(
        val kind: String,
        val text: String,
        val score: Float,
    )
}
