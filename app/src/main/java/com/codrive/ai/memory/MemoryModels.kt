package com.codrive.ai.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

object MemoryRetentionPolicy {
    const val sessionTtlMillis: Long = 60L * 60L * 1000L

    fun nextSessionExpiry(nowMillis: Long): Long = nowMillis + sessionTtlMillis

    fun isExpired(nowMillis: Long, expiresAtMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

@Entity(tableName = "identity_entries")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val updatedAtMillis: Long,
)

@Entity(tableName = "session_context_entries")
data class SessionContextEntity(
    @PrimaryKey val id: String,
    val taskKey: String,
    val value: String,
    val expiresAtMillis: Long,
)

