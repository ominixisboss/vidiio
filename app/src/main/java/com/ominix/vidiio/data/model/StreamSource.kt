package com.ominix.vidiio.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamSource(
    val serverName: String,
    val url: String,
    val sourceName: String = "",
    val sourceId: String = "",
    val isM3u8: Boolean = true,
    /** A live feed (IPTV channel, live event): never resumed, progressed or auto-advanced. */
    val isLive: Boolean = false,
    /**
     * Not a media file but a provider's web player page (NuvioSports' PPV feeds): the player opens
     * it in its WebView. Kept apart from a stream so the UI says a page will open.
     */
    val isWebPage: Boolean = false,
    val quality: String? = null,
    val size: String? = null,
    val seeders: Int? = null,
    val headers: Map<String, String>? = null,
    /** For multi-file torrents: the addon-chosen file index (0-based) and/or name. */
    val fileIndex: Int? = null,
    val fileName: String? = null
)
