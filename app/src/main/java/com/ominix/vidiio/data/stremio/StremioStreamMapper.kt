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
 * Maps a Stremio addon stream to a [StreamSource] that both the player and the
 * downloader can consume:
 *  - `url` streams  → HTTP/HLS (with proxyHeaders carried through)
 *  - `infoHash` streams → a full magnet (trackers + name), with the addon's
 *    chosen file index/name preserved so the exact file is streamed/downloaded.
 */
@Suppress("UNCHECKED_CAST")
fun StremioStream.toStreamSource(addonName: String? = null): StreamSource? {
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

    val fullText = "$label $displayName ${streamTitle.orEmpty()} ${filename.orEmpty()}".uppercase()
    val is4k = fullText.contains("4K") || fullText.contains("2160P") || fullText.contains("UHD")
    val isHdr = fullText.contains("HDR") || fullText.contains("HDR10")
    val isDv = fullText.contains("DV") || fullText.contains("DOLBY VISION") || fullText.contains("DOVI")
    val isAtmos = fullText.contains("ATMOS") || fullText.contains("7.1") || fullText.contains("TRUEHD")
    val isRemux = fullText.contains("REMUX")
    val isCam = fullText.contains("CAM") || fullText.contains("HDCAM") || fullText.contains("TELESYNC") || fullText.contains("TS-RIP")
    val isDebrid = fullText.contains("[RD+]") || fullText.contains("[AD+]") || fullText.contains("[PM+]") || fullText.contains("⚡")

    val providerName = addonName ?: streamName ?: "Stremio"

    return StreamSource(
        url = streamUrl,
        sourceName = providerName,
        sourceId = providerName.lowercase().replace(" ", "_"),
        serverName = displayName,
        isM3u8 = streamUrl.substringBefore('?').endsWith(".m3u8", true),
        quality = QUALITY_RE.find(label)?.value?.uppercase() ?: QUALITY_RE.find(streamTitle.orEmpty())?.value?.uppercase(),
        size = sizeBytes?.let { humanSize(it) } ?: label.let {
            Regex("""\b\d+(?:\.\d+)?\s?[GM]B\b""", RegexOption.IGNORE_CASE).find(it)?.value
        },
        seeders = SEEDERS_RE.find(label)?.groupValues?.get(1)?.toIntOrNull(),
        headers = headers,
        fileIndex = fileIdx,
        fileName = filename,
        is4K = is4k,
        isHdr = isHdr,
        isDolbyVision = isDv,
        isDolbyAtmos = isAtmos,
        isRemux = isRemux,
        isCam = isCam,
        isDebridCached = isDebrid
    )
}
