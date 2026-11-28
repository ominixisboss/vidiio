package com.ominix.vidiio.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceCacheKeyTest {

    private val torrentio = "https://torrentio.strem.fun/manifest.json"
    private val piratebay = "https://torrentio.strem.fun/providers=thepiratebay/manifest.json"

    private fun key(
        enabledSources: Set<String> = setOf("knaben", "tg"),
        addons: Set<String> = setOf(torrentio),
        movieId: String = "1399",
        season: Int? = 20,
        episode: Int? = 10,
    ) = sourceCacheKey(movieId, season, episode, enabledSources, addons)

    @Test
    fun `same configuration produces the same key`() {
        assertEquals(key(), key())
    }

    @Test
    fun `adding an addon produces a new key`() {
        assertNotEquals(key(), key(addons = setOf(torrentio, piratebay)))
    }

    @Test
    fun `removing an addon produces a new key`() {
        assertNotEquals(key(addons = setOf(torrentio, piratebay)), key())
    }

    @Test
    fun `adding a scraper produces a new key`() {
        assertNotEquals(key(), key(enabledSources = setOf("knaben", "tg", "cinejoy")))
    }

    @Test
    fun `another episode or title has its own key`() {
        assertNotEquals(key(), key(episode = 11))
        assertNotEquals(key(), key(movieId = "1400"))
    }

    @Test
    fun `key does not depend on set iteration order`() {
        assertEquals(
            key(enabledSources = setOf("knaben", "tg")),
            key(enabledSources = linkedSetOf("tg", "knaben")),
        )
        assertEquals(
            key(addons = setOf(torrentio, piratebay)),
            key(addons = linkedSetOf(piratebay, torrentio)),
        )
    }
}
