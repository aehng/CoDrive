package com.codrive.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchToolTest {
    private class FakeIdentityDao(
        private val entries: MutableList<IdentityEntity> = mutableListOf(),
    ) : IdentityDao {
        override fun getAll(): MutableList<IdentityEntity> = entries

        override fun upsertAll(entries: MutableList<IdentityEntity>) {
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

        override fun getAll(): MutableList<SessionContextEntity> = entries

        override fun upsertAll(entries: MutableList<SessionContextEntity>) {
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

    @Test
    fun searchPurgesSessionThenReturnsCompactMatches() {
        val now = 2_000L
        val identityDao = FakeIdentityDao(
            mutableListOf(
                IdentityEntity("id-1", "phone", "555-0100", 1_000L),
            ),
        )
        val sessionDao = FakeSessionDao(
            mutableListOf(
                SessionContextEntity("s-expired", "task", "old", expiresAtMillis = 100L),
                SessionContextEntity("s-live", "application", "Option A", expiresAtMillis = 5_000L),
            ),
        )

        val tool = MemorySearchTool(
            identityDaoProvider = { identityDao },
            sessionContextDaoProvider = { sessionDao },
            nowProvider = { now },
        )

        val result = tool.search("app")

        assertEquals(now, sessionDao.purgeCalledWith)
        assertTrue(result.contains("SESSION[application=Option A]"))
    }

    @Test
    fun searchReturnsNoQueryAndNoMatchSignals() {
        val tool = MemorySearchTool(
            identityDaoProvider = { FakeIdentityDao() },
            sessionContextDaoProvider = { FakeSessionDao() },
            nowProvider = { 0L },
        )

        assertEquals("NO_QUERY", tool.search("   "))
        assertEquals("NO_MATCH", tool.search("missing"))
    }
}

