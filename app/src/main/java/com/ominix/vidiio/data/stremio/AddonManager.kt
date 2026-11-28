package com.ominix.vidiio.data.stremio

import android.util.Log
import com.ominix.vidiio.data.api.StremioService
import com.ominix.vidiio.data.model.stremio.Manifest
import com.ominix.vidiio.data.model.stremio.StremioMeta
import com.ominix.vidiio.data.model.stremio.StremioMetaDetail
import com.ominix.vidiio.data.model.stremio.StremioStream
import com.ominix.vidiio.data.model.subtitles.SubtitleTrack
import com.ominix.vidiio.data.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One catalogue of a live addon and its events (possibly none right now). */
data class AddonLiveCatalog(val addonName: String, val catalogName: String, val events: List<LiveAddonEvent>)

/** Live addon catalogues, and the Live TV addon URLs that could not be reached. */
data class AddonLiveResult(val catalogs: List<AddonLiveCatalog>, val unreachable: List<String>)

/** One live event or channel from an installed addon's catalogue, and where to ask for its streams. */
data class LiveAddonEvent(val addonName: String, val baseUrl: String, val type: String, val meta: StremioMeta)

/** url + resolved manifest for one installed Stremio addon. */
data class InstalledAddon(val url: String, val manifest: Manifest) {
    val baseUrl: String get() = url.substringBeforeLast('/') + "/"
}

/**
 * What the installed addons answered for one stream request: their streams, plus whether a
 * PenguPlay addon answered with nothing but its sign-in clip, so its links were withheld
 * rather than simply absent.
 */
data class AddonStreams(val streams: List<StremioStream>, val penguSignInOnly: Boolean = false)

