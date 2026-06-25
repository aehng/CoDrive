package com.codrive.ai.memory

import java.util.Locale

interface TextEmbedder {
    fun embed(text: String): FloatArray
}

class HashingTextEmbedder @JvmOverloads constructor(
    private val dimensions: Int = 384,
) : TextEmbedder {
    override fun embed(text: String): FloatArray {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) {
            return FloatArray(dimensions)
        }

        val vector = FloatArray(dimensions)
        tokens.forEachIndexed { index, token ->
            val tokenHash = token.hashCode()
            val primaryIndex = floorMod(tokenHash, dimensions)
            vector[primaryIndex] += 1f

            if (index < tokens.lastIndex) {
                val bigram = token + "_" + tokens[index + 1]
                val bigramIndex = floorMod(bigram.hashCode(), dimensions)
                vector[bigramIndex] += 0.5f
            }
        }

        normalize(vector)
        return vector
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
    }

    private fun normalize(values: FloatArray) {
        var sumSquares = 0f
        for (value in values) {
            sumSquares += value * value
        }
        if (sumSquares == 0f) {
            return
        }
        val scale = 1f / kotlin.math.sqrt(sumSquares)
        for (index in values.indices) {
            values[index] *= scale
        }
    }

    private fun floorMod(value: Int, modulus: Int): Int {
        val remainder = value % modulus
        return if (remainder >= 0) remainder else remainder + modulus
    }
}
