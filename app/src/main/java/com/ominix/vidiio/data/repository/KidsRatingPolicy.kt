package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.KidsMaturity

/**
 * Pure age-rating rules for kids profiles.
 *
 * Kept free of Android and network types so the policy can be unit-tested. The repository only
 * supplies the raw TMDB values.
 */

/** One US release entry, reduced to what the policy needs. */
internal data class UsRelease(val type: Int?, val certification: String?)

/** US film certification order, mildest first. Anything absent (NR, Unrated) is not allowed. */
private val MOVIE_CERT_RANK = mapOf("G" to 0, "PG" to 1, "PG-13" to 2, "R" to 3, "NC-17" to 4)

/**
 * The certification that represents a film's US release.
 *
 * TMDB lists every US release, and re-releases often carry a different or missing certificate.
 * The old rule required *every* entry to be G/PG, so a PG family film with an unrated digital
 * release disappeared. Prefer the theatrical release, then a limited theatrical one; among the
 * chosen type take the strictest known certificate. A film with no rated entry stays unrated and
 * is rejected by [isMovieCertificationAllowed].
 */
internal fun representativeMovieCertification(releases: List<UsRelease>): String? {
    val rated = releases.mapNotNull { release ->
        val cert = release.certification?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (cert == null) null else release.type to cert
    }
    if (rated.isEmpty()) return null

    val preferredType = listOf(3, 2).firstOrNull { type -> rated.any { it.first == type } }
    val candidates = if (preferredType != null) rated.filter { it.first == preferredType } else rated
    val known = candidates.filter { it.second in MOVIE_CERT_RANK }
    if (known.isEmpty()) return candidates.first().second
    return known.maxByOrNull { MOVIE_CERT_RANK.getValue(it.second) }?.second
}

/** True only for a known certification at or below the profile's ceiling. */
internal fun isMovieCertificationAllowed(certification: String?, maturity: KidsMaturity): Boolean {
    val cert = certification?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return false
    val rank = MOVIE_CERT_RANK[cert] ?: return false
    return rank <= (MOVIE_CERT_RANK[maturity.movieMax] ?: return false)
}

/**
 * True for a US TV rating the profile may see. The hyphen check covers suffixes such as
 * TV-Y7-FV; the exact check keeps TV-Y7 from matching TV-Y.
 */
internal fun isTvRatingAllowed(rating: String?, maturity: KidsMaturity): Boolean {
    val value = rating?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return false
    return maturity.tvRatings.any { allowed -> value == allowed || value.startsWith("$allowed-") }
}