class AddonManager(
    private val stremioService: StremioService,
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val manifestCache = mutableMapOf<String, Manifest>()

    /** User-added addons plus addons bundled permanently with the app. */
    private suspend fun allAddonUrls(): List<String> {
        val user = settingsRepository.stremioAddonsFlow.first()
        return (BUILTIN_ADDON_URLS + user).distinct()
            .sortedByDescending { it.contains("torrentio") }
    }

    suspend fun getInstalledAddons(): List<InstalledAddon> = loadAddons(allAddonUrls())

    /** Manifests for [urls]; an addon that fails or is slow is left out. */
    private suspend fun loadAddons(urls: List<String>): List<InstalledAddon> = coroutineScope {
        urls.map { url ->
            async {
                val m = manifestCache[url] ?: try {
                    // One slow addon must not hold up the rest (or the Home screen): give each a few seconds.
                    kotlinx.coroutines.withTimeoutOrNull(MANIFEST_TIMEOUT_MS) {
                        stremioService.getManifest(url).also { manifestCache[url] = it }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("AddonManager", "Error fetching manifest from ${redactCredentials(url)}", e); null
                }
                m?.let { InstalledAddon(url, it) }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Streams offered by every installed addon that declares the `stream` resource.
     *
     * Each addon gets its own deadline. PenguPlay answers one request by fanning out to ~27
     * providers and is routinely slower than a plain scraper addon; when the caller held one
     * shared deadline over the whole batch, that slow answer cancelled every other addon's
     * links with it - the addon that needed the time was the one that lost everything.
     */
    suspend fun getStreams(type: String, id: String): AddonStreams = coroutineScope {
        // Select Sources can switch an addon off without uninstalling it.
        val disabledSources = settingsRepository.sourcesFlow.first()
        val streamAddons = getInstalledAddons().filter { addon ->
            addon.manifest.hasResource("stream") && !disabledSources.contains("disabled_${addon.url}")
        }
        val answers = streamAddons.map { addon ->
            async {
                val offered = try {
                    withTimeoutOrNull(streamBudgetMs(addon)) {
                        stremioService.getStreams("${addon.baseUrl}stream/$type/$id.json").streams
                    }.orEmpty()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("AddonManager", "Error fetching streams from ${addon.manifest.name}", e)
                    emptyList()
                }
                addon to offered
            }
        }.awaitAll()
        AddonStreams(
            streams = answers.flatMap { it.second },
            // An answer made only of sign-in clips means this addon's links were withheld, not absent.
            penguSignInOnly = answers.any { (addon, offered) ->
                addon.baseUrl.contains("pengu.uk", ignoreCase = true) &&
                    offered.isNotEmpty() && offered.all { it.isSignInClip() }
            },
        )
    }

    /** How long one addon may take to answer a stream request. */
    private fun streamBudgetMs(addon: InstalledAddon): Long =
        if (addon.baseUrl.contains("pengu.uk", ignoreCase = true)) PENGU_STREAM_TIMEOUT_MS
        else ADDON_STREAM_TIMEOUT_MS

    /**
     * Subtitles offered by every installed addon that declares the `subtitles` resource.
     *
     * Each track is tagged with the addon's own name, so the player can show which
     * provider a subtitle came from rather than presenting one undifferentiated list.
     * Addons that fail or time out are skipped - a dead addon must not cost the user the
     * subtitles the others returned.
     */
    suspend fun getSubtitles(type: String, id: String): List<SubtitleTrack> = coroutineScope {
        val subtitleAddons = getInstalledAddons().filter { it.manifest.hasResource("subtitles") }
        subtitleAddons.map { addon ->
            async {
                try {
                    stremioService.getSubtitles("${addon.baseUrl}subtitles/$type/$id.json")
                        .subtitles
                        .orEmpty()
                        .mapNotNull { sub ->
                            val url = sub.url ?: return@mapNotNull null
                            val subId = sub.id ?: url.substringAfterLast('/')
                            val trackLabel = when {
                                sub.id != null && sub.id.length > 3 && !sub.id.all { it.isDigit() } -> sub.id
                                else -> addon.manifest.name
                            }
                            SubtitleTrack(
                                id = "stremio:${addon.manifest.id}:${subId}",
                                url = url,
                                language = sub.lang.orEmpty(),
                                label = trackLabel,
                                source = addon.manifest.name,
                            )
                        }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("AddonManager", "Error fetching subtitles from ${addon.manifest.name}", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
            // Two installs of one addon (same manifest id, different config) return the same tracks.
            .distinctBy { it.url }
    }

    /** Full metadata (with episode list) for one item from whichever addon serves it. */
    suspend fun getMeta(type: String, id: String): StremioMetaDetail? = coroutineScope {
        val metaAddons = getInstalledAddons().filter { it.manifest.hasResource("meta") }
        metaAddons.map { addon ->
            async {
                try {
                    stremioService.getMeta("${addon.baseUrl}meta/$type/$id.json").meta
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "AddonManager.getMeta() failed", e)
                    null
                }
            }
        }.awaitAll().firstOrNull { it != null }
    }

    /** Titles from one catalogue of the built-in Streaming Catalogs addon (Netflix, HBO Max, ...). Empty on failure. */
    suspend fun streamingCatalog(type: String, serviceId: String): List<StremioMeta> = try {
        kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
            stremioService.getCatalog("$STREAMING_CATALOGS_BASE/catalog/$type/$serviceId.json").metas
        } ?: emptyList()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("AddonManager", "Streaming catalogue $serviceId failed", e)
        emptyList()
    }

    suspend fun installAddon(url: String) {
        settingsRepository.addStremioAddon(url)
    }

    suspend fun uninstallAddon(url: String) {
        settingsRepository.removeStremioAddon(url)
    }

    /** Fetches the Stremio community and official addon directory entries. */
    suspend fun getAddonStoreEntries(): List<com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://stremio-addons.net/api/manifest.json",
            "https://v3-cinemeta.strem.io/official-addons.json"
        )
        val entries = mutableListOf<com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry>()

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                val response = client.newCall(request).execute()
                val bodyText = response.use { if (it.isSuccessful) it.body.string() else null }
                if (bodyText.isNullOrBlank()) continue

                val jsonArray = runCatching { org.json.JSONArray(bodyText) }.getOrNull()
                    ?: runCatching {
                        val obj = org.json.JSONObject(bodyText)
                        obj.optJSONArray("addons") ?: obj.optJSONArray("entries") ?: obj.optJSONArray("manifests")
                    }.getOrNull() ?: continue

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val manifestObj = obj.optJSONObject("manifest") ?: obj

                    val id = manifestObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = manifestObj.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val description = manifestObj.optString("description").takeIf { it.isNotBlank() }
                    val version = manifestObj.optString("version").takeIf { it.isNotBlank() }
                    val logo = manifestObj.optString("logo").takeIf { it.isNotBlank() }
                        ?: manifestObj.optString("icon").takeIf { it.isNotBlank() }

                    val typesArray = manifestObj.optJSONArray("types")
                    val types = (0 until (typesArray?.length() ?: 0)).mapNotNull { idx -> typesArray?.optString(idx) }

                    val resourcesArray = manifestObj.optJSONArray("resources")
                    val resourcesList = (0 until (resourcesArray?.length() ?: 0)).mapNotNull { idx ->
                        resourcesArray?.optString(idx) ?: resourcesArray?.optJSONObject(idx)?.optString("name")
                    }

                    val transportUrl = obj.optString("transportUrl").takeIf { it.isNotBlank() }
                        ?: "https://$id.strem.fun/manifest.json"

                    val manifest = Manifest(
                        id = id,
                        name = name,
                        description = description,
                        version = version,
                        types = types,
                        resources = resourcesList,
                        logo = logo
                    )
                    entries.add(com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry(manifest = manifest, transportUrl = transportUrl))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("AddonManager", "Error fetching addon directory from $url", e)
            }
        }

        // Add built-in popular fallback directory entries if empty or missing
        val fallbacks = listOf(
            com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry(
                manifest = Manifest(
                    id = "org.stremio.torrentio",
                    name = "Torrentio",
                    description = "Provides torrent streams from multiple providers including YTS, EZTV, RARBG, 1337x, TorrentGalaxy and RealDebrid.",
                    version = "1.0.8",
                    types = listOf("movie", "series"),
                    resources = listOf("stream"),
                    logo = "https://torrentio.strem.fun/logo.png"
                ),
                transportUrl = "https://torrentio.strem.fun/manifest.json"
            ),
            com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry(
                manifest = Manifest(
                    id = "org.stremio.knightcrawler",
                    name = "KnightCrawler",
                    description = "Scrapes streams from TorrentGalaxy, 1337x, LimeTorrents, EZTV and Debrid services.",
                    version = "1.0.0",
                    types = listOf("movie", "series"),
                    resources = listOf("stream"),
                    logo = "https://knightcrawler.elfhosted.com/logo.png"
                ),
                transportUrl = "https://knightcrawler.elfhosted.com/manifest.json"
            ),
            com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry(
                manifest = Manifest(
                    id = "com.stremio.cyberflix",
                    name = "CyberFlix Catalog",
                    description = "Adds extensive movie and TV series catalogs from Netflix, Disney+, Apple TV+, HBO Max, Hulu, Paramount+ and Amazon Prime.",
                    version = "1.4.1",
                    types = listOf("movie", "series"),
                    resources = listOf("catalog"),
                    logo = "https://cyberflix.elfhosted.com/logo.png"
                ),
                transportUrl = "https://cyberflix.elfhosted.com/manifest.json"
            ),
            com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry(
                manifest = Manifest(
                    id = "org.stremio.opensubtitlesv3",
                    name = "OpenSubtitles v3",
                    description = "Official subtitles addon for Stremio using OpenSubtitles.com API with automatic subtitle language matching.",
                    version = "1.0.0",
                    types = listOf("movie", "series", "anime"),
                    resources = listOf("subtitles"),
                    logo = "https://opensubtitles-v3.strem.io/logo.png"
                ),
                transportUrl = "https://opensubtitles-v3.strem.io/manifest.json"
            ),
            com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry(
                manifest = Manifest(
                    id = "com.stremio.animekitsu",
                    name = "Anime Kitsu",
                    description = "Anime catalog addon powered by Kitsu.io containing popular and seasonal anime series.",
                    version = "1.0.0",
                    types = listOf("anime"),
                    resources = listOf("catalog"),
                    logo = "https://anime-kitsu.strem.fun/logo.png"
                ),
                transportUrl = "https://anime-kitsu.strem.fun/manifest.json"
            )
        )

        (entries + fallbacks).distinctBy { it.transportUrl ?: it.manifest.id }
    }

    suspend fun getCatalogs(): List<Pair<String, List<StremioMeta>>> = coroutineScope {
        getInstalledAddons()
            .filter { it.url !in BUILTIN_ADDON_URLS && !it.manifest.catalogs.isNullOrEmpty() }
            .flatMap { addon ->
                addon.manifest.catalogs!!.map { catalog ->
                    async {
                        try {
                            val url = "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}.json"
                            (catalog.name ?: addon.manifest.name) to (kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) { stremioService.getCatalog(url).metas } ?: emptyList())
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e("AddonManager", "Error fetching catalog ${catalog.name}", e); null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
    }

    /** Catalogs from the bundled anime addons only (shown inside the Anime area of Home). */
    suspend fun getAnimeCatalogs(): List<Pair<String, List<StremioMeta>>> = coroutineScope {
        getInstalledAddons()
            .filter { (it.url.contains("onepace", ignoreCase = true) || it.manifest.name.contains("one pace", ignoreCase = true) || it.manifest.types.orEmpty().any { t -> t.lowercase() == "anime" }) && !it.manifest.catalogs.isNullOrEmpty() }
            .flatMap { addon ->
                addon.manifest.catalogs!!.filter { c -> c.type.lowercase() == "anime" || addon.url.contains("onepace", ignoreCase = true) || addon.manifest.name.contains("one pace", ignoreCase = true) }.map { catalog ->
                    async {
                        runCatching {
                            val url = "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}.json"
                            (catalog.name ?: addon.manifest.name) to (kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) { stremioService.getCatalog(url).metas } ?: emptyList())
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull().filter { it.second.isNotEmpty() }
    }

    private suspend fun penguBaseUrl(): String {
        return getInstalledAddons().firstOrNull { it.baseUrl.contains("pengu.uk", ignoreCase = true) }?.baseUrl ?: PENGU_LIVE_BASE
    }

    /**
     * PenguPlay's live sports and TV catalogues (Live Now, Soccer, Channels, ...), with the events in
     * each. Only non-empty catalogues are returned; between events most of them are empty.
     */
    suspend fun penguLiveCatalogs(): List<Pair<String, List<StremioMeta>>> = coroutineScope {
        val base = penguBaseUrl()
        val manifest = try {
            kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) { stremioService.getManifest("${base}manifest.json") }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("AddonManager", "PenguPlay live manifest failed", e); null
        } ?: return@coroutineScope emptyList()
        manifest.catalogs.orEmpty().map { catalog ->
            async {
                runCatching {
                    val metas = kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                        stremioService.getCatalog("${base}catalog/${catalog.type}/${catalog.id}.json").metas
                    }.orEmpty()
                    (catalog.name ?: catalog.id).removeSuffix(" - TV") to metas
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.second.isNotEmpty() }
    }

    /**
     * Every live catalogue with its events: all catalogues of the Live TV addons, plus the non-movie
     * catalogues of the regular addons. Empty catalogues are kept, so an addon with nothing on right now
     * still shows; [AddonLiveResult.unreachable] lists Live TV addons whose manifest could not be loaded.
     */
    suspend fun addonLiveCatalogs(): AddonLiveResult = coroutineScope {
        val sources = liveSources()
        val catalogs = sources.catalogs.map { (addon, catalog) ->
                async {
                    val extraPath = catalog.extra.orEmpty()
                        .filter { it.isRequired && !it.options.isNullOrEmpty() }
                        .joinToString("") { "/${it.name}=${it.options!!.first()}" }
                    val url = "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}$extraPath.json"
                    val metas = runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                            stremioService.getCatalog(url).metas
                        }.orEmpty()
                    }.getOrDefault(emptyList())
                    AddonLiveCatalog(
                        addonName = addon.manifest.name,
                        catalogName = catalog.name?.takeIf { it.isNotBlank() } ?: catalog.id,
                        events = metas.map { LiveAddonEvent(addon.manifest.name, addon.baseUrl, catalog.type, it) },
                    )
                }
            }.awaitAll()
        AddonLiveResult(catalogs, sources.unreachable)
    }

    /**
     * Events in the installed addons' live catalogues whose name contains [query] (a team name, say).
     * Uses an addon's search where it has one, else filters the catalogue's own list.
     */
    suspend fun addonLiveSearch(query: String): List<LiveAddonEvent> = coroutineScope {
        val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val needle = query.lowercase()
        liveSources().catalogs.map { (addon, catalog) ->
            async {
                runCatching {
                    val searchable = catalog.extra.orEmpty().any { it.name == "search" }
                    val requiredOther = catalog.extra.orEmpty().any { it.isRequired && it.name != "search" }
                    if (requiredOther) return@runCatching emptyList()
                    val url = if (searchable) "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}/search=$q.json"
                    else "${addon.baseUrl}catalog/${catalog.type}/${catalog.id}.json"
                    kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) { stremioService.getCatalog(url).metas }.orEmpty()
                        .filter { it.name.lowercase().contains(needle) }
                        .map { LiveAddonEvent(addon.manifest.name, addon.baseUrl, catalog.type, it) }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    /** The streams one addon offers for one of its live events. */
    suspend fun addonLiveStreams(event: LiveAddonEvent): List<StremioStream> =
        kotlinx.coroutines.withTimeoutOrNull(liveStreamBudgetMs(event)) {
            val id = java.net.URLEncoder.encode(event.meta.id, "UTF-8").replace("+", "%20")
            stremioService.getStreams("${event.baseUrl}stream/${event.type}/$id.json").streams
        }.orEmpty()

    /**
     * How long one addon may take to answer for a live event. NuvioSports fans out to many
     * providers per event and a 15s budget cut real feeds off mid-answer (measured 15.1s).
     */
    private fun liveStreamBudgetMs(event: LiveAddonEvent): Long =
        if (event.baseUrl.contains("nuviosports", ignoreCase = true)) NUVIO_LIVE_TIMEOUT_MS
        else STREAM_TIMEOUT_MS

    private class LiveSources(
        val catalogs: List<Pair<InstalledAddon, com.ominix.vidiio.data.model.stremio.Catalog>>,
        val unreachable: List<String>,
    )

    /**
     * Where Live TV reads from: every catalogue of the addons added for Live TV, and the catalogues of the
     * regular addons that are not movies, series or anime (sport, sports, tv, events, ...).
     */
    private suspend fun liveSources(): LiveSources = coroutineScope {
        // Bundled live addons are always present, whatever the viewer's own list says.
        val liveUrls = (settingsRepository.liveAddonsFlow.first().toList() + BUILTIN_LIVE_ADDON_URLS).distinct()
        val live = async { loadAddons(liveUrls) }
        val regular = async {
            getInstalledAddons().filter { it.url !in BUILTIN_ADDON_URLS && it.url !in liveUrls && !it.baseUrl.contains("pengu.uk", ignoreCase = true) }
        }
        val liveAddons = live.await()
        val catalogs = liveAddons.flatMap { addon -> addon.manifest.catalogs.orEmpty().map { addon to it } } +
            regular.await().filter { it.manifest.hasResource("stream") }
                .flatMap { addon -> addon.manifest.catalogs.orEmpty().filter { it.type.lowercase() !in VOD_TYPES }.map { addon to it } }
        val loaded = liveAddons.map { it.url }.toSet()
        LiveSources(catalogs, liveUrls.filter { it !in loaded })
    }

    /** PenguPlay live events in [catalogId] (e.g. pp-live-basketball) matching [query]. Empty on failure. */
    suspend fun penguLiveSearch(catalogId: String, query: String): List<StremioMeta> = try {
        val base = penguBaseUrl()
        val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        kotlinx.coroutines.withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
            stremioService.getCatalog("${base}catalog/tv/$catalogId/search=$q.json").metas
        }.orEmpty()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("AddonManager", "PenguPlay live search failed", e)
        emptyList()
    }

    /**
     * The playable streams for one PenguPlay live event or channel. Empty when it does not answer
     * in time: the sheet waiting on this must never spin forever.
     */
    suspend fun penguLiveStreams(type: String, id: String): List<StremioStream> =
        withTimeoutOrNull(PENGU_LIVE_TIMEOUT_MS) {
            stremioService.getStreams("${penguBaseUrl()}stream/$type/${java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")}.json").streams
        }.also { if (it == null) Log.w("AddonManager", "PenguPlay live streams timed out after ${PENGU_LIVE_TIMEOUT_MS}ms") }
            .orEmpty()

    private fun Manifest.hasResource(name: String): Boolean {
        val res = resources ?: return false
        return res.any { r ->
            (r is String && r == name) || (r is Map<*, *> && r["name"] == name)
        }
    }

    companion object {
        /** The Streaming Catalogs addon (Netflix, HBO Max, Disney+, Prime Video, Apple TV+ trending lists). */
        private const val MANIFEST_TIMEOUT_MS = 4_000L
        private const val CATALOG_TIMEOUT_MS = 8_000L

        const val STREAMING_CATALOGS_BASE = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibSxwbXAscGNwLGhsdTo6VVM6MTc5NDc4ODc1MzUzMjowOjA6VVM%3D"

        /**
         * PenguPlay with its "Live TV" option on. Its config is URL-encoded JSON in the path; the
         * plain pengu.uk manifest lists no catalogues because Live TV is off by default.
         */
        const val PENGU_LIVE_BASE = "https://pengu.uk/%7B%22source_live-sports%22%3A%22on%22%7D/"

        /** Catalogue types that are on-demand video; every other type an addon uses counts as live. */
        private val VOD_TYPES = setOf("movie", "series", "anime", "other")
        private const val STREAM_TIMEOUT_MS = 15_000L

        /** Budget for one regular addon's stream request. */
        private const val ADDON_STREAM_TIMEOUT_MS = 25_000L

        /** PenguPlay asks ~27 providers at once and needs far longer than a scraper addon. */
        private const val PENGU_STREAM_TIMEOUT_MS = 60_000L

        /** Budget for one live event or channel, so the Live TV sheet cannot wait forever. */
        private const val PENGU_LIVE_TIMEOUT_MS = 45_000L

        /** NuvioSports needs longer: it asks every provider for a feed before answering. */
        private const val NUVIO_LIVE_TIMEOUT_MS = 60_000L


        /**
         * Live TV addons bundled with the app: always listed on the Live TV page and not removable.
         *
         * NuvioSports, pre-configured for US leagues. Its config segment holds only preferences
         * (timezone and league ids), no credential, so it is safe to ship - unlike SubSource below.
         */
        val BUILTIN_LIVE_ADDON_URLS = listOf(
            "https://nuviosports.xyz/eyJ0aW1lem9uZSI6IkFtZXJpY2EvTmV3X1lvcmsiLCJsaXZlTGVhZ3VlcyI6eyJpZHMiOlsibWxzIiwibndzbCIsImVwbCIsIm5mbCIsImNvbGxlZ2UtZm9vdGJhbGwiLCJuYmEiLCJ3bmJhIiwiY29sbGVnZS1iYXNrZXRiYWxsIiwibWxiIiwibmhsIiwiZm9ybXVsYS0xIiwibW90b2dwIiwibmFzY2FyIiwiaW5keWNhciIsInVmYyIsImJveGluZyIsInBnYS10b3VyIiwibGl2LWdvbGYiXSwiY3VzdG9tIjpbXSwib3RoZXIiOmZhbHNlfSwic3BvcnRzIjoiZm9vdGJhbGwsYmFza2V0YmFsbCxhbWVyaWNhbl9mb290YmFsbCxtb3RvcnNwb3J0LGhja2V5LGJhc2ViYWxsLGNvbGxlZ2UsbW1hIn0/manifest.json"
        )

        /** Permanently bundled addons the user can't remove. */
        val BUILTIN_ADDON_URLS = listOf(
            "https://torrentio.strem.fun/manifest.json",
            "https://knightcrawler.elfhosted.com/manifest.json",
            "https://cyberflix.elfhosted.com/manifest.json",
            "https://comet.elfhosted.com/manifest.json",
            "https://mediafusion.elfhosted.com/manifest.json",
            "https://onepace.e6ip.com/eyJkZWJyaWRTZXJ2aWNlcyI6W3sic2VydmljZSI6InAycCJ9XX0=/manifest.json",
            "https://addon.notorrent2.workers.dev/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
            "https://opensubtitlesv3-pro.dexter21767.com/eyJsYW5ncyI6WyJlbmdsaXNoIl0sInNvdXJjZSI6ImFsbCIsImFpVHJhbnNsYXRlZCI6dHJ1ZSwiYXV0b0FkanVzdG1lbnQiOnRydWV9/manifest.json",
            "https://subsource.strem.top/c2tfMGE1MWViZmRmNjA0MzVmZWEwNDdlYWQzYTQ3ZmFmYTZjNGRjY2E3NWE3OTMyZTQ0NDVlMWJlZTIwMGQ2NTcyYy9lbmdsaXNoL2hpSW5jbHVkZS90eXBlOjAv/manifest.json"
        )
    }
}

private const val TAG = "AddonManager"