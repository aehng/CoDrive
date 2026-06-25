package com.codrive.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchToolTest {
    private class FakeIdentityDao(
        private val entries: MutableList<IdentityEntity> = mutableListOf(),
    ) : IdentityDao {
        val upserts = mutableListOf<List<IdentityEntity>>()

        override fun getAll(): MutableList<IdentityEntity> = entries

        override fun upsertAll(entries: MutableList<IdentityEntity>) {
            upserts += entries.toList()
            this.entries.clear()
            this.entries.addAll(entries)
        }

        override fun clearAll() {
            entries.clear()
        }
    }

    private class FakeSessionDao(
        private val entries: MutableList<SessionContextEntity> = mutableListOf(),
    ) : SessionContextDao {
        var purgeCalledWith: Long? = null
        val upserts = mutableListOf<List<SessionContextEntity>>()

        override fun getAll(): MutableList<SessionContextEntity> = entries

        override fun upsertAll(entries: MutableList<SessionContextEntity>) {
            upserts += entries.toList()
            this.entries.clear()
            this.entries.addAll(entries)
        }

        override fun purgeExpired(nowMillis: Long) {
            purgeCalledWith = nowMillis
            entries.removeAll { it.expiresAtMillis <= nowMillis }
        }

        override fun clearAll() {
            entries.clear()
        }
    }

    private class FakeEmbedder : TextEmbedder {
        override fun embed(text: String): FloatArray {
            val normalized = text.lowercase()
            return floatArrayOf(
                if (normalized.contains("address")) 1f else 0f,
                if (normalized.contains("resume")) 1f else 0f,
                if (normalized.contains("phone")) 1f else 0f,
            )
        }
    }

    @Test
    fun searchPurgesSessionThenReturnsSemanticMatches() {
        val now = 2_000L
        val identityDao = FakeIdentityDao(
            mutableListOf(
                IdentityEntity(
                    id = "id-1",
                    key = "home address",
                    value = "123 Main St",
                    updatedAtMillis = 1_000L,
                    embedding = MemoryEmbeddingCodec.encode(floatArrayOf(1f, 0f, 0f)),
                ),
            ),
        )
        val sessionDao = FakeSessionDao(
            mutableListOf(
                SessionContextEntity(
                    id = "s-expired",
                    taskKey = "task",
                    value = "old",
                    expiresAtMillis = 100L,
                    embedding = MemoryEmbeddingCodec.encode(floatArrayOf(0.1f, 0.1f, 0.1f)),
                ),
                SessionContextEntity(
                    id = "s-live",
                    taskKey = "resume",
                    value = "Software engineer at Example Corp",
                    expiresAtMillis = 5_000L,
                    embedding = MemoryEmbeddingCodec.encode(floatArrayOf(0f, 1f, 0f)),
                ),
            ),
        )

        val tool = MemorySearchTool(
            identityDaoProvider = { identityDao },
            sessionContextDaoProvider = { sessionDao },
            embedder = FakeEmbedder(),
            nowProvider = { now },
        )

        val result = tool.search("address resume")

        assertEquals(now, sessionDao.purgeCalledWith)
        assertTrue(result.startsWith("MEMORY_RESULT:"))
        assertTrue(result.contains("identity: home address 123 Main St"))
        assertTrue(result.contains("session: resume Software engineer at Example Corp"))
    }

    @Test
    fun rememberHelpersStoreEmbeddingsWithStableIds() {
        val identityDao = FakeIdentityDao()
        val sessionDao = FakeSessionDao()
        val tool = MemorySearchTool(
            identityDaoProvider = { identityDao },
            sessionContextDaoProvider = { sessionDao },
            embedder = FakeEmbedder(),
            nowProvider = { 1_234L },
        )

        val identity = tool.rememberIdentity("phone", "+1-555-0100")
        val session = tool.rememberSessionContext("resume", "Jane Doe, senior engineer")

        assertNotNull(identity)
        assertNotNull(session)
        assertTrue(identityDao.upserts.isNotEmpty())
        assertTrue(sessionDao.upserts.isNotEmpty())
        assertEquals("identity:phone", identity!!.id)
        assertEquals("session:resume:1234", session!!.id)
        assertTrue(identity.embedding?.isNotEmpty() == true)
        assertTrue(session.embedding?.isNotEmpty() == true)
    }

    @Test
    fun searchReturnsNoQueryAndNoMatchSignals() {
        val tool = MemorySearchTool(
            identityDaoProvider = { FakeIdentityDao() },
            sessionContextDaoProvider = { FakeSessionDao() },
            embedder = FakeEmbedder(),
            nowProvider = { 0L },
        )

        assertEquals("NO_QUERY", tool.search("   "))
        assertEquals("NO_MATCH", tool.search("missing"))
    }
}
