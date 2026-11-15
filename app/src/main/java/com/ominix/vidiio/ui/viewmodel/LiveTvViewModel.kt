package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.live.Game
import com.ominix.vidiio.data.live.GameState
import com.ominix.vidiio.data.live.IptvChannels
import com.ominix.vidiio.data.live.League
import com.ominix.vidiio.data.live.SportsScores
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.stremio.StremioMeta
import com.ominix.vidiio.data.stremio.AddonManager
import com.ominix.vidiio.data.stremio.toStreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row on the Live TV page. [channels] rows hold always-on channels; the rest are PenguPlay events. */
data class LiveRow(
    val title: String,
    val items: List<Movie>,
    val channels: Boolean,
    /** Shown instead of cards when the row is empty ("Nothing live right now"). */
    val note: String? = null,
)

data class LiveTvState(
    /** null while loading. */
    val rows: List<LiveRow>? = null,
    val failed: Boolean = false,
    /** The event whose streams are being fetched or picked from. */
    val opening: Movie? = null,
    /** Streams for [opening]; null while they load. */
    val streams: List<StreamSource>? = null,
    val message: String? = null,
    /** Today's games per league (null while loading), and the league whose games are shown. */
    val sports: List<Pair<League, List<Game>>>? = null,
    val league: League? = null,
)

/**
 * Live TV: the viewer's own pasted links first, then PenguPlay's live events (when it lists any), then
 * free live channels from iptv-org; live sports scores sit above them all.
 */
