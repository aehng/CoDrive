package com.codrive.ai.memory;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SessionContextDao {
    @Query("SELECT * FROM session_context_entries ORDER BY expiresAtMillis DESC")
    List<SessionContextEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<SessionContextEntity> entries);

    @Query("DELETE FROM session_context_entries WHERE expiresAtMillis <= :nowMillis")
    void purgeExpired(long nowMillis);

    @Query("DELETE FROM session_context_entries")
    void clearAll();
}

