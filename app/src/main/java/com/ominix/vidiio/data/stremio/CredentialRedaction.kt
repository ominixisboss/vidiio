package com.ominix.vidiio.data.stremio

import java.net.URLDecoder

/**
 * Hides addon credentials in text bound for logcat.
 *
 * Several addons carry their whole configuration in the transport URL path: PenguPlay's
 * `auth_token` and SubSource's account key both sit in one opaque segment between the host and
 * `manifest.json`. Logging a request verbatim therefore writes a live credential into logcat,
 * where `adb logcat` - and any other app allowed to read logs - can copy it. Replacing only that
 * segment keeps the line useful for debugging: `stream/tv/<id>.json` is normally what matters.
 */
fun redactCredentials(text: String): String = LOG_URL_RE.replace(text) { redactUrl(it.value) }

private val LOG_URL_RE = Regex("""https?://\S+""")

/** Base64 of an addon config or key; ordinary path segments carry dots, dashes or underscores. */
private val OPAQUE_SEGMENT_RE = Regex("""[A-Za-z0-9+/=]{32,}""")

private const val REDACTED = "<redacted>"

private fun redactUrl(url: String): String {
    val hostStart = url.indexOf("://") + 3
    if (hostStart < 3) return url
    val pathStart = url.indexOf('/', hostStart)
    if (pathStart < 0) return url
    val queryStart = url.indexOf('?', pathStart).takeIf { it >= 0 } ?: url.length
    val path = url.substring(pathStart, queryStart)
    val redactedPath = path.split('/').joinToString("/") { if (isCredentialSegment(it)) REDACTED else it }
    return url.substring(0, pathStart) + redactedPath + url.substring(queryStart)
}

/** An addon's encoded config (URL-encoded JSON) or an opaque key: neither belongs in a log. */
private fun isCredentialSegment(segment: String): Boolean {
    if (segment.isEmpty()) return false
    val decoded = runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
    if (decoded.startsWith("{") && decoded.contains(':')) return true
    return OPAQUE_SEGMENT_RE.matches(segment)
}
