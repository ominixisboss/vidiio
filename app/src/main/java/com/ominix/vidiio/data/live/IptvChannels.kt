package com.ominix.vidiio.data.live

import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One free live channel from the iptv-org lists. */
data class LiveChannel(
    val name: String,
    val logo: String?,
    val category: String,
    val source: StreamSource,
)

/** The iptv-org list last downloaded for a country, reused until it goes stale. */
private data class CachedChannels(
    val country: String,
    val at: Long,
    val rows: List<Pair<String, List<LiveChannel>>>,
)

/**
 * Free, publicly available live TV channels from iptv-org (github.com/iptv-org/iptv): Pluto TV,
 * Samsung TV Plus, Xumo, network news feeds and the like, for one country.
 *
 * Channels the list flags as geo-blocked are left out, since most of them won't play here.
 */
class IptvChannels(private val client: OkHttpClient) {

    /** The list last downloaded for a country. Only ever replaced whole, so no lock is needed. */
    @Volatile
    private var cached: CachedChannels? = null

    /** Channels grouped by category, best-known categories first. Throws on network failure. */
    suspend fun byCategory(country: String = "us"): List<Pair<String, List<LiveChannel>>> = withContext(Dispatchers.IO) {
        // Re-opening Live TV should not download the whole country list again.
        val hit = cached
        if (hit != null && hit.country == country && System.currentTimeMillis() - hit.at < CACHE_TTL_MS) {
            return@withContext hit.rows
        }

        val request = Request.Builder()
            .url("https://iptv-org.github.io/iptv/countries/$country.m3u")
            .header("User-Agent", USER_AGENT)
            .build()
        val rows = try {
            val text = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("Channel list HTTP ${response.code}")
                response.body.string()
            }
            val channels = parse(text).toMutableList()
            channels.addAll(0, ADULT_SWIM_CHANNELS)
            channels
                .groupBy { it.category }
                .toList()
                .sortedWith(compareBy({ CATEGORY_ORDER.indexOf(it.first).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.first }))
        } catch (e: java.io.IOException) {
            // A flaky network should not empty the page: an old list beats no list at all.
            cached?.takeIf { it.country == country }?.rows ?: throw e
        }
        cached = CachedChannels(country, System.currentTimeMillis(), rows)
        rows
    }

    /**
     * The channels behind a link the viewer pasted: an M3U (or plain URL) channel list becomes
     * its channels, and anything else - an HLS or DASH stream, a video file - becomes one
     * channel named [name].
     */
    suspend fun fromLink(name: String, url: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        val link = url.trim()
        val path = link.substringBefore('?').substringBefore('#').lowercase()
        val single = listOf(channel(name, link, category = name))

        if (!link.startsWith("http://", ignoreCase = true) && !link.startsWith("https://", ignoreCase = true)) {
            return@withContext single
        }
        // A media URL says what it is. Fetching it would only pull megabytes of video to learn that.
        if (DIRECT_EXTENSIONS.any { path.endsWith(it) }) return@withContext single

        val headers = defaultHeaders(link)
        // A small ranged read, not the whole thing: a live feed must not be downloaded just to
        // find out that it is a live feed.
        val prefix = readHead(link, headers, PROBE_BYTES) ?: return@withContext single
        val head = prefix.trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        // An HLS playlist is a single feed, even though it is full of #EXTINF lines. Reading it
        // as a channel list used to turn every quality variant or segment into a "channel".
        if (head.contains("#EXT-X-")) return@withContext single

        // A channel list is the one case worth downloading in full.
        val listing = if (head.startsWith("#EXTM3U", ignoreCase = true)) {
            readHead(link, headers, MAX_PLAYLIST_BYTES) ?: prefix
        } else {
            prefix
        }
        val channels = parse(listing, skipGeoBlocked = false, baseUrl = link)
        if (channels.isEmpty()) single else channels.map { it.copy(category = name) }
    }

    /** A single pasted stream, as one channel. */
    private fun channel(name: String, url: String, category: String): LiveChannel {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return LiveChannel(
            name = name,
            logo = null,
            category = category,
            source = StreamSource(
                serverName = name,
                url = url,
                sourceName = "Live TV",
                sourceId = "iptv",
                isM3u8 = path.contains("m3u8"),
                isLive = true,
                quality = "Live",
                headers = defaultHeaders(url),
            ),
        )
    }

    /** The first [limit] bytes of [url] as text, or null when it could not be read. */
    private fun readHead(url: String, headers: Map<String, String>, limit: Long): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-$limit")
            .header("Accept", "*/*")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val source = response.body.source()
            source.request(limit)
            source.buffer.clone().readUtf8(minOf(source.buffer.size, limit))
        }
    }.getOrNull()

    companion object {
        private const val ADULT_SWIM_LOGO =
            "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png"
        private const val ADULT_SWIM_CATEGORY = "Adult Swim 24/7"

        /** One of Adult Swim's always-on streams: same logo, category, headers and format every time. */
        private fun adultSwimChannel(name: String, url: String) = LiveChannel(
            name = name,
            logo = ADULT_SWIM_LOGO,
            category = ADULT_SWIM_CATEGORY,
            source = StreamSource(
                serverName = name,
                url = url,
                sourceName = "Adult Swim",
                sourceId = "iptv",
                isM3u8 = true,
                isLive = true,
                quality = "24/7 Live HD",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to "https://www.adultswim.com/",
                ),
            ),
        )

        val ADULT_SWIM_CHANNELS = listOf(
            adultSwimChannel("Rick and Morty 24/7", "https://adultswim-vodlive.cdn.turner.com/live/test-2/stream.m3u8"),
            adultSwimChannel("Rick and Morty (Alt)", "https://adultswim-vodlive.cdn.turner.com/live/rick-and-morty/stream.m3u8"),
            adultSwimChannel("Toonami 24/7", "https://amd.cdn.turner.com/adultswim/big/streams/playlists/toonami.m3u8"),
            adultSwimChannel("Superjail! 24/7", "https://adultswim-vodlive.cdn.turner.com/live/superjail/stream.m3u8"),
            adultSwimChannel("Tim and Eric 24/7", "https://adultswim-vodlive.cdn.turner.com/live/tim-and-eric/stream.m3u8"),
            adultSwimChannel("Off the Air 24/7", "https://adultswim-vodlive.cdn.turner.com/live/off-the-air/stream.m3u8"),
            adultSwimChannel("The Venture Bros. 24/7", "https://adultswim-vodlive.cdn.turner.com/live/venture-bros/stream.m3u8"),
            adultSwimChannel("The Eric Andre Show 24/7", "https://adultswim-vodlive.cdn.turner.com/live/eric-andre/stream.m3u8"),
            adultSwimChannel("Williams Stream 24/7", "https://adultswim-vodlive.cdn.turner.com/live/williams-stream/stream.m3u8"),
        )

        // Media the player handles directly: never fetched while adding a link.
        private val DIRECT_EXTENSIONS = listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".ts", ".webm", ".mov", ".m4v", ".flv")
        private const val MAX_PLAYLIST_BYTES = 4L * 1024 * 1024

        /** Enough of a pasted link to tell a playlist from a video. */
        private const val PROBE_BYTES = 64L * 1024

        /** How long the iptv-org country list is reused before it is downloaded again. */
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val CATEGORY_ORDER = listOf(
            "Adult Swim 24/7", "News", "Sports", "Movies", "Entertainment", "Series", "Kids", "Animation", "Comedy",
            "Documentary", "Music", "Classic", "Lifestyle", "Cooking", "Outdoor", "Travel",
            "Business", "Weather", "Education", "Science", "Shop", "General", "Religious",
            "Legislative", "Other",
        )
        // Values may be quoted or bare: `tvg-id="x"` and `group-title=News` both occur in real lists.
        private val ATTR = Regex("""([\w-]+)=(?:"([^"]*)"|([^,\s]+))""")
        private val NAME_NOISE = Regex("""\s*(\(\d{3,4}p\)|\[[^]]*])""")

        /**
         * Parses an M3U channel list, resolving relative URLs against [baseUrl] and attaching the
         * headers an entry asks for (#EXTVLCOPT / #KODIPROP). Options appended to a URL with a
         * pipe ("...m3u8|User-Agent=...") are stripped, not honoured.
         */
        fun parse(m3u: String, skipGeoBlocked: Boolean = true, baseUrl: String? = null): List<LiveChannel> {
            val channels = ArrayList<LiveChannel>()
            val base = baseUrl?.toHttpUrlOrNull()
            val defaults = defaultHeaders(baseUrl)
            var info: String? = null
            var group: String? = null
            val headers = HashMap<String, String>()

            for (raw in m3u.lineSequence()) {
                val line = raw.trim().removePrefix("\uFEFF")
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        info = line
                        headers.clear()
                        group = null
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) ->
                        group = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) ->
                        applyHeaderOption(line.substringAfter(':'), headers)
                    line.startsWith("#KODIPROP:", ignoreCase = true) ->
                        applyHeaderOption(line.substringAfter(':'), headers)
                    line.startsWith("#") -> Unit
                    info != null -> {
                        val meta = info
                        info = null
                        if (skipGeoBlocked && meta.contains("Geo-blocked", ignoreCase = true)) continue

                        // Lists sometimes append options to the URL ("...m3u8|User-Agent=...");
                        // the stream itself plays without them.
                        val streamUrl = resolveStreamUrl(line.substringBefore('|'), base) ?: continue

                        val (attrText, rawName) = extInfParts(meta)
                        val attrs = ATTR.findAll(attrText).associate { match ->
                            match.groupValues[1] to match.groupValues[2].ifEmpty { match.groupValues[3] }
                        }
                        val name = rawName.replace(NAME_NOISE, "").trim()
                            .ifBlank { attrs["tvg-name"]?.trim().orEmpty() }
                            .ifBlank { runCatching { java.net.URI(streamUrl).host }.getOrNull() ?: streamUrl }
                        val category = attrs["group-title"].orEmpty().split(';').first().trim()
                            .ifBlank { group.orEmpty() }
                            .takeUnless { it.isBlank() || it.equals("Undefined", ignoreCase = true) }
                            ?: "Other"

                        val channelHeaders = HashMap(defaults)
                        channelHeaders.putAll(headers)

                        channels += LiveChannel(
                            name = name,
                            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                            category = category,
                            source = StreamSource(
                                serverName = name,
                                url = streamUrl,
                                sourceName = "Live TV",
                                sourceId = "iptv",
                                isM3u8 = streamUrl.substringBefore('?').contains("m3u8", ignoreCase = true),
                                isLive = true,
                                quality = "Live",
                                headers = channelHeaders.takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                }
            }
            return channels
        }

        /** Splits "#EXTINF:-1 tvg-id=...,Name" at the comma that ends its attribute list. */
        private fun extInfParts(meta: String): Pair<String, String> {
            var quoted = false
            for (i in meta.indices) {
                when {
                    meta[i] == '"' -> quoted = !quoted
                    meta[i] == ',' && !quoted -> return meta.substring(0, i) to meta.substring(i + 1)
                }
            }
            return meta to ""
        }

        private fun applyHeaderOption(option: String, into: MutableMap<String, String>) {
            val at = option.indexOf('=')
            if (at <= 0) return
            val key = option.substring(0, at).trim().lowercase()
            val value = option.substring(at + 1).trim()
            if (value.isEmpty()) return
            when (key.substringAfterLast('.')) {
                "http-user-agent", "user-agent", "useragent" -> into["User-Agent"] = value
                "http-referer", "referer", "referrer" -> into["Referer"] = value
                "http-origin", "origin" -> into["Origin"] = value
                "http-cookie", "cookie" -> into["Cookie"] = value
            }
        }

        /** Absolute stream URL: as given, or resolved against the playlist's own URL. */
        private fun resolveStreamUrl(raw: String, base: HttpUrl?): String? {
            val url = raw.trim()
            val absolute = when {
                url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
                base != null -> base.resolve(url)?.toString()
                else -> null
            } ?: return null
            // ExoPlayer cannot open these either, so an entry pointing at one would just fail.
            return absolute.takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            }
        }

        private fun defaultHeaders(baseUrl: String?): MutableMap<String, String> {
            val headers = mutableMapOf("User-Agent" to USER_AGENT)
            val origin = baseUrl?.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
            if (origin != null) {
                headers["Referer"] = "$origin/"
                headers["Origin"] = origin
            }
            return headers
        }
    }
}
