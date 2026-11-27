package com.ominix.vidiio.data.db

import androidx.room.*
import com.ominix.vidiio.data.model.FavoriteMovie
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_movies WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getAllFavorites(profileId: String): Flow<List<FavoriteMovie>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE id = :movieId AND profileId = :profileId)")
    fun isFavorite(movieId: String, profileId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: FavoriteMovie)

    @Delete
    suspend fun delete(movie: FavoriteMovie)
}
