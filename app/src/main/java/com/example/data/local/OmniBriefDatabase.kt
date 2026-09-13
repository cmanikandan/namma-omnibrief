package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BriefItem::class], version = 1, exportSchema = false)
abstract class OmniBriefDatabase : RoomDatabase() {
    abstract fun briefItemDao(): BriefItemDao

    companion object {
        @Volatile
        private var INSTANCE: OmniBriefDatabase? = null

        fun getDatabase(context: Context): OmniBriefDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniBriefDatabase::class.java,
                    "omnibrief_database"
                ).fallbackToDestructiveMigration(false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
