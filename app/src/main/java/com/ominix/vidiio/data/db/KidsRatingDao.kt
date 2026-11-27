package com.ominix.vidiio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ominix.vidiio.data.model.KidsRatingVerdict

@Dao
interface KidsRatingDao {
    @Query("SELECT * FROM kids_rating_verdicts WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): KidsRatingVerdict?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(verdict: KidsRatingVerdict)

    @Query("DELETE FROM kids_rating_verdicts WHERE checkedAt < :before")
    suspend fun pruneBefore(before: Long)
}
