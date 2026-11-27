package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.model.KidsMaturity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsRatingPolicyTest {

    @Test
    fun `pg theatrical stays allowed when an unrated digital release exists`() {
        val cert = representativeMovieCertification(
            listOf(UsRelease(type = 3, certification = "PG"), UsRelease(type = 5, certification = "NR"))
        )

        assertEquals("PG", cert)
        assertTrue(isMovieCertificationAllowed(cert, KidsMaturity.OLDER))
    }

    @Test
    fun `theatrical release wins over a different digital certificate`() {
        val cert = representativeMovieCertification(
            listOf(UsRelease(type = 4, certification = "R"), UsRelease(type = 3, certification = "PG"))
        )

        assertEquals("PG", cert)
        assertTrue(isMovieCertificationAllowed(cert, KidsMaturity.OLDER))
    }

    @Test
    fun `pg13 is blocked for older kids`() {
        assertFalse(isMovieCertificationAllowed("PG-13", KidsMaturity.OLDER))
    }

    @Test
    fun `pg is blocked for little kids but g is allowed`() {
        assertFalse(isMovieCertificationAllowed("PG", KidsMaturity.LITTLE))
        assertTrue(isMovieCertificationAllowed("G", KidsMaturity.LITTLE))
    }

    @Test
    fun `unrated and unknown film certificates fail closed`() {
        assertFalse(isMovieCertificationAllowed(null, KidsMaturity.OLDER))
        assertFalse(isMovieCertificationAllowed("NR", KidsMaturity.OLDER))
        assertFalse(isMovieCertificationAllowed("Unrated", KidsMaturity.OLDER))
    }

    @Test
    fun `no released certificate is reported as unrated`() {
        assertNull(representativeMovieCertification(emptyList()))
        assertNull(representativeMovieCertification(listOf(UsRelease(3, null))))
        assertNull(representativeMovieCertification(listOf(UsRelease(3, "  "))))
    }

    @Test
    fun `tv-pg is allowed for older kids only`() {
        assertTrue(isTvRatingAllowed("TV-PG", KidsMaturity.OLDER))
        assertFalse(isTvRatingAllowed("TV-PG", KidsMaturity.LITTLE))
    }

    @Test
    fun `tv suffixes and the y7 boundary are handled`() {
        assertTrue(isTvRatingAllowed("TV-Y7-FV", KidsMaturity.LITTLE))
        assertTrue(isTvRatingAllowed("TV-Y7", KidsMaturity.LITTLE))
        assertTrue(isTvRatingAllowed("TV-Y", KidsMaturity.OLDER))
    }

    @Test
    fun `teen and adult tv ratings fail closed`() {
        assertFalse(isTvRatingAllowed("TV-14", KidsMaturity.OLDER))
        assertFalse(isTvRatingAllowed("TV-MA", KidsMaturity.OLDER))
        assertFalse(isTvRatingAllowed(null, KidsMaturity.OLDER))
        assertFalse(isTvRatingAllowed("", KidsMaturity.OLDER))
    }

    @Test
    fun `unknown stored maturity falls back to the stricter tier`() {
        assertEquals(KidsMaturity.LITTLE, KidsMaturity.fromDb(99))
    }
}
