package com.codrive.ai.llm

import com.codrive.ai.contracts.LlmClient
import com.codrive.ai.model.AgentDecision
import com.codrive.ai.model.PrunedUiMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class DualRoutingLlmClient(
    private val groqClient: LlmClient,
    private val geminiClient: LlmClient,
    groqPercent: Int,
) : LlmClient {
    private val safeGroqPercent = groqPercent.coerceIn(0, 100)
    private val cursor = AtomicInteger(0)

    override fun infer(command: String, uiMap: PrunedUiMap): AgentDecision {
        return pickClient().infer(command, uiMap)
    }

    override fun cancel() {
        groqClient.cancel()
        geminiClient.cancel()
    }

    private fun pickClient(): LlmClient {
        if (safeGroqPercent <= 0) return geminiClient
        if (safeGroqPercent >= 100) return groqClient

        val groqSlots = safeGroqPercent
        val geminiSlots = 100 - safeGroqPercent
        val gcdValue = gcd(groqSlots, geminiSlots)
        val groqCount = groqSlots / gcdValue
        val geminiCount = geminiSlots / gcdValue
        val cycleLength = groqCount + geminiCount
        val index = abs(cursor.getAndIncrement()) % cycleLength
        return if (index < groqCount) groqClient else geminiClient
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0) 1 else x
    }
}
