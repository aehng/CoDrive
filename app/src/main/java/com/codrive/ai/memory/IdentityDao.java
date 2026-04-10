package com.codrive.ai.memory;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface IdentityDao {
    @Query("SELECT * FROM identity_entries ORDER BY updatedAtMillis DESC")
    List<IdentityEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<IdentityEntity> entries);

    @Query("DELETE FROM identity_entries")
    void clearAll();
}

