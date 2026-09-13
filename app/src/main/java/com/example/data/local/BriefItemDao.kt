package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BriefItemDao {

    @Query("SELECT * FROM brief_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<BriefItem>>

    @Query("SELECT * FROM brief_items WHERE type = :type ORDER BY timestamp DESC")
    fun getItemsByType(type: String): Flow<List<BriefItem>>

    @Query("SELECT * FROM brief_items WHERE id = :id")
    suspend fun getItemById(id: Long): BriefItem?

    @Query("SELECT COUNT(*) FROM brief_items")
    suspend fun getItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BriefItem): Long

    @Query("DELETE FROM brief_items WHERE id NOT IN (SELECT id FROM brief_items ORDER BY timestamp DESC LIMIT :maxLimit)")
    suspend fun trimBeyondLimit(maxLimit: Int = 10)

    @Transaction
    suspend fun insertWithRollover(item: BriefItem, maxLimit: Int = 10): Long {
        val insertedId = insert(item)
        trimBeyondLimit(maxLimit)
        return insertedId
    }

    @Query("DELETE FROM brief_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM brief_items")
    suspend fun clearAll()
}
