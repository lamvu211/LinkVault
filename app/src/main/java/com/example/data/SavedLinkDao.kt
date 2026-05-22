package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLinkDao {
    @Query("SELECT * FROM saved_links ORDER BY timestamp DESC")
    fun getAllLinksFlow(): Flow<List<SavedLink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SavedLink): Long

    @Update
    suspend fun updateLink(link: SavedLink)

    @Delete
    suspend fun deleteLink(link: SavedLink)

    @Query("SELECT * FROM saved_links WHERE id = :id LIMIT 1")
    suspend fun getLinkById(id: Int): SavedLink?

    @Query("SELECT * FROM saved_links WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun getLinksByCategoryIdFlow(categoryId: Int): Flow<List<SavedLink>>

    @Query("UPDATE saved_links SET categoryId = 0 WHERE categoryId = :categoryId")
    suspend fun clearCategoryForLinks(categoryId: Int)

    @Query("DELETE FROM saved_links WHERE categoryId = :categoryId")
    suspend fun deleteLinksByCategoryId(categoryId: Int)
}
