package com.ominix.vidiio.data.model

import androidx.room.Entity

/**
 * One "Continue Watching" entry. Keyed by movie id (one row per title); for TV the row
 * tracks whichever episode was last played.
 */
// One row per title per profile: each profile keeps its own resume point.
@Entity(tableName = "watch_progress", primaryKeys = ["id", "profileId"])
data class WatchProgress(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val type: String, // MOVIE or TV_SHOW
    val source: String,
    val imdbId: String? = null,
    val episodeId: String? = null,
    val episodeLabel: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val profileId: String = "default",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

fun WatchProgress.toMovie() = Movie(
    id = id,
    title = title,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    synopsis = synopsis,
    year = year,
    rating = rating,
    type = MovieType.valueOf(type),
    source = source,
    imdbId = imdbId
)
