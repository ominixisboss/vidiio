package com.ominix.vidiio.data.stremio

import com.ominix.vidiio.data.model.stremio.StremioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioStreamMapperTest {

    @Test
    fun `the sign-in clip never becomes a source`() {
        // What PenguPlay answers with when the token is missing, stale or revoked.
        assertNull(
            StremioStream(url = "https://pengu.uk/signin.mp4", title = "You must sign in")
                .toStreamSource()
        )
        // Same clip behind a query string, and recognised by its label alone.
        assertNull(StremioStream(url = "https://pengu.uk/signin.mp4?x=1").toStreamSource())
        assertNull(
            StremioStream(url = "https://example.com/a.mp4", title = "You must sign in at pengu.uk")
                .toStreamSource()
        )
    }

    @Test
    fun `a real pengu link is still mapped`() {
        val source = StremioStream(
            name = "PenguPlay",
            title = "PenguPlay 1080p",
            url = "https://pengu.uk/play/abc?psig=1",
        ).toStreamSource()

        assertNotNull(source)
        assertEquals("https://pengu.uk/play/abc?psig=1", source!!.url)
    }

    @Test
    fun `a web-page-only feed is kept but marked as a page`() {
        // NuvioSports' PPV feeds carry no file URL, only their player page. The sheet uses the flag
        // to say a page opens; a real link must not be marked as one.
        val page = StremioStream(
            name = "Nuvio Live Sports",
            title = "🌐 Web Stream",
            externalUrl = "https://nuviosports.xyz/watch?url=https%3A%2F%2Fexample.com%2Fembed%2Fabc",
        ).toStreamSource()

        assertNotNull(page)
        assertTrue(page!!.isWebPage)
        assertEquals("https://example.com/embed/abc", page.url)

        val direct = StremioStream(url = "https://cdn.example.com/a.m3u8").toStreamSource()
        assertNotNull(direct)
        assertFalse(direct!!.isWebPage)
    }
}
