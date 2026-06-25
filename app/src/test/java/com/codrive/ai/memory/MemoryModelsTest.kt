package com.codrive.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryModelsTest {
    @Test
    fun retentionPolicyProvidesOneHourWindow() {
        val now = 1_000L
        val expiry = MemoryRetentionPolicy.nextSessionExpiry(now)

        assertEquals(3_601_000L, expiry)
        assertFalse(MemoryRetentionPolicy.isExpired(now, expiry))
        assertTrue(MemoryRetentionPolicy.isExpired(expiry, expiry))
    }

    @Test
    fun identityEntityCapturesDurableProfileData() {
        val entity = IdentityEntity(
            id = "identity-1",
            key = "phone",
            value = "+1-555-0100",
            updatedAtMillis = 1_700_000_000_000L,
        )

        assertEquals("identity-1", entity.id)
        assertEquals("phone", entity.key)
        assertEquals("+1-555-0100", entity.value)
        assertEquals("phone +1-555-0100", entity.semanticText())
    }

    @Test
    fun sessionContextEntityCapturesRollingContext() {
        val entity = SessionContextEntity(
            id = "session-1",
            taskKey = "page-choice",
            value = "Option A",
            expiresAtMillis = 1_700_000_360_000L,
        )

        assertEquals("session-1", entity.id)
        assertEquals("page-choice", entity.taskKey)
        assertEquals("Option A", entity.value)
        assertEquals("page-choice Option A", entity.semanticText())
    }

    @Test
    fun memoryEmbeddingCodecRoundTripsFloats() {
        val original = floatArrayOf(0.5f, -1.25f, 3.75f)
        val encoded = MemoryEmbeddingCodec.encode(original)
        val decoded = MemoryEmbeddingCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original.size, decoded!!.size)
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun hashingTextEmbedderProducesStableNonZeroVectors() {
        val embedder = HashingTextEmbedder(dimensions = 64)
        val first = embedder.embed("My address is 123 Main St")
        val second = embedder.embed("My address is 123 Main St")

        assertEquals(first.size, second.size)
        assertTrue(first.any { it != 0f })
        assertEquals(first.toList(), second.toList())
    }
}

