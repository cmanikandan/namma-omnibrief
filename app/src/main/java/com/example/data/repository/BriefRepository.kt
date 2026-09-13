package com.example.data.repository

import com.example.data.local.BriefItem
import com.example.data.local.BriefItemDao
import kotlinx.coroutines.flow.Flow

class BriefRepository(private val dao: BriefItemDao) {

    val allItems: Flow<List<BriefItem>> = dao.getAllItems()

    fun getItemsByType(type: String): Flow<List<BriefItem>> = dao.getItemsByType(type)

    suspend fun getItemById(id: Long): BriefItem? = dao.getItemById(id)

    suspend fun getItemCount(): Int = dao.getItemCount()

    suspend fun saveWithRollover(item: BriefItem, maxLimit: Int = 10): Long {
        return dao.insertWithRollover(item, maxLimit)
    }

    suspend fun updateItem(item: BriefItem): Long {
        return dao.insert(item)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
