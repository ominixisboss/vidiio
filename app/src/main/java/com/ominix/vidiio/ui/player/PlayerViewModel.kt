package com.ominix.vidiio.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.ominix.vidiio.VidiioApplication
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.subtitles.SubtitleTrack
import com.ominix.vidiio.data.stremio.AddonManager
import com.ominix.vidiio.data.api.SubdlService
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.data.repository.MediaPlayerChoice
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.SourceHealth
import com.ominix.vidiio.data.repository.SourceStatus
import com.ominix.vidiio.data.repository.SubtitleEdge
import com.ominix.vidiio.data.repository.SubtitleStyle
import com.ominix.vidiio.data.repository.WatchProgressRepository
import com.ominix.vidiio.download.DownloadManager
import com.ominix.vidiio.torrent.TorrentFileInfo
import com.ominix.vidiio.ui.viewmodel.MediaSession
import kotlinx.coroutines.flow.Flow
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything PlayerScreen renders. One object so the screen has a single subscription. */
@UnstableApi
data class PlayerUiState(
    val selectedSource: StreamSource? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val errorMessage: String? = null,
    /** Host of an addon whose stream turned out to be a "sign in" placeholder clip, shown until dismissed. */
    val signInHost: String? = null,
    /** Set when something already watched is reopened: the saved point, for the Resume / Start over prompt. */
    val resumePromptMs: Long? = null,

    /** The episode that will start by itself when this one ends, and seconds left to cancel. */
    val upNext: com.ominix.vidiio.data.model.Episode? = null,
    val upNextSeconds: Int = 0,
    /** True from choosing the next episode until it is actually playing. */
    val loadingNext: Boolean = false,

    val subtitlesEnabled: Boolean = true,
    val selectedSubtitleUrl: String? = null,
    val subtitleOffsetMs: Long = 0L,

    val availableAudioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioTrack: AudioTrackInfo? = null,

    val playbackSpeed: Float = 1.0f,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val notchSafe: Boolean = false,

    val showSkipIntro: Boolean = false,
    val showSkipOutro: Boolean = false,
    val showAutoNext: Boolean = false,
    val autoNextSeconds: Int = 5,

    /** Live TV (an IPTV channel or a live event): no end time, no progress, no intro skip. */
    val isLive: Boolean = false,
    val isTorrentStream: Boolean = false,
    val isTorrentLoading: Boolean = false,
    val torrentFiles: List<TorrentFileInfo>? = null,
    val showTorrentFileSheet: Boolean = false,
    val pickedTorrentFileIndex: Int? = null,

    val mediaPlayerChoice: MediaPlayerChoice = MediaPlayerChoice.INTERNAL,
    val isVlcAvailable: Boolean = false,
    val isVlcActive: Boolean = false,
) {
    val isEmbed: Boolean
        get() = selectedSource?.let { it.isWebPage || it.url.contains("embed") || it.url.contains("vidsrc") } == true

    val isMagnet: Boolean
        get() = selectedSource?.url?.startsWith("magnet:") == true
}

/**
 * Owns the ExoPlayer instance and the whole playback session.
 *
 * This used to live in `PlayerScreen` as ~30 `remember`ed variables around a `remember`ed
 * ExoPlayer, which meant the player and everything about the session were destroyed and
 * rebuilt on any configuration change the Activity does not declare in `configChanges` -
 * a full re-buffer, and for a torrent stream that is 10-30 seconds of finding peers
 * again. Held here, the session survives.
 *
 * The screen keeps what is genuinely view state (which menu is open, whether the controls
 * are showing) and the Android Service binding, which is tied to its context.
 */
