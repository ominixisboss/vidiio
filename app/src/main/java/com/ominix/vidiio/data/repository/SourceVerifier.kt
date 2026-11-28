package com.ominix.vidiio.data.repository

import android.os.SystemClock
import android.util.Log
import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where a source stands once we have looked at it. Order is the order they sort in. */
enum class SourceStatus {
    /** Confirmed to answer with media. */
    WORKING,

    /** A probe is still running. */
    CHECKING,

    /** Cannot be checked from here (web embeds, torrents with no seeder count). */
    UNKNOWN,

    /** Probed and failed: dead link, not media, refused, or timed out. */
    DEAD,
}

data class SourceHealth(
    val status: SourceStatus,
    /** Time to first byte for a direct link, when known. */
    val latencyMs: Long? = null,
    /** Short human reason, shown next to the badge ("404", "Not a video", "12 seeders"). */
    val note: String? = null,
) {
    companion object {
        val Checking = SourceHealth(SourceStatus.CHECKING)
    }
}

/**
 * Finds out which of the scraped sources are actually worth offering.
 *
 * Scrapers return whatever a page or addon *claimed*: plenty of those links are dead, geo
 * blocked, or an HTML error page behind a video-looking URL, and the old list showed them all
 * in seeder order. A source is judged by what it can tell us cheaply:
 *  - direct links: ask for the first couple of KB and check what comes back is media
 *    (for HLS, that it really starts with `#EXTM3U`) - so a 200 that is an HTML page does
 *    not count;
 *  - torrents: no network probe is possible without joining the swarm, so the seeder count
 *    the scraper reported decides (zero seeders = dead, unknown = unverified);
 *  - web embeds: they only load inside a WebView, so they are left unverified.
 */
class SourceVerifier(client: OkHttpClient) {

    private val probeClient: OkHttpClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // A page can return dozens of sources; don't open dozens of sockets at once.
    private val gate = Semaphore(MAX_CONCURRENT_PROBES)

    suspend fun check(source: StreamSource): SourceHealth {
        val url = source.url
        return when {
            isTorrent(url) -> judgeTorrent(source.seeders)
            isEmbed(url) -> SourceHealth(SourceStatus.UNKNOWN, note = "Web player")
            !url.startsWith("http", ignoreCase = true) -> SourceHealth(SourceStatus.UNKNOWN)
            // The body is read here, not just the headers. Called from viewModelScope this ran
            // on the main thread, and Android's StrictMode threw NetworkOnMainThreadException
            // as soon as the probe had to wait for bytes that were not already buffered - which
            // is exactly what happens with a slow-but-good link (PixelDrain), while instant
            // error pages sailed through. Swallowed as "Unverified", it sorted those sources
            // under every torrent.
            else -> withContext(Dispatchers.IO) {
                gate.withPermit {
                    val first = probe(source)
                    if (!shouldRetry(first)) return@withPermit first
                    // A slow first byte is not a verdict on the link: a host that takes a few
                    // seconds on a cold connection often answers at once the second time.
                    delay(RETRY_DELAY_MS)
                    probe(source)
                }
            }
        }
    }

