package com.codrive.ai.memory;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {IdentityEntity.class, SessionContextEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class IdentityDatabase extends RoomDatabase {
    public abstract IdentityDao identityDao();

    public abstract SessionContextDao sessionContextDao();
}