class LiveTvViewModel(
    private val addonManager: AddonManager,
    private val iptv: IptvChannels,
    private val scores: SportsScores,
    private val settings: com.ominix.vidiio.data.repository.SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    /** Channel cards to their stream, so a channel plays without another request. */
    private val channelStreams = HashMap<String, StreamSource>()
    /** Cards from installed addons' live catalogues, to the addon event behind each. */
    private val addonEvents = HashMap<String, com.ominix.vidiio.data.stremio.LiveAddonEvent>()
    private var streamJob: Job? = null
    private var refreshJob: Job? = null

    init {
        // The first emission does the initial load; adding or removing a link reloads the page.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(settings.liveLinksFlow, settings.liveAddonsFlow) { links, addons -> links to addons }
                .distinctUntilChanged()
                .collect { refresh() }
        }
        // Scores and live status move by the minute; keep them current while the page is open.
        viewModelScope.launch {
            while (isActive) {
                loadSports()
                delay(SCORES_REFRESH_MS)
            }
        }
    }

    private suspend fun loadSports() {
        val sports = runCatching { scores.today() }
            .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "Scores failed", it) }
            .getOrNull() ?: return
        _state.update { s ->
            // Start on the league with the most live games, else the first one with any games.
            val league = s.league ?: sports.maxByOrNull { (_, games) -> games.count { it.state == GameState.LIVE } }
                ?.takeIf { (_, games) -> games.any { it.state == GameState.LIVE } }?.first
                ?: sports.firstOrNull { it.second.isNotEmpty() }?.first
            s.copy(sports = sports, league = league)
        }
    }

    fun selectLeague(league: League) = _state.update { it.copy(league = league) }

    /**
     * Looks for a stream of [game] among PenguPlay's live sports (searching by each team) and plays
     * it; when there is none, says which network airs the game.
     */
    fun openGame(game: Game) {
        streamJob?.cancel()
        val title = "${game.away.name} at ${game.home.name}"
        val movie = Movie(id = "game:${game.league.name}:${game.id}", title = title, posterUrl = game.home.logo.orEmpty(), source = PENGU_SOURCE)
        _state.update { it.copy(opening = movie, streams = null, message = null) }
        streamJob = viewModelScope.launch {
            val keys = listOf(game.home.name, game.away.name).map { it.lowercase() }
            fun matches(name: String) = name.lowercase().let { n -> keys.any { key -> n.contains(key) || n.contains(key.substringAfterLast(' ')) } }
            // Ask PenguPlay and every installed live addon at once; teams are searched by nickname ("Chiefs").
            val penguStreams = async {
                val event = listOf(game.away.name, game.home.name).firstNotNullOfOrNull { team ->
                    addonManager.penguLiveSearch(game.league.penguCatalog, team).firstOrNull { matches(it.name) }
                }
                event?.let {
                    runCatching { addonManager.penguLiveStreams(PENGU_TYPE, it.id).mapNotNull { s -> s.toStreamSource() } }
                        .onFailure { e -> if (e is CancellationException) throw e }
                        .getOrDefault(emptyList())
                }.orEmpty()
            }
            val addonStreams = async {
                val nicknames = listOf(game.away.name, game.home.name).map { it.substringAfterLast(' ') }.distinct()
                val events = nicknames.map { nick -> async { addonManager.addonLiveSearch(nick) } }.awaitAll().flatten()
                    .filter { matches(it.meta.name) }
                    .distinctBy { it.baseUrl + it.meta.id }
                    .take(MAX_GAME_EVENTS)
                events.map { event ->
                    async {
                        runCatching {
                            addonManager.addonLiveStreams(event).mapNotNull { s ->
                                s.toStreamSource()?.let { src -> src.copy(serverName = "${event.addonName}: ${src.serverName}") }
                            }
                        }.onFailure { e -> if (e is CancellationException) throw e }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
            val streams = (penguStreams.await() + addonStreams.await()).distinctBy { it.url }
            _state.update {
                if (it.opening?.id != movie.id) it
                else if (streams.isEmpty()) it.copy(
                    opening = null,
                    message = "No stream found for this game." +
                        (game.networks.takeIf { n -> n.isNotEmpty() }?.let { n -> " It's on ${n.joinToString(", ")}." } ?: "")
                )
                else it.copy(streams = streams)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        _state.update { it.copy(rows = null, failed = false) }
        refreshJob = viewModelScope.launch {
            val links = settings.liveLinksFlow.first()
            val mine = links.map { link ->
                async {
                    runCatching { iptv.fromLink(link.name, link.url) }
                        .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "Link ${link.name} failed", it) }
                        .getOrNull()
                        .let { link to it }
                }
            }
            val addonRows = async {
                runCatching { addonManager.addonLiveCatalogs() }
                    .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "Addon live catalogues failed", it) }
                    .getOrNull()
                    ?.let { addonRows(it) }
                    .orEmpty()
            }
            val pengu = async {
                runCatching { addonManager.penguLiveCatalogs() }
                    .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "PenguPlay live failed", it) }
                    .getOrDefault(emptyList())
                    .map { (name, metas) -> LiveRow(name, metas.map { it.toLiveMovie() }, channels = false) }
            }
            val channels = async {
                runCatching { iptv.byCategory() }
                    .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "Channel list failed", it) }
                    .getOrNull()
            }
            val channelRows = channels.await()?.map { (category, list) -> channelRow(category, list) }
            val loaded = mine.awaitAll()
            // Playlists get a row each; single streams share one "My Channels" row.
            val (lists, singles) = loaded.mapNotNull { (_, channels) -> channels }.partition { it.size > 1 }
            val myRows = lists.map { channels -> channelRow(channels.first().category, channels) } +
                listOfNotNull(singles.flatten().takeIf { it.isNotEmpty() }?.let { channelRow(MY_CHANNELS, it) })
            val broken = loaded.filter { it.second == null }.map { it.first.name }
            val rows = myRows + addonRows.await() + pengu.await() + channelRows.orEmpty()
            _state.update {
                it.copy(
                    rows = rows,
                    failed = rows.isEmpty() && channelRows == null,
                    message = broken.takeIf { b -> b.isNotEmpty() }?.let { b -> "Couldn't load: ${b.joinToString(", ")}" } ?: it.message,
                )
            }
        }
    }

    /**
     * Rows for the live addons, grouped by addon (A to Z) in each addon's own catalogue order. An addon
     * with nothing on keeps one row saying so, and one that could not be reached gets a row saying that.
     */
    private fun addonRows(result: com.ominix.vidiio.data.stremio.AddonLiveResult): List<LiveRow> {
        val byAddon = result.catalogs.groupBy { it.addonName }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        val rows = byAddon.flatMap { (addon, catalogs) ->
            val full = catalogs.filter { it.events.isNotEmpty() }
            if (full.isEmpty()) listOf(LiveRow(addon, emptyList(), channels = false, note = "Nothing live right now."))
            else full.map { c -> LiveRow("$addon \u00b7 ${c.catalogName}", c.events.map { it.toCard() }, channels = false) }
        }
        val unreachable = result.unreachable.map { url ->
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url
            LiveRow(host, emptyList(), channels = false, note = "Couldn't load this addon. Check the link in Settings > Live TV Links & Addons.")
        }
        return rows + unreachable
    }

    private fun com.ominix.vidiio.data.stremio.LiveAddonEvent.toCard(): Movie {
        val id = "addon:${baseUrl}:${meta.id}"
        addonEvents[id] = this
        return Movie(
            id = id,
            title = meta.name,
            posterUrl = meta.poster ?: meta.logo ?: meta.background ?: "",
            backdropUrl = meta.background,
            synopsis = meta.description ?: meta.releaseInfo,
            source = ADDON_SOURCE,
        )
    }

    private fun channelRow(title: String, channels: List<com.ominix.vidiio.data.live.LiveChannel>) =
        LiveRow(title, channels.map { channel ->
            val id = "iptv:${channel.source.url}"
            channelStreams[id] = channel.source
            Movie(id = id, title = channel.name, posterUrl = channel.logo.orEmpty(), source = CHANNEL_SOURCE)
        }, channels = true)

    /** A channel plays at once; a PenguPlay event fetches its streams first and may offer several. */
    fun open(movie: Movie) {
        streamJob?.cancel()
        channelStreams[movie.id]?.let { source ->
            _state.update { it.copy(opening = movie, streams = listOf(source), message = null) }
            return
        }
        _state.update { it.copy(opening = movie, streams = null, message = null) }
        val addonEvent = addonEvents[movie.id]
        streamJob = viewModelScope.launch {
            val streams = try {
                if (addonEvent != null) addonManager.addonLiveStreams(addonEvent).mapNotNull { it.toStreamSource() }
                else addonManager.penguLiveStreams(PENGU_TYPE, movie.id).mapNotNull { it.toStreamSource() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Live streams for ${movie.id} failed", e)
                emptyList()
            }
            _state.update {
                if (it.opening?.id != movie.id) it
                else if (streams.isEmpty()) it.copy(opening = null, message = "No live stream for ${movie.title} right now.")
                else it.copy(streams = streams)
            }
        }
    }

    fun dismiss() {
        streamJob?.cancel()
        _state.update { it.copy(opening = null, streams = null) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun StremioMeta.toLiveMovie() = Movie(
        id = id,
        title = name,
        posterUrl = poster ?: background ?: logo ?: "",
        backdropUrl = background,
        synopsis = description ?: releaseInfo,
        source = PENGU_SOURCE,
    )

    private companion object {
        const val TAG = "LiveTvViewModel"
        const val PENGU_TYPE = "tv"
        const val PENGU_SOURCE = "pengu-live"
        const val CHANNEL_SOURCE = "iptv"
        const val MY_CHANNELS = "My Channels"
        const val ADDON_SOURCE = "addon-live"
        /** How many matching addon events a game pulls streams from. */
        const val MAX_GAME_EVENTS = 6
        const val SCORES_REFRESH_MS = 60_000L
    }
}
