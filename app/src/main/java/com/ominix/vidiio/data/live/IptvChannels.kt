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
        parse(text)
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
        val defaultHeaders = mapOf("User-Agent" to "VLC/3.0.18 LibVLC/3.0.18")
        val single = listOf(LiveChannel(name, null, name, StreamSource(
            serverName = name, url = url, sourceName = "Live TV", sourceId = "iptv",
            isM3u8 = path.endsWith(".m3u8") || path.contains("m3u8") || path.contains("playlist"), quality = "Live",
            headers = defaultHeaders,
        )))
        // Obvious single streams need no look inside.
        if (DIRECT_EXTENSIONS.any { path.endsWith(it) }) return@withContext single
        val response = runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()).execute()
        }.getOrNull() ?: return@withContext single

        val head = response.use { resp ->
            if (!resp.isSuccessful) return@withContext single
            val src = resp.body.source()
            src.request(MAX_PLAYLIST_BYTES)
            src.buffer.clone().readUtf8()
        }
        val isChannelList = head.contains("#EXTINF") || head.contains("#EXTM3U")
        if (!isChannelList) single
        else parse(head, skipGeoBlocked = false).map { it.copy(category = name) }.ifEmpty { single }
    }

    companion object {
        private val DIRECT_EXTENSIONS = listOf(".mpd", ".mp4", ".mkv", ".ts", ".webm", ".mov")
        private const val MAX_PLAYLIST_BYTES = 4L * 1024 * 1024
        private val CATEGORY_ORDER = listOf(
            "News", "Sports", "Movies", "Entertainment", "Series", "Kids", "Animation", "Comedy",
            "Documentary", "Music", "Classic", "Lifestyle", "Cooking", "Outdoor", "Travel",
            "Business", "Weather", "Education", "Science", "Shop", "General", "Religious",
            "Legislative", "Other",
        )
        private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
        private val NAME_NOISE = Regex("""\s*(\(\d{3,4}p\)|\[[^]]*])""")

        /** Parses an M3U playlist, honouring the #EXTVLCOPT user-agent / referrer lines some streams need. */
        fun parse(m3u: String, skipGeoBlocked: Boolean = true): List<LiveChannel> {
            val channels = ArrayList<LiveChannel>()
            var info: String? = null
            val headers = HashMap<String, String>()
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
                    line.startsWith("http") && info != null -> {
                        val meta = info
                        info = null
                        if (skipGeoBlocked && meta.contains("Geo-blocked")) continue
                        val attrs = ATTR.findAll(meta.substringBefore(",")).associate { it.groupValues[1] to it.groupValues[2] }
                        val name = meta.substringAfterLast(',').replace(NAME_NOISE, "").trim().ifBlank { continue }
                        val category = attrs["group-title"].orEmpty().split(';').first().trim()
                            .takeUnless { it.isBlank() || it == "Undefined" } ?: "Other"

                        val channelHeaders = HashMap(headers)
                        if (!channelHeaders.containsKey("User-Agent")) {
                            channelHeaders["User-Agent"] = "VLC/3.0.18 LibVLC/3.0.18"
                        }

                        channels += LiveChannel(
                            name = name,
                            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                            category = category,
                            source = StreamSource(
                                serverName = name,
                                url = line,
                                sourceName = "Live TV",
                                sourceId = "iptv",
                                isM3u8 = line.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
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