    private suspend fun probe(source: StreamSource): SourceHealth {
        val request = Request.Builder()
            .url(source.url)
            .header("Range", "bytes=0-$PROBE_BYTES")
            .apply { source.headers?.forEach { (name, value) -> header(name, value) } }
            .build()

        val started = SystemClock.elapsedRealtime()
        return try {
            probeClient.newCall(request).await().use { response ->
                val latency = SystemClock.elapsedRealtime() - started
                val prefix = readPrefix(response)
                val health = judgeResponse(
                    code = response.code,
                    contentType = response.header("Content-Type"),
                    bodyPrefix = prefix,
                    looksLikeHls = source.isM3u8 || source.url.contains(".m3u8", ignoreCase = true),
                )
                if (health.status == SourceStatus.WORKING) health.copy(latencyMs = latency) else health
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // A connect timeout means the host is not answering at all. A read timeout on a 4K
            // file is usually just a slow first byte, which says nothing about the link, so it is
            // left unverified instead of being hidden as dead.
            val connectTimeout = e is java.net.SocketTimeoutException &&
                e.message?.contains("connect", ignoreCase = true) == true
            when {
                e !is java.net.SocketTimeoutException -> SourceHealth(SourceStatus.DEAD, note = "Unreachable")
                connectTimeout -> SourceHealth(SourceStatus.DEAD, note = "Unreachable")
                else -> SourceHealth(SourceStatus.UNKNOWN, note = "Slow")
            }
        } catch (e: Exception) {
            // Host only: some addon URLs carry a credential in their path. Previously this
            // failure was silent, so a bug in the probe looked like "the link may not work".
            Log.w(
                "SourceVerifier",
                "probe of ${source.url.substringBefore('?').substringAfter("://").substringBefore('/')} failed: ${e.javaClass.simpleName}",
            )
            SourceHealth(SourceStatus.UNKNOWN, note = "Probe failed")
        }
    }

    private fun readPrefix(response: Response): String = runCatching {
        val body = response.body ?: return@runCatching ""
        val source = body.source()
        source.request(PROBE_BYTES.toLong())
        val size = minOf(source.buffer.size, PROBE_BYTES.toLong())
        source.buffer.peek().readUtf8(size)
    }.getOrDefault("")

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = continuation.resume(response)
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        private const val MAX_CONCURRENT_PROBES = 6
        private const val PROBE_BYTES = 2047
        private const val RETRY_DELAY_MS = 600L

        /**
         * True when a probe result says nothing definite about the link, so it is worth asking
         * again: the read timed out ("Slow"), the host was busy, or the call failed in a way
         * that is not an HTTP verdict (no note). A 4xx/5xx or a page that is not media is a
         * real answer and is not retried.
         */
        fun shouldRetry(health: SourceHealth): Boolean =
            health.status == SourceStatus.UNKNOWN && health.note !in DEFINITE_REFUSALS

        /** Refusals that are stable answers about the file rather than a passing problem. */
        private val DEFINITE_REFUSALS = setOf("HTTP 401", "HTTP 403", "Web player")

        fun isTorrent(url: String): Boolean =
            url.startsWith("magnet:", ignoreCase = true) ||
                url.substringBefore('?').endsWith(".torrent", ignoreCase = true)

        /** Same test the player uses to decide a source belongs in a WebView. */
        fun isEmbed(url: String): Boolean = url.contains("embed") || url.contains("vidsrc")

        fun judgeTorrent(seeders: Int?): SourceHealth = when {
            seeders == null -> SourceHealth(SourceStatus.UNKNOWN, note = "Torrent")
            seeders <= 0 -> SourceHealth(SourceStatus.DEAD, note = "No seeders")
            else -> SourceHealth(SourceStatus.WORKING, note = "$seeders seeders")
        }

        /**
         * Pure decision on what a probe saw. Kept free of OkHttp types so it can be unit
         * tested with plain values.
         */
        fun judgeResponse(
            code: Int,
            contentType: String?,
            bodyPrefix: String,
            looksLikeHls: Boolean,
        ): SourceHealth {
            when (code) {
                in 200..206 -> Unit
                // Rate limited: says nothing about whether the link is good.
                408, 429 -> return SourceHealth(SourceStatus.UNKNOWN, note = "Busy")
                // Refused, not missing. Token-gated CDNs (PenguPlay) and hotlink protection
                // answer 403 for a probe whose headers they dislike while the same URL plays
                // in the player, so this is not evidence that the file is gone.
                401, 403 -> return SourceHealth(SourceStatus.UNKNOWN, note = "HTTP $code")
                else -> return SourceHealth(SourceStatus.DEAD, note = "HTTP $code")
            }

            val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val isPlaylistType = "mpegurl" in type
            val trimmed = bodyPrefix.trimStart('\uFEFF', ' ', '\n', '\r', '\t')

            if (looksLikeHls || isPlaylistType) {
                return if (trimmed.startsWith("#EXTM3U")) {
                    SourceHealth(SourceStatus.WORKING)
                } else {
                    SourceHealth(SourceStatus.DEAD, note = "Bad playlist")
                }
            }

            val isPage = type.startsWith("text/") || type.contains("html") || type.contains("json") ||
                type.contains("xml") || trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)
            if (isPage) return SourceHealth(SourceStatus.DEAD, note = "Not a video")

            return SourceHealth(SourceStatus.WORKING)
        }
    }
}
