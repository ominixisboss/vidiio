package com.ominix.vidiio.data.stremio

import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.stremio.StremioStream
import java.net.URLEncoder

/** Public BitTorrent trackers appended to bare-infoHash magnets so they actually resolve. */
private val PUBLIC_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.demonii.com:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://explodie.org:6969/announce",
    "udp://tracker.dler.org:6969/announce",
    "udp://opentracker.i2p.rocks:6969/announce",
    "http://tracker.openbittorrent.com:80/announce"
)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

fun buildMagnet(infoHash: String, displayName: String?): String {
    val sb = StringBuilder("magnet:?xt=urn:btih:").append(infoHash.trim())
    if (!displayName.isNullOrBlank()) sb.append("&dn=").append(enc(displayName))
    PUBLIC_TRACKERS.forEach { sb.append("&tr=").append(enc(it)) }
    return sb.toString()
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
    return String.format("%.1f %s", v, u[i])
}

private val SEEDERS_RE = Regex("""(?:👤|Seeders?:?|👥)\s*(\d+)""", RegexOption.IGNORE_CASE)
private val QUALITY_RE = Regex("""\b(2160p|4k|1080p|720p|480p|360p|CAM|HDCAM|WEB-?DL|WEBRip|BluRay|BRRip|HDRip)\b""", RegexOption.IGNORE_CASE)

/**
 * The "you must sign in" clip some token-gated addons (PenguPlay) return instead of
 * the film when the credential is missing, stale or revoked. It is not media and must never
 * reach the player, where it would play as a short video and look like a broken source.
 */
private fun isSignInPlaceholder(url: String?, title: String?): Boolean {
    if (url != null && url.substringBefore('?').endsWith("/signin.mp4", ignoreCase = true)) return true
    val label = title?.lowercase().orEmpty()
    return label.contains("must sign in") || label.contains("sign in at ")
}

/**
 * What to tell the viewer when a token-gated addon answered with nothing but that clip: its links
 * are missing for a reason the viewer can fix, so say so rather than showing fewer sources.
 */
const val SIGN_IN_CLIP_NOTE = "PenguPlay answered with its sign-in clip instead of a stream. " +
    "Sign in at pengu.uk and re-add your link in Settings."

/** True when this stream is an addon's sign-in clip rather than media. */
internal fun StremioStream.isSignInClip(): Boolean = isSignInPlaceholder(url, title)

/**
 * The player page inside a provider wrapper (`/watch?url=<page>`), or [url] itself.
 *
 * The wrapper is a bare iframe, and these players refuse to be framed, so the WebView shows a
 * black page for it. The inner page is the one that actually runs the player.
 */
private fun unwrapPlayerPage(url: String): String {
    val inner = url.substringAfter("?url=", "").substringBefore('&').takeIf { it.isNotBlank() } ?: return url
    val decoded = runCatching { java.net.URLDecoder.decode(inner, "UTF-8") }.getOrDefault(inner)
    return decoded.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: url
}

/**
 * Maps a Stremio addon stream to a [StreamSource] that both the player and the
 * downloader can consume:
 *  - `url` streams  → HTTP/HLS (with proxyHeaders carried through)
 *  - `infoHash` streams → a full magnet (trackers + name), with the addon's
 *    chosen file index/name preserved so the exact file is streamed/downloaded.
 */
@Suppress("UNCHECKED_CAST")
fun StremioStream.toStreamSource(): StreamSource? {
    // Filtered before anything else: a sign-in clip has a real URL and would otherwise be
    // probed, verified as playable media and offered as a source.
    if (isSignInClip()) return null

    val bh = behaviorHints ?: emptyMap()

    val proxyHeaders = ((bh["proxyHeaders"] as? Map<String, Any?>)?.get("request")) as? Map<String, Any?>
    val rawHeaders = proxyHeaders 
        ?: (bh["headers"] as? Map<String, Any?>) 
        ?: (bh["httpHeaders"] as? Map<String, Any?>) 
        ?: (bh["requestHeaders"] as? Map<String, Any?>)
        
    val extractedHeaders = rawHeaders
        ?.mapNotNull { (k, v) -> if (v != null) k to v.toString() else null }
        ?.toMap()
        .orEmpty()

    val headers = mutableMapOf<String, String>().apply {
        putAll(extractedHeaders)
        if (!containsKey("User-Agent") && !containsKey("user-agent")) {
            put("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
        }
    }.takeIf { it.isNotEmpty() }

    val filename = (bh["filename"] as? String)?.takeIf { it.isNotBlank() }
    val sizeBytes = (bh["videoSize"] as? Number)?.toLong()
    val label = (title ?: name ?: "").trim()

    val streamUrl = when {
        !url.isNullOrBlank() -> url!!
        // Some providers (NuvioSports' PPV feeds) never link the file: the stream is their web
        // player page, which the player already opens in a WebView. Dropped here, that feed was
        // missing from the list even though it was the only one carrying the event.
        !externalUrl.isNullOrBlank() -> unwrapPlayerPage(externalUrl!!)
        !infoHash.isNullOrBlank() -> buildMagnet(infoHash!!, filename ?: label.substringBefore('\n'))
        else -> return null
    }

    val streamTitle = title?.takeIf { it.isNotBlank() }
    val streamName = name?.takeIf { it.isNotBlank() }
    
    val displayName = buildString {
        if (!streamName.isNullOrBlank() && streamName != "Stremio" && !streamName.contains("Direct Stream", true)) {
            append(streamName).append(" · ")
        }
        append(streamTitle ?: streamName ?: filename ?: "Stremio")
    }.replace('\n', ' ').trim().ifBlank { "Stremio" }

    return StreamSource(
        url = streamUrl,
        sourceName = "Stremio",
        sourceId = "stremio",
        serverName = displayName,
        isM3u8 = streamUrl.substringBefore('?').endsWith(".m3u8", true),
        quality = QUALITY_RE.find(label)?.value?.uppercase() ?: QUALITY_RE.find(streamTitle.orEmpty())?.value?.uppercase(),
        size = sizeBytes?.let { humanSize(it) } ?: label.let {
            Regex("""\b\d+(?:\.\d+)?\s?[GM]B\b""", RegexOption.IGNORE_CASE).find(it)?.value
        },
        seeders = SEEDERS_RE.find(label)?.groupValues?.get(1)?.toIntOrNull(),
        headers = headers,
        fileIndex = fileIdx,
        fileName = filename
    )
}
