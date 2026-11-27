package com.ominix.vidiio.data.model

/**
 * How strict a kids profile is.
 *
 * Stored as an Int so the profile table stays plain and old rows migrate without a converter;
 * [dbValue] is the contract, never the ordinal.
 */
enum class KidsMaturity(
    val dbValue: Int,
    val label: String,
    val summary: String,
    /** Highest US film certification the profile may see. */
    val movieMax: String,
    /** US TV content ratings the profile may see. */
    val tvRatings: Set<String>,
) {
    LITTLE(
        dbValue = 0,
        label = "Little kids",
        summary = "G films · TV-Y, TV-Y7 and TV-G shows",
        movieMax = "G",
        tvRatings = setOf("TV-Y", "TV-Y7", "TV-G"),
    ),
    OLDER(
        dbValue = 1,
        label = "Older kids",
        summary = "Up to PG films · up to TV-PG shows",
        movieMax = "PG",
        tvRatings = setOf("TV-Y", "TV-Y7", "TV-G", "TV-PG"),
    );

    companion object {
        /** Unknown values (a future level read by an older build) fall back to the stricter tier. */
        fun fromDb(value: Int): KidsMaturity = entries.firstOrNull { it.dbValue == value } ?: LITTLE
    }
}