@UnstableApi
class PlayerViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val movieRepository: MovieRepository,
    downloadRepository: DownloadRepository,
    downloadManager: DownloadManager,
    subdlService: SubdlService,
    watchProgressRepository: WatchProgressRepository,
    addonManager: AddonManager,
    movie: Movie,
    initialEpisodeId: String? = null,
    initialSource: StreamSource? = null,
) : AndroidViewModel(application) {

    /**
     * The title being played: details, episodes, sources, subtitles, watch progress.
     *
     * The player used to reach into DetailsViewModel for all of this, which meant
     * PlayerScreen took two ViewModels that had to be keyed in lockstep and carried
     * favourites and source-toggle state it never used.
     */
    private val session = MediaSession(
        scope = viewModelScope,
        movieRepository = movieRepository,
        downloadRepository = downloadRepository,
        downloadManager = downloadManager,
        subdlService = subdlService,
        settingsRepository = settingsRepository,
        watchProgressRepository = watchProgressRepository,
        addonManager = addonManager,
        initialMovie = movie,
        initialEpisodeId = initialEpisodeId,
        initialSource = initialSource
    )

    val movie: StateFlow<Movie?> = session.movie
    val selectedEpisode: StateFlow<Episode?> = session.selectedEpisode
    val subtitles: StateFlow<List<SubtitleTrack>> = session.subtitles
    val streamSources: StateFlow<List<StreamSource>> = session.streamSources

    /** Result of the "does this source work" check, for the Sources panel. */
    val sourceHealth: StateFlow<Map<String, SourceHealth>> = session.sourceHealth
    val unavailableCount: StateFlow<Int> = session.unavailableCount
    val showUnavailable: StateFlow<Boolean> = session.showUnavailable
    fun setShowUnavailable(show: Boolean) = session.setShowUnavailable(show)

    /** User's subtitle appearance, applied to the player's SubtitleView by the screen. */
    val subtitleStyle: StateFlow<SubtitleStyle> = settingsRepository.subtitleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleStyle())

    /** Picking an episode (e.g. from the in-player list) really starts it, with the best stream for it. */
    fun selectEpisode(episode: Episode) = startEpisode(episode)

    private var upNextJob: Job? = null

    /** True once the next episode has been looked up for this one, so it happens once per episode. */
    private var nextPrefetched = false

    /**
     * While an episode plays, find the next one's sources and join its best torrent's swarm, so the Up Next
     * countdown (or a tap on the episode list) starts it right away. The scrape also fills the source cache,
     * so the next episode's list is there instantly.
     */
    private fun prefetchNextEpisode() {
        if (nextPrefetched) return
        val show = session.movie.value ?: return
        val next = com.ominix.vidiio.data.repository.nextEpisodeAfter(show, session.selectedEpisode.value) ?: return
        nextPrefetched = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                var best: StreamSource? = null
                kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                    movieRepository.getStreamSources(show, next).collect { source ->
                        val good = source.url.startsWith("magnet:") && (source.seeders ?: 0) >= 5 &&
                            com.ominix.vidiio.data.repository.SourceLanguage.isLikelyEnglish(source.serverName) &&
                            !com.ominix.vidiio.data.repository.SourceHeaviness.isHeavy(source.serverName)
                        if (good && (best == null || (source.seeders ?: 0) > (best?.seeders ?: 0))) best = source
                    }
                }
                val pick = best ?: return@runCatching
                val engine = (getApplication<Application>() as VidiioApplication).torrServerEngine
                if (engine.ensureStarted()) {
                    engine.getApi()?.addTorrent(com.ominix.vidiio.torrent.TorrentTuning.withTrackers(pick.url))
                }
            }
        }
    }

    /**
     * Set by the screen: returns a [CreditsDetector.WIDTH] x [CreditsDetector.HEIGHT] copy of the current
     * video frame, or null if one cannot be had right now (no surface yet, DRM, ...).
     */
    var frameSampler: (suspend () -> IntArray?)? = null
    private val creditsTracker = CreditsTracker()
    private var lastCreditsSampleAt = 0L

    /** The episode finished: play next episode directly if available. */
    private fun onEpisodeEnded() {
        val show = session.movie.value ?: return
        val next = com.ominix.vidiio.data.repository.nextEpisodeAfter(show, session.selectedEpisode.value) ?: return
        startEpisode(next)
    }

    fun cancelUpNext() {
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null, upNextSeconds = 0) }
    }

    fun playNextEpisode() {
        val show = session.movie.value ?: return
        val next = com.ominix.vidiio.data.repository.nextEpisodeAfter(show, session.selectedEpisode.value) ?: return
        startEpisode(next)
    }

    private fun startEpisode(next: Episode) {
        if (next.id == session.selectedEpisode.value?.id && _state.value.selectedSource != null) return
        creditsTracker.reset()
        nextPrefetched = false
        upNextJob?.cancel()
        skippedUrls.clear()
        hlsRetryUrls.clear()
        // The saved resume point belongs to the episode that was playing, not to the one being switched to:
        // without this the new episode seeks to the old one's timestamp as soon as it loads.
        resumePositionMs = 0L
        didResume = true
        torrentPrepareRetries = 0
        // Drop the finished video: otherwise the player still reports a position at the end of it and the
        // credits check below would immediately offer the episode after this one, and so on.
        player.stop()
        player.clearMediaItems()
        // Empty the session's source list FIRST. The picker below reacts to that list, and if the old episode's
        // sources are still in it when the selected source is cleared, it re-picks one of them and the new
        // episode plays the old one.
        session.selectEpisode(next)
        // Clearing the source lets the source-picker below choose the best working English stream for the
        // new episode as soon as its links have been found and checked.
        _state.update {
            it.copy(
                upNext = null, upNextSeconds = 0, loadingNext = true, selectedSource = null,
                selectedSubtitleUrl = null, positionMs = 0L, durationMs = 0L, errorMessage = null
            )
        }
    }
    fun downloadSource(source: StreamSource) = session.downloadSource(source)
    fun getDownloadStatus(url: String): Flow<DownloadStatus?> = session.getDownloadStatus(url)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.mediaPlayerFlow.collect { choice ->
                _state.update { it.copy(mediaPlayerChoice = choice) }
            }
        }
    }

    /** Resume point from Continue Watching. Applied once, when the media is ready. */
    private var resumePositionMs: Long = 0L
    private var didResume = false

    /** Counts the silent re-kicks described in [Player.Listener.onPlayerError]. */
    private var torrentPrepareRetries = 0

    /** Sources that failed to decode on this device, so the fallback never loops back to them. */
    private val skippedUrls = mutableSetOf<String>()

    /** URLs already re-opened as HLS once, so a link that fails in both forms is not retried forever. */
    private val hlsRetryUrls = mutableSetOf<String>()

    /** Attempts already spent re-opening a live feed that dropped; reset per source and per retry. */
    private var liveRetries = 0

    /** Bumped to re-run the screen's source-preparation effect. */
    private val _retryTrigger = MutableStateFlow(0)
    val retryTrigger: StateFlow<Int> = _retryTrigger.asStateFlow()

    private var pollJob: Job? = null
    private var skipIntroDismissJob: Job? = null
    private var isIntroDismissed = false
    private var audioHudJob: Job? = null

    private val _audioHudText = MutableStateFlow<String?>(null)
    val audioHudText: StateFlow<String?> = _audioHudText.asStateFlow()

    // OkHttp for http(s); DefaultDataSource delegates file:// / content:// (offline
    // downloads) to FileDataSource / ContentDataSource.
    private val httpDataSourceFactory = OkHttpDataSource.Factory(
        (application as VidiioApplication).playbackHttpClient
    )

    private val dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val current = _state.value.selectedSource
            val currentUrl = current?.url
            val live = isLiveFeed()

            // A live feed has no second source to fall back to, and losing it for a moment (a
            // segment at the live edge, a CDN hiccup) is normal. Rejoin or retry the same URL
            // rather than declaring the channel dead.
            if (live) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // A stall, or the app sitting in the background, left the live window.
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }
                if (current != null && liveRetries < LIVE_RETRY_LIMIT) {
                    liveRetries++
                    _state.update { it.copy(isBuffering = true, errorMessage = null) }
                    viewModelScope.launch {
                        delay(LIVE_RETRY_DELAY_MS * liveRetries)
                        // Only if the viewer is still on this feed.
                        if (_state.value.selectedSource?.url == currentUrl) startPlayback(current)
                    }
                    return
                }
                _state.update {
                    it.copy(
                        isBuffering = false,
                        errorMessage = "Live stream stopped: ${error.localizedMessage ?: "the feed is offline"}",
                    )
                }
                return
            }

            // Some providers serve an HLS playlist from a URL with no .m3u8 in it (PenguPlay hands
            // out /play/<id>?psig=…), so the source was never flagged HLS and ExoPlayer opened the
            // playlist as a progressive file and failed to parse it. Reopen the same URL as HLS
            // once before writing the link off.
            if (current != null && currentUrl != null && !current.isM3u8 &&
                !current.url.contains("m3u8", ignoreCase = true) &&
                isContainerParseError(error) && hlsRetryUrls.add(currentUrl)
            ) {
                _state.update { it.copy(isBuffering = true, errorMessage = null) }
                startPlayback(current.copy(isM3u8 = true))
                return
            }

            // Mark the failed stream as dead in health state
            if (currentUrl != null) {
                skippedUrls += currentUrl
                session.updateSourceHealth(currentUrl, com.ominix.vidiio.data.repository.SourceHealth(com.ominix.vidiio.data.repository.SourceStatus.DEAD, note = "Playback Error"))
            }

            // Look for the next available non-skipped working stream
            val availableSources = session.streamSources.value
            val nextSource = availableSources.firstOrNull {
                it.url != currentUrl && !skippedUrls.contains(it.url) &&
                    !com.ominix.vidiio.data.repository.SourceHeaviness.isHeavy(it.serverName)
            } ?: availableSources.firstOrNull { it.url != currentUrl && !skippedUrls.contains(it.url) }

            if (nextSource != null) {
                torrentPrepareRetries = 0
                selectSource(nextSource)
                viewModelScope.launch {
                    android.widget.Toast.makeText(application, "Stream failed, switching to ${nextSource.serverName}", android.widget.Toast.LENGTH_SHORT).show()
                }
                return
            }

            if (_state.value.isTorrentStream && torrentPrepareRetries < 3) {
                torrentPrepareRetries++
                _state.update { it.copy(isTorrentLoading = true) }
                viewModelScope.launch {
                    delay(4000)
                    _retryTrigger.update { n -> n + 1 }
                }
            } else {
                _state.update { it.copy(errorMessage = "Playback Error: ${error.localizedMessage ?: "Stream unplayable"}") }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val tracksList = mutableListOf<AudioTrackInfo>()
            tracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        tracksList.add(
                            AudioTrackInfo(
                                name = format.label ?: format.language ?: "Track ${tracksList.size + 1}",
                                groupIndex = groupIndex,
                                trackIndex = i,
                                format = format
                            )
                        )
                    }
                }
            }
            _state.update { it.copy(availableAudioTracks = tracksList) }

            // Auto-select English audio track for dual-audio anime and foreign releases if available
            val englishTrack = tracksList.firstOrNull { track ->
                val lang = track.format.language?.lowercase().orEmpty()
                val name = track.name.lowercase()
                lang == "en" || lang == "eng" || lang == "english" || name.contains("english") || name.contains("en ") || name.contains("[en]")
            }
            if (englishTrack != null && _state.value.selectedAudioTrack == null) {
                selectAudioTrack(englishTrack)
            }

            // The file says what language its audio is in. If it says so and none of it is English,
            // this source is the wrong one: move on to the next. (Players pick the English track of a
            // dual-audio file on their own - see setPreferredAudioLanguages below.)
            val tags = tracksList.map { it.format.language }
            val current = _state.value.selectedSource
            if (current != null && com.ominix.vidiio.data.repository.AudioLanguage.definitelyNotEnglish(tags)) {
                val next = session.streamSources.value.firstOrNull {
                    it.url != current.url && !skippedUrls.contains(it.url) &&
                        !com.ominix.vidiio.data.repository.SourceHeaviness.isHeavy(it.serverName) &&
                        com.ominix.vidiio.data.repository.SourceLanguage.isLikelyEnglish(it.serverName)
                }
                if (next != null) {
                    skippedUrls += current.url
                    torrentPrepareRetries = 0
                    selectSource(next)
                }
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _state.update { it.copy(isPlaying = playing, loadingNext = if (playing) false else it.loadingNext) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
        }
    }

    /**
     * Built eagerly, not `by lazy`: the initialiser has to start the polling coroutine, and
     * on Dispatchers.Main.immediate that body can run before the lazy field is assigned,
     * re-entering a half-initialised delegate.
     */
    val player: ExoPlayer = run {
        // A 2-minute buffer of 1080p is ~120 MB of RAM. Fine on a phone, fatal on a 1 GB stick, where the
        // system kills the app. Keep it to 30 s there; fast start (below) is unchanged.
        val am = application.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val lowRam = am?.let { it.isLowRamDevice || it.memoryClass <= 192 } == true
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowRam) 15000 else 50000, // minBufferMs
                if (lowRam) 30000 else 120000, // maxBufferMs
                2500, // bufferForPlaybackMs (Fast start!)
                5000 // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val builder = ExoPlayer.Builder(application)
            .setTrackSelector(androidx.media3.exoplayer.trackselection.DefaultTrackSelector(application).apply {
                // Choose the English audio track whenever a file has one (dual-audio anime, etc.).
                parameters = buildUponParameters().setPreferredAudioLanguages("en", "eng").build()
            })
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)

        if ((application as VidiioApplication).assNativeStyling) {
            // buildWithAssSupport installs a libass-backed ASS/SSA subtitle renderer
            // (styled positioning/fonts/colours). CUES mode pre-renders to bitmap cues the
            // existing SubtitleView draws — no extra overlay view to wire up.
            builder.buildWithAssSupport(
                context = application,
                renderType = AssRenderType.CUES,
                dataSourceFactory = dataSourceFactory
            )
        } else {
            // Without libass, media3's own SSA decoder emits ordinary text cues, which
            // SubtitleView can restyle. That is the whole point of the setting: bitmaps
            // cannot be restyled, so keeping ASS styling and applying your own are
            // mutually exclusive.
            builder.build()
        }
    }

    private var notchSafeSeeded = false

    init {
        player.addListener(playerListener)
        // These touch the player, so they must run after it exists. Started from the constructor
        // body above the player they used to run first (Main.immediate resumes synchronously once
        // DataStore has a cached value) and crashed with a null player when subtitles default to Off.
        viewModelScope.launch {
            // Read the user's default once, before any subtitle track can arrive - not as a
            // running collector, since after this the player owns subtitlesEnabled for the
            // rest of the session (the in-player "Off" toggle shouldn't be fought by Settings).
            val subtitlesEnabledByDefault = settingsRepository.subtitlesEnabledByDefaultFlow.first()
            _state.update { it.copy(subtitlesEnabled = subtitlesEnabledByDefault) }
            if (!subtitlesEnabledByDefault) setTextTrackDisabled(true)

            subtitles.collect { subTracks ->
                if (subTracks.isNotEmpty() && _state.value.subtitlesEnabled && _state.value.selectedSubtitleUrl == null) {
                    val bestTrack = subTracks.first()
                    selectSubtitle(bestTrack)
                }
            }
        }
        startPolling()

        viewModelScope.launch {
            session.resumePositionMs.collect { resumePositionMs = it }
        }
        viewModelScope.launch {
            // Start on the best source that has actually been verified. Waiting for a check
            // costs a moment, so if nothing verifies we fall back to the first listed source
            // once scraping is done, or after a grace period - never sit on a blank player.
            combine(session.streamSources, session.sourceHealth, session.isSearchingSources) { sources, health, searching ->
                Triple(sources, health, searching)
            }.collectLatest { (sources, health, searching) ->
                // Same choice the Details page makes: working, English, light enough to decode.
                val pick = com.ominix.vidiio.data.repository.pickStreamToPlay(sources, health, searching, giveUp = false)
                when {
                    pick != null -> selectSourceIfNone(pick)
                    sources.isEmpty() -> Unit
                    else -> {
                        delay(AUTO_PICK_GRACE_MS)
                        selectSourceIfNone(
                            com.ominix.vidiio.data.repository.pickStreamToPlay(sources, health, false, giveUp = true)
                                ?: sources.first()
                        )
                    }
                }
            }
        }

        // Seed the cutout preference once; the Aspect menu can then flip it live for the
        // session without writing back until the user asks.
        viewModelScope.launch {
            val avoid = settingsRepository.avoidCameraCutoutFlow.first()
            if (!notchSafeSeeded) {
                notchSafeSeeded = true
                _state.update { it.copy(notchSafe = avoid) }
            }
        }
    }

    // ── Position polling, resume, skip/auto-next ─────────────────────────────

    /**
     * Whether the feed being played is live.
     *
     * The source knows: the IPTV parser and the live addon paths flag it before playback starts,
     * which is what makes the first seconds behave. ExoPlayer only knows once it has parsed a live
     * playlist, so it covers feeds from providers that do not flag anything.
     */
    private fun isLiveFeed(): Boolean =
        player.isCurrentMediaItemLive || _state.value.selectedSource?.isLive == true

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                val position = player.currentPosition
                val live = isLiveFeed()
                val duration = if (player.duration > 0) player.duration else 0L
                // A live feed can report a finite DVR window, but none of the film logic below
                // (resume, intro skip, up next, progress) may run for it.
                val vodDuration = if (live) 0L else duration

                // Some addons answer an unauthorised request with a short "sign in at <site>" clip instead of the
                // film. A real film or episode is never under a minute, so an addon stream that short is one of
                // those: skip it, tell the viewer where to sign in, and move on to the next source.
                if (!live && duration in 1L..PLACEHOLDER_MAX_MS && player.playbackState == Player.STATE_READY) {
                    val current = _state.value.selectedSource
                    if (current != null && current.sourceId == "stremio" && !current.url.startsWith("magnet:") &&
                        !skippedUrls.contains(current.url)
                    ) {
                        skippedUrls += current.url
                        session.updateSourceHealth(current.url, com.ominix.vidiio.data.repository.SourceHealth(com.ominix.vidiio.data.repository.SourceStatus.DEAD, note = "Clip skipped"))
                        val availableSources = session.streamSources.value
                        val next = availableSources.firstOrNull { it.url != current.url && !skippedUrls.contains(it.url) &&
                            !com.ominix.vidiio.data.repository.SourceHeaviness.isHeavy(it.serverName) }
                            ?: availableSources.firstOrNull { it.url != current.url && !skippedUrls.contains(it.url) }
                        if (next != null) {
                            selectSource(next)
                            viewModelScope.launch {
                                android.widget.Toast.makeText(getApplication(), "Sign-in clip skipped, auto-switching to ${next.serverName}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Something already watched is being reopened: ask whether to carry on or start over, rather
                // than silently jumping. The video waits (paused) until the viewer chooses.
                if (!didResume && resumePositionMs > 0L && vodDuration > 0L && player.playbackState == Player.STATE_READY) {
                    didResume = true
                    val nearEnd = resumePositionMs >= vodDuration - 60_000L
                    if (resumePositionMs >= RESUME_PROMPT_MIN_MS && !nearEnd) {
                        player.pause()
                        _state.update { it.copy(resumePromptMs = resumePositionMs) }
                    }
                }

                val playing = player.isPlaying

                // Halfway through, get the next episode ready in the background.
                if (playing && vodDuration > 0L && position > vodDuration * 0.35) prefetchNextEpisode()

                // Persist progress every ~10s of playback.
                if (playing && vodDuration > 0L && ++tick % 20 == 0) {
                    session.saveWatchProgress(position, vodDuration)
                }

                _state.update { s ->
                    var next = s.copy(
                        positionMs = position,
                        durationMs = duration,
                        bufferedPositionMs = player.bufferedPosition,
                        isLive = live,
                    )
                    if (vodDuration > 0) {
                        val inIntroRange = position in 5000L..90000L
                        if (!inIntroRange) isIntroDismissed = false

                        val remaining = vodDuration - position
                        next = next.copy(
                            showSkipIntro = inIntroRange && !isIntroDismissed,
                            showSkipOutro = remaining < 120000L && duration > 120000L,
                            showAutoNext = false,
                            autoNextSeconds = 0
                        )
                    }
                    next
                }

                // Auto-dismiss the Skip Intro prompt after 7s of being visible.
                if (_state.value.showSkipIntro && skipIntroDismissJob?.isActive != true) {
                    skipIntroDismissJob = viewModelScope.launch {
                        delay(7000)
                        dismissSkipIntro()
                    }
                }

                delay(500)
            }
        }
    }

    // ── Source selection and playback ────────────────────────────────────────

    /** Picks the first source once the details load, if the caller gave us none. */
    private fun selectSourceIfNone(source: StreamSource?) {
        if (_state.value.selectedSource == null && source != null) selectSource(source)
    }

    fun selectSource(source: StreamSource) {
        // A new source is a new torrent session: forget the picked file and retry count.
        _state.update {
            it.copy(selectedSource = source, pickedTorrentFileIndex = null, isVlcActive = false)
        }
        torrentPrepareRetries = 0
        liveRetries = 0
    }

    fun setMediaPlayerChoice(choice: MediaPlayerChoice) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayer(choice)
        }
    }

    fun setVlcAvailable(available: Boolean) {
        _state.update { it.copy(isVlcAvailable = available) }
    }

    fun setVlcActive(active: Boolean) {
        _state.update { it.copy(isVlcActive = active) }
    }

    fun playInVlc(context: android.content.Context) {
        val source = state.value.selectedSource ?: return
        if (source.url.startsWith("magnet:")) {
            android.widget.Toast.makeText(context, "Preparing torrent stream for VLC...", android.widget.Toast.LENGTH_SHORT).show()
            _state.update { it.copy(isVlcActive = true) }
            return
        }
        pause()
        session.saveWatchProgress(state.value.positionMs, state.value.durationMs)

        val launched = ExternalPlayer.playInVlc(
            context = context,
            url = source.url,
            title = movie.value?.title,
            positionMs = state.value.positionMs,
            subtitleUrl = state.value.selectedSubtitleUrl,
            headers = source.headers,
        )

        if (launched) {
            _state.update { it.copy(isVlcActive = true) }
        } else {
            _state.update { it.copy(isVlcActive = false) }
            play()
        }
    }

    fun copyStreamUrlToClipboard(context: android.content.Context) {
        state.value.selectedSource?.url?.let { url ->
            ExternalPlayer.copyStreamUrlToClipboard(context, url)
        }
    }

    /** Starts playback of a directly playable URL. */
    fun startPlayback(source: StreamSource) {
        player.stop()
        player.clearMediaItems()

        // Many streaming hosts 403 without the scraper's Referer/Origin/UA headers.
        // ExoPlayer carries these on the HTTP data source, not the MediaItem.
        val requestHeaders = buildMap {
            put(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            source.headers?.let { putAll(it) }
        }
        httpDataSourceFactory.setDefaultRequestProperties(requestHeaders)

        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(streamMimeType(source))
            .build()

        // C.TIME_UNSET, not 0: a live HLS feed usually carries a DVR window, and position 0 is
        // its oldest segment - minutes behind the live edge. TIME_UNSET starts at the default
        // position, which for a live window is the edge.
        player.setMediaItem(mediaItem, C.TIME_UNSET)
        _state.update { it.copy(isLive = source.isLive, errorMessage = null) }
        player.prepare()
        player.play()
    }

    /**
     * Whether ExoPlayer failed because it could not parse what it was given, rather than because
     * the link was unreachable. Those are the only failures worth re-opening as HLS: a 404 or a
     * timeout says nothing about the container.
     */
    private fun isContainerParseError(error: PlaybackException): Boolean {
        // PlaybackException itself only says "Source error"; the reason is in the cause chain.
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException) return true
            if (cause.message?.contains("None of the available extractors", ignoreCase = true) == true) return true
            cause = cause.cause
        }
        return error.errorCode in 3001..3004
    }

    /**
     * The container to open [source] as, when its URL says so.
     *
     * ExoPlayer infers HLS and DASH from a plain ".m3u8"/".mpd" path, but not when the URL
     * carries a token, a query string or no extension at all - the shape most IPTV panels hand
     * out - and a live feed opened as a progressive file simply fails. So only HLS needs help,
     * and the source already knows whether it is HLS.
     */
    private fun streamMimeType(source: StreamSource): String? =
        if (source.isM3u8 || source.url.contains("m3u8", ignoreCase = true)) {
            MimeTypes.APPLICATION_M3U8
        } else {
            null
        }

    // ── Transport ────────────────────────────────────────────────────────────

    fun togglePlayPause() = if (player.isPlaying) pause() else play()

    fun play() {
        snapToLiveEdgeIfBehind()
        player.play()
    }

    /**
     * Re-joins the live edge when playback resumes well behind it.
     *
     * A paused live feed keeps its position while the playlist keeps sliding, so resuming just
     * continues from wherever it stopped - minutes behind for a long pause, because nothing
     * here ever asked to move forward. ExoPlayer only reports BEHIND_LIVE_WINDOW once the
     * position has already left the window, which leaves everything between the live edge and
     * the window edge playing as a recording. This closes that gap.
     *
     * A fresh live open sits a few target durations behind the end, so the threshold is that
     * offset plus slack: a short pause keeps its DVR position, a long one rejoins live.
     */
    private fun snapToLiveEdgeIfBehind() {
        if (!isLiveFeed() || player.playbackState == Player.STATE_IDLE) return
        // The DVR window length for a live feed; unset until the playlist has been parsed.
        val window = player.duration
        if (window <= 0L) return
        val behind = window - player.currentPosition
        // Half the window, capped: a live playlist can carry minutes of DVR or only a handful
        // of segments, and the threshold has to make sense for both.
        if (behind <= minOf(LIVE_EDGE_SNAP_BEHIND_MS, window / 2)) return
        // Same idea for where to land: keep an eighth of the window as headroom, never more
        // than the fixed padding, so a 60 s window is not rejoined 30 s back.
        val headroom = minOf(LIVE_EDGE_PADDING_MS, window / 8)
        player.seekTo(player.currentMediaItemIndex, (window - headroom).coerceAtLeast(0L))
    }

    fun pause() {
        player.pause()
        runCatching { session.saveWatchProgress(player.currentPosition, player.duration) }
    }
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun seekBy(deltaMs: Long) {
        val target = player.currentPosition + deltaMs
        val max = player.duration.coerceAtLeast(0L)
        player.seekTo(target.coerceIn(0L, if (max > 0) max else target.coerceAtLeast(0L)))
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setResizeMode(mode: Int) = _state.update { it.copy(resizeMode = mode) }

    /** The player is always immersive; the fullscreen button is a quick fill/fit toggle. */
    fun toggleFillMode() = _state.update {
        it.copy(
            resizeMode = if (it.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        )
    }

    fun setNotchSafe(enabled: Boolean, persist: Boolean = true) {
        _state.update { it.copy(notchSafe = enabled) }
        if (persist) viewModelScope.launch { settingsRepository.setAvoidCameraCutout(enabled) }
    }

    // ── Subtitles ────────────────────────────────────────────────────────────

    private fun setTextTrackDisabled(disabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled).build()
    }

    /** Re-enable the text renderer only - no setMediaItem, so no re-buffer. */
    fun useEmbeddedSubtitles() {
        _state.update { it.copy(selectedSubtitleUrl = null, subtitlesEnabled = true) }
        setTextTrackDisabled(false)
    }

    fun selectSubtitle(subtitle: SubtitleTrack) {
        _state.update { it.copy(selectedSubtitleUrl = subtitle.url, subtitlesEnabled = true) }
        setTextTrackDisabled(false)

        val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setMimeType(subtitleMimeType(subtitle.url))
            .setLanguage(subtitle.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val currentMediaItem = player.currentMediaItem ?: return
        val updatedItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subConfig))
            .build()
        player.setMediaItem(updatedItem, player.currentPosition)
    }

    /**
     * Turn off every subtitle track by disabling the text renderer. No setMediaItem here -
     * that would re-prepare and re-buffer the stream.
     */
    fun disableSubtitles() {
        _state.update { it.copy(selectedSubtitleUrl = null, subtitlesEnabled = false) }
        setTextTrackDisabled(true)
    }

    fun setSubtitleOffset(offsetMs: Long) = _state.update { it.copy(subtitleOffsetMs = offsetMs) }

    /**
     * Guesses the subtitle format from the URL, defaulting to SubRip.
     *
     * This used to be hardcoded to TEXT_VTT, which silently broke every provider that
     * serves anything else - OpenSubtitles v3, for one, returns application/x-subrip from
     * URLs with no file extension at all, so nothing it offered would ever have rendered.
     * SubRip is the right default: it is what the subtitle addons overwhelmingly serve,
     * and it is what an extensionless URL almost always turns out to be.
     */
    private fun subtitleMimeType(url: String): String {
        // Only look at the last path segment - hosts have dots in them too, so taking the
        // extension off the whole URL finds ".io" in "subs5.strem.io".
        val path = url.substringBefore('?').substringBefore('#')
        // Last *non-empty* segment: opensubtitles PRO serves ".../sub.vtt/?lang_code=en",
        // where a plain substringAfterLast('/') is the empty string after the trailing
        // slash - which would silently fall through to the SubRip default and fail to
        // parse a WebVTT file.
        val lastSegment = path.split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
        val extension = if (lastSegment.contains('.')) {
            lastSegment.substringAfterLast('.').lowercase()
        } else {
            ""
        }
        return when (extension) {
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    // ── Audio ────────────────────────────────────────────────────────────────

    fun selectAudioTrack(track: AudioTrackInfo) {
        _state.update { it.copy(selectedAudioTrack = track) }

        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()

        _audioHudText.value = track.name
        audioHudJob?.cancel()
        audioHudJob = viewModelScope.launch {
            delay(2000)
            _audioHudText.value = null
        }
    }

    // ── Skip / auto-next ─────────────────────────────────────────────────────

    fun skipIntro() {
        player.seekTo(90000L)
        dismissSkipIntro()
    }

    fun dismissSkipIntro() {
        isIntroDismissed = true
        _state.update { it.copy(showSkipIntro = false) }
    }

    fun cancelAutoNext() = _state.update { it.copy(showAutoNext = false) }

    // ── Torrent session ──────────────────────────────────────────────────────

    fun setTorrentLoading(loading: Boolean) = _state.update { it.copy(isTorrentLoading = loading) }
    fun setTorrentStream(isTorrent: Boolean) = _state.update { it.copy(isTorrentStream = isTorrent) }

    fun showTorrentFilePicker(files: List<TorrentFileInfo>) = _state.update {
        it.copy(torrentFiles = files, showTorrentFileSheet = true, isTorrentLoading = false)
    }

    fun dismissTorrentFilePicker() = _state.update { it.copy(showTorrentFileSheet = false) }

    fun onTorrentFilePicked(index: Int) = _state.update {
        it.copy(pickedTorrentFileIndex = index, showTorrentFileSheet = false,
            isTorrentLoading = true, isTorrentStream = true)
    }

    // ── Errors ───────────────────────────────────────────────────────────────

    fun showError(message: String) = _state.update { it.copy(errorMessage = message, isTorrentLoading = false) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }
    fun dismissSignIn() = _state.update { it.copy(signInHost = null) }

    /** Carry on from where the viewer left off. */
    fun resumeFromSaved() {
        val at = _state.value.resumePromptMs ?: return
        _state.update { it.copy(resumePromptMs = null) }
        player.seekTo(at)
        player.play()
    }

    /** Play from the beginning instead. */
    fun startOver() {
        _state.update { it.copy(resumePromptMs = null) }
        player.seekTo(0L)
        player.play()
    }

    fun retry() {
        // A manual retry gets its live attempts back, otherwise the button would reopen the
        // feed only to re-show the error the exhausted retries already produced.
        liveRetries = 0
        _state.update { it.copy(errorMessage = null, isTorrentLoading = false) }
        _retryTrigger.update { it + 1 }
    }

    fun handleTorrentError(failedSource: StreamSource) {
        val currentUrl = failedSource.url
        skippedUrls += currentUrl
        session.updateSourceHealth(currentUrl, com.ominix.vidiio.data.repository.SourceHealth(com.ominix.vidiio.data.repository.SourceStatus.DEAD, note = "Torrent metadata error"))

        val availableSources = session.streamSources.value
        val nextSource = availableSources.firstOrNull {
            it.url != currentUrl && !skippedUrls.contains(it.url) &&
                !com.ominix.vidiio.data.repository.SourceHeaviness.isHeavy(it.serverName)
        } ?: availableSources.firstOrNull { it.url != currentUrl && !skippedUrls.contains(it.url) }

        if (nextSource != null) {
            torrentPrepareRetries = 0
            selectSource(nextSource)
            viewModelScope.launch {
                android.widget.Toast.makeText(getApplication(), "Torrent failed, switching to ${nextSource.serverName}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            _state.update { it.copy(errorMessage = "Torrent Error: Failed to fetch metadata and no other sources available.", isTorrentLoading = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { session.saveWatchProgress(player.currentPosition, player.duration) }
        pollJob?.cancel()
        player.removeListener(playerListener)
        player.stop()
        player.release()
    }
}

/** How long the player waits for a source to verify before starting on the first listed one. */
private const val AUTO_PICK_GRACE_MS = 8_000L

/** Seconds the "Up next" countdown runs before the next episode starts by itself. */
private const val UP_NEXT_SECONDS = 10

/** How long before the end the "Up next" countdown begins when the picture gave no sign: ~5% of the episode, 30s to 2.5 min. */
internal fun creditsWindowMs(durationMs: Long): Long =
    (durationMs / 20).coerceIn(if (durationMs >= 20 * 60_000L) 30_000L else 20_000L, 150_000L)

/** How often the picture is checked for credits, once the episode is in its closing stretch. */
private const val CREDITS_SAMPLE_MS = 1_000L

/** An addon stream shorter than this is a placeholder clip, not the film. */
private const val PLACEHOLDER_MAX_MS = 75_000L

/** Saved progress shorter than this is not worth asking about; playback just starts from the beginning. */
private const val RESUME_PROMPT_MIN_MS = 30_000L

/** How many times a live feed that dropped is re-opened before the error is shown. */
private const val LIVE_RETRY_LIMIT = 3

/** Wait before each live retry; multiplied by the attempt number so a dead feed backs off. */
private const val LIVE_RETRY_DELAY_MS = 1_500L

/** How far behind the window end a resumed live feed may sit before it rejoins the edge. */
private const val LIVE_EDGE_SNAP_BEHIND_MS = 60_000L

/** Headroom a rejoin leaves at the window end: the newest segments may still be being written. */
private const val LIVE_EDGE_PADDING_MS = 30_000L
