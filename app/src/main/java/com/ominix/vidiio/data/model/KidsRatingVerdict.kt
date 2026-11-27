package com.ominix.vidiio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One remembered age-rating answer, so every launch does not re-ask TMDB about the same title.
 *
 * The key carries the maturity level: a title allowed for an older-kids profile may still be
 * blocked for a little-kids one.
 */
@Entity(tableName = "kids_rating_verdicts")
data class KidsRatingVerdict(
    @PrimaryKey val key: String,
    val allowed: Boolean,
    val checkedAt: Long,
)
