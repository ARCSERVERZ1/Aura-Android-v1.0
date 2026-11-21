package com.aura.family.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingTelemetryEntity::class, SentTelemetryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {

    abstract fun pendingTelemetryDao(): PendingTelemetryDao
    abstract fun sentTelemetryDao(): SentTelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
