package com.ominix.vidiio.data.live

import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** One free live channel from the iptv-org lists. */
data class LiveChannel(
    val name: String,
    val logo: String?,
    val category: String,
    val source: StreamSource,
)

/**
 * Free, publicly available live TV channels from iptv-org (github.com/iptv-org/iptv): Pluto TV,
 * Samsung TV Plus, Xumo, network news feeds and the like, for one country.
 *
 * Channels the list flags as geo-blocked are left out, since most of them won't play here.
 */
class IptvChannels(private val client: OkHttpClient) {

    /** Channels grouped by category, best-known categories first. Throws on network failure. */
    suspend fun byCategory(country: String = "us"): List<Pair<String, List<LiveChannel>>> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://iptv-org.github.io/iptv/countries/$country.m3u").build()
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
    }

    /**
     * The channels behind a link the viewer pasted: an M3U channel list becomes its channels, and
     * anything else (an HLS or DASH stream, a video file) becomes one channel named [name].
     */
    suspend fun fromLink(name: String, url: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        val path = url.substringBefore('?').lowercase()
        val origin = runCatching { java.net.URI(url).let { "${it.scheme}://${it.host}" } }.getOrNull()
        val defaultHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        origin?.let {
            defaultHeaders["Referer"] = "$it/"
            defaultHeaders["Origin"] = it
        }

        val single = listOf(LiveChannel(name, null, name, StreamSource(
            serverName = name, url = url, sourceName = "Live TV", sourceId = "iptv",
            isM3u8 = path.endsWith(".m3u8") || path.contains("m3u8") || path.contains("playlist"), quality = "Live",
            headers = defaultHeaders,
        )))

        if (DIRECT_EXTENSIONS.any { path.endsWith(it) }) return@withContext single

        val request = Request.Builder()
            .url(url)
            .apply { defaultHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return@withContext single

        val head = response.use { resp ->
            if (!resp.isSuccessful) return@withContext single
            val src = resp.body.source()
            src.request(MAX_PLAYLIST_BYTES)
            src.buffer.clone().readUtf8()
        }
        val isChannelList = head.contains("#EXTINF") || head.contains("#EXTM3U")
        if (!isChannelList) single
        else parse(head, skipGeoBlocked = false, baseUrl = url).map { it.copy(category = name) }.ifEmpty { single }
    }

    companion object {
        val ADULT_SWIM_CHANNELS = listOf(
            LiveChannel(
                name = "Rick and Morty 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Rick and Morty 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/test-2/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "Rick and Morty (Alt)",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Rick and Morty (Alt)",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/rick-and-morty/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "Toonami 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Toonami 24/7",
                    url = "https://amd.cdn.turner.com/adultswim/big/streams/playlists/toonami.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "Superjail! 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Superjail! 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/superjail/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "Tim and Eric 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Tim and Eric 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/tim-and-eric/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "Off the Air 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Off the Air 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/off-the-air/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "The Venture Bros. 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "The Venture Bros. 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/venture-bros/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "The Eric Andre Show 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "The Eric Andre Show 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/eric-andre/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            ),
            LiveChannel(
                name = "Williams Stream 24/7",
                logo = "https://images.pluto.tv/channels/5d1f8876c2438848d6015a99/colorLogoPNG.png",
                category = "Adult Swim 24/7",
                source = StreamSource(
                    serverName = "Williams Stream 24/7",
                    url = "https://adultswim-vodlive.cdn.turner.com/live/williams-stream/stream.m3u8",
                    sourceName = "Adult Swim",
                    sourceId = "iptv",
                    isM3u8 = true,
                    quality = "24/7 Live HD",
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Referer" to "https://www.adultswim.com/")
                )
            )
        )

        private val DIRECT_EXTENSIONS = listOf(".mpd", ".mp4", ".mkv", ".ts", ".webm", ".mov")
        private const val MAX_PLAYLIST_BYTES = 4L * 1024 * 1024
        private val CATEGORY_ORDER = listOf(
            "Adult Swim 24/7", "News", "Sports", "Movies", "Entertainment", "Series", "Kids", "Animation", "Comedy",
            "Documentary", "Music", "Classic", "Lifestyle", "Cooking", "Outdoor", "Travel",
            "Business", "Weather", "Education", "Science", "Shop", "General", "Religious",
            "Legislative", "Other",
        )
        private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
        private val NAME_NOISE = Regex("""\s*(\(\d{3,4}p\)|\[[^]]*])""")

        /** Parses an M3U playlist, resolving relative URLs against [baseUrl] and attaching Referer/User-Agent. */
        fun parse(m3u: String, skipGeoBlocked: Boolean = true, baseUrl: String? = null): List<LiveChannel> {
            val channels = ArrayList<LiveChannel>()
            var info: String? = null
            val headers = HashMap<String, String>()

            val defaultHeaders = mutableMapOf<String, String>()
            defaultHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            baseUrl?.let { url ->
                val origin = runCatching { java.net.URI(url).let { "${it.scheme}://${it.host}" } }.getOrNull()
                origin?.let {
                    defaultHeaders["Referer"] = "$it/"
                    defaultHeaders["Origin"] = it
                }
            }

            for (raw in m3u.lineSequence()) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF") -> { info = line; headers.clear() }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val opt = line.substringAfter(':').trim()
                        val key = opt.substringBefore('=').trim().lowercase()
                        val value = opt.substringAfter('=').trim()
                        when (key) {
                            "http-user-agent", "user-agent" -> headers["User-Agent"] = value
                            "http-referer", "referer", "referrer" -> headers["Referer"] = value
                            "origin" -> headers["Origin"] = value
                        }
                    }
                    !line.startsWith("#") && line.isNotBlank() && info != null -> {
                        val meta = info
                        info = null
                        if (skipGeoBlocked && meta.contains("Geo-blocked")) continue

                        val streamUrl = when {
                            line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> line
                            line.startsWith("/") && baseUrl != null -> {
                                val root = runCatching { java.net.URI(baseUrl).let { "${it.scheme}://${it.host}" } }.getOrNull()
                                if (root != null) "$root$line" else line
                            }
                            baseUrl != null -> {
                                val folder = baseUrl.substringBeforeLast('/') + "/"
                                "$folder$line"
                            }
                            else -> line
                        }

                        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) continue

                        val attrs = ATTR.findAll(meta.substringBefore(",")).associate { it.groupValues[1] to it.groupValues[2] }
                        val name = meta.substringAfterLast(',').replace(NAME_NOISE, "").trim().ifBlank { continue }
                        val category = attrs["group-title"].orEmpty().split(';').first().trim()
                            .takeUnless { it.isBlank() || it == "Undefined" } ?: "Other"

                        val channelHeaders = HashMap(defaultHeaders)
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
                                isM3u8 = streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true) || streamUrl.contains("m3u8", ignoreCase = true),
                                quality = "Live",
                                headers = channelHeaders.toMap().takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                }
            }
            return channels
        }
    }
}
