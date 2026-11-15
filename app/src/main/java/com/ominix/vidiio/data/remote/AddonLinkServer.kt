package com.ominix.vidiio.data.remote

import android.util.Log
import com.ominix.vidiio.data.repository.LiveLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder

/**
 * A tiny web page on the home network for pasting addon and Live TV links from a computer, so
 * nobody has to type a long URL with a TV remote.
 *
 * It only listens while [serve] runs (the settings dialog keeps it open) and only accepts a link
 * together with [pin], which is shown on the TV, so another device on the network can't slip
 * addons in unseen.
 */
class AddonLinkServer(val pin: String) {

    /** The address to open on the computer, or null when the device has no LAN address. */
    fun address(port: Int = PORT): String? = lanIpv4()?.let { "http://$it:$port" }

    /**
     * Serves until the calling coroutine is cancelled. [onLinks] gets each accepted batch: addon
     * manifest URLs, and Live TV links (playlists or streams).
     */
    suspend fun serve(onLinks: suspend (addons: List<String>, live: List<LiveLink>, liveAddons: List<String>) -> Unit, port: Int = PORT) = withContext(Dispatchers.IO) {
        val server = try {
            ServerSocket(port)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't listen on $port", e); return@withContext
        }
        // Closing the socket is what unblocks accept() when the dialog closes.
        val closer = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { runCatching { server.close() } }
        try {
            while (isActive) {
                val client = try { server.accept() } catch (_: SocketException) { break }
                client.use { handle(it, onLinks) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Addon link server stopped", e)
        } finally {
            closer?.dispose()
            runCatching { server.close() }
        }
    }

    private suspend fun handle(socket: Socket, onLinks: suspend (List<String>, List<LiveLink>, List<String>) -> Unit) {
        socket.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        var contentLength = 0
        while (true) {
            val header = reader.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
            }
        }
        val method = requestLine.substringBefore(' ')
        val path = requestLine.substringAfter(' ').substringBefore(' ')

        val page = if (method == "POST" && path == "/add") {
            val body = CharArray(contentLength).let { buf ->
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            }
            val form = body.split('&').associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            when {
                form["pin"]?.trim() != pin -> page("Wrong code. Use the 4-digit code shown on the TV.", error = true)
                else -> {
                    val links = parseLinks(form["links"].orEmpty())
                    val live = parseLiveLinks(form["live"].orEmpty())
                    // Addon manifests pasted in the Live TV box are Live TV addons.
                    val liveAddons = parseLinks(form["live"].orEmpty())
                    if (links.isEmpty() && live.isEmpty() && liveAddons.isEmpty()) page("No links found. Addon links end in manifest.json; live links start with http.", error = true)
                    else {
                        onLinks(links, live, liveAddons)
                        val parts = listOfNotNull(
                            links.size.takeIf { it > 0 }?.let { "$it addon${if (it == 1) "" else "s"}" },
                            live.size.takeIf { it > 0 }?.let { "$it Live TV link${if (it == 1) "" else "s"}" },
                            liveAddons.size.takeIf { it > 0 }?.let { "$it Live TV addon${if (it == 1) "" else "s"}" },
                        )
                        page("Added ${parts.joinToString(" and ")}. You can add more or close this page.")
                    }
                }
            }
        } else page(null)

        val bytes = page.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        )
        out.write(bytes)
        out.flush()
    }

    private fun page(message: String?, error: Boolean = false): String {
        val note = message?.let {
            "<p class=\"${if (error) "err" else "ok"}\">${it.replace("&", "&amp;").replace("<", "&lt;")}</p>"
        }.orEmpty()
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Add links to Vidiio</title><style>
body{font-family:system-ui,sans-serif;background:#111;color:#eee;max-width:640px;margin:40px auto;padding:0 16px}
textarea,input{width:100%;box-sizing:border-box;background:#222;color:#eee;border:1px solid #444;border-radius:8px;padding:10px;font-size:15px}
textarea{height:160px}button{margin-top:14px;background:#e5242f;color:#fff;border:0;border-radius:8px;padding:12px 22px;font-size:16px;cursor:pointer}
label{display:block;margin:16px 0 6px}.ok{color:#7bd389}.err{color:#ff8080}</style></head><body>
<h1>Add links to Vidiio</h1>$note
<form method="post" action="/add">
<label for="links">Stremio addon links (one per line)</label>
<textarea id="links" name="links" placeholder="https://example.com/manifest.json"></textarea>
<label for="live">Live TV: addon links (manifest.json), channel playlists (M3U) or streams, one per line, optionally "Name, link"</label>
<textarea id="live" name="live" placeholder="Sports, https://example.com/playlist.m3u"></textarea>
<label for="pin">Code shown on the TV</label>
<input id="pin" name="pin" inputmode="numeric" maxlength="4" autocomplete="off">
<button type="submit">Add to Vidiio</button></form></body></html>"""
    }

    companion object {
        private const val TAG = "AddonLinkServer"
        const val PORT = 8765
        private const val MAX_BODY = 64 * 1024

        /** Manifest links from pasted text: one per line or space, stremio:// rewritten to https://. */
        fun parseLinks(text: String): List<String> =
            text.split(Regex("\\s+"))
                .map { it.trim().replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://") }
                .filter { (it.startsWith("https://") || it.startsWith("http://")) && it.substringBefore('?').endsWith("manifest.json") }
                .distinct()

        /**
         * Live TV links from pasted text, one per line as "link" or "Name, link". Addon manifests are
         * left to [parseLinks]. Without a name, the link's host names it.
         */
        fun parseLiveLinks(text: String): List<LiveLink> =
            text.lines().mapNotNull { raw ->
                val line = raw.trim()
                val at = Regex("https?://", RegexOption.IGNORE_CASE).find(line) ?: return@mapNotNull null
                val url = line.substring(at.range.first).trim().substringBefore(' ')
                if (url.substringBefore('?').endsWith("manifest.json")) return@mapNotNull null
                val name = line.substring(0, at.range.first).trim().trimEnd(',', '|', '-', ':').trim()
                    .ifBlank { runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url }
                LiveLink(name, url)
            }.distinctBy { it.url }

        fun newPin(): String = (1000..9999).random().toString()

        private fun lanIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
