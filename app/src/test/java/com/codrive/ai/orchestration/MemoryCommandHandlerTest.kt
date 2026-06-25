package com.codrive.ai.orchestration

import com.codrive.ai.memory.IdentityDao
import com.codrive.ai.memory.IdentityEntity
import com.codrive.ai.memory.MemorySearchTool
import com.codrive.ai.memory.SessionContextDao
import com.codrive.ai.memory.SessionContextEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCommandHandlerTest {
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

        override fun clearAll() = Unit
    }

    private class FakeSessionDao(
        private val entries: MutableList<SessionContextEntity> = mutableListOf(),
    ) : SessionContextDao {
        val upserts = mutableListOf<List<SessionContextEntity>>()

        override fun getAll(): MutableList<SessionContextEntity> = entries

        override fun upsertAll(entries: MutableList<SessionContextEntity>) {
            upserts += entries.toList()
            this.entries.clear()
            this.entries.addAll(entries)
        }

        override fun purgeExpired(nowMillis: Long) = Unit

        override fun clearAll() = Unit
    }

    @Test
    fun rememberIdentityCommandStoresDurableMemory() {
        val identityDao = FakeIdentityDao()
        val sessionDao = FakeSessionDao()
        val tool = MemorySearchTool(
            identityDaoProvider = { identityDao },
            sessionContextDaoProvider = { sessionDao },
            nowProvider = { 1_234L },
        )
        val handler = MemoryCommandHandler(tool, nowProvider = { 1_234L })

        val result = handler.tryHandle("remember my address is 123 Main St")

        assertNotNull(result)
        assertEquals("Remembered address.", result!!.finalFeedback)
        assertTrue(identityDao.upserts.isNotEmpty())
        assertEquals("address", identityDao.upserts.single().single().key)
        assertEquals("123 Main St", identityDao.upserts.single().single().value)
    }

    @Test
    fun rememberSessionCommandStoresRollingMemory() {
        val identityDao = FakeIdentityDao()
        val sessionDao = FakeSessionDao()
        val tool = MemorySearchTool(
            identityDaoProvider = { identityDao },
            sessionContextDaoProvider = { sessionDao },
            nowProvider = { 9_999L },
        )
        val handler = MemoryCommandHandler(tool, nowProvider = { 9_999L })

        val result = handler.tryHandle("remember for this session my prompt alias is beta")

        assertNotNull(result)
        assertEquals("Remembered prompt alias for this session.", result!!.finalFeedback)
        assertTrue(sessionDao.upserts.isNotEmpty())
        assertEquals("prompt alias", sessionDao.upserts.single().single().taskKey)
        assertEquals("beta", sessionDao.upserts.single().single().value)
    }

    @Test
    fun nonRememberCommandReturnsNull() {
        val tool = MemorySearchTool(
            identityDaoProvider = { FakeIdentityDao() },
            sessionContextDaoProvider = { FakeSessionDao() },
            nowProvider = { 0L },
        )
        val handler = MemoryCommandHandler(tool)

        assertEquals(null, handler.tryHandle("tap submit"))
    }
}
