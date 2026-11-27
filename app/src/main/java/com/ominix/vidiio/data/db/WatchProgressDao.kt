package com.ominix.vidiio.data.db

import androidx.room.*
import com.ominix.vidiio.data.model.WatchProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT 20")
    fun getRecent(profileId: String): Flow<List<WatchProgress>>

    /** Every saved position, so posters in a grid can show how far in they are. */
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId")
    fun getAll(profileId: String): Flow<List<WatchProgress>>

    @Query("SELECT * FROM watch_progress WHERE id = :id AND profileId = :profileId LIMIT 1")
    suspend fun get(id: String, profileId: String): WatchProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgress)

    @Query("DELETE FROM watch_progress WHERE id = :id AND profileId = :profileId")
    suspend fun delete(id: String, profileId: String)
}
