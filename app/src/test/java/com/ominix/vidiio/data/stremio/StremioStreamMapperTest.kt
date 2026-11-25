package com.ominix.vidiio.data.stremio

import com.ominix.vidiio.data.model.stremio.StremioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
