package com.codrive.ai.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,
) {
    fun semanticText(): String = listOf(key, value)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
        .trim()
}

@Entity(tableName = "session_context_entries")
data class SessionContextEntity(
    @PrimaryKey val id: String,
    val taskKey: String,
    val value: String,
    val expiresAtMillis: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,
) {
    fun semanticText(): String = listOf(taskKey, value)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
        .trim()
}

object MemoryEmbeddingCodec {
    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(blob: ByteArray?): FloatArray? {
        if (blob == null || blob.isEmpty() || blob.size % Float.SIZE_BYTES != 0) {
            return null
        }
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(blob.size / Float.SIZE_BYTES)
        for (index in result.indices) {
            result[index] = buffer.float
        }
        return result
    }
}

