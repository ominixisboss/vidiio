package com.ominix.vidiio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.repository.SourceHealth
import com.ominix.vidiio.data.repository.SourceStatus
import com.ominix.vidiio.ui.components.ErrorState
import com.ominix.vidiio.ui.components.MovieItem
import com.ominix.vidiio.ui.components.SourceHealthBadge
import com.ominix.vidiio.ui.components.tvFocusRing
import com.ominix.vidiio.ui.components.GlassCard
import com.ominix.vidiio.ui.components.RiveLoader
import com.ominix.vidiio.ui.viewmodel.DetailsUiState
import com.ominix.vidiio.ui.viewmodel.DetailsViewModel

/**
 * Sources whose check has not come back yet. A scrape can finish while its last sources are
 * still being probed, so "not searching" alone is not enough to say nothing works.
 */
private fun List<StreamSource>.anyStillChecking(health: Map<String, SourceHealth>): Boolean =
    any { source ->
        val status = health[source.url]?.status
        status == null || status == SourceStatus.CHECKING
    }

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (Movie, Episode?, StreamSource) -> Unit,
    autoPlaySeason: Int? = null,
    autoPlayEpisode: Int? = null,
    /** Set by a long-press Play on a poster: start the best stream as soon as it is found. */
    autoPlay: Boolean = false,
    /** The poster that was tapped, shown while the full details load. */
    initialMovie: Movie? = null,
    /** Opening a title from the "More like this" rail. */
    onMovieClick: (Movie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sourceHealth by viewModel.sourceHealth.collectAsState()
    val unavailableCount by viewModel.unavailableCount.collectAsState()
    val showUnavailable by viewModel.showUnavailable.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val similar by viewModel.similar.collectAsState()
    // Up from Play has nothing reliably above it to land on (the trailer sits there), so give
    // the remote a direct route to the top bar.
    val topFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Where the remote lands again once the finding overlay closes.
    val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var showSourceSelector by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.focusRequester(topFocus)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is DetailsUiState.Success) {
                        val isFavorite = (uiState as DetailsUiState.Success).isFavorite
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is DetailsUiState.Loading -> {
                    DetailsLoadingPlaceholder(
                        movie = initialMovie,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is DetailsUiState.Success -> {
                    val permissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState(
                        permissions = com.ominix.vidiio.utils.PermissionUtils.getRequiredPermissions()
                    )
                    // Picking an episode starts the best stream that works and is in English, rather than just
                    // listing links: the episode that was tapped, once its sources are found and checked.
                    var autoPlayEpisodeId by remember { mutableStateOf<String?>(null) }
                    // The episode whose search has actually been seen running. Until then, "not
                    // searching" only means the tap has not reached the scraper yet.
                    var episodeSearchSeen by remember { mutableStateOf<String?>(null) }
                    // Whether this wait ended by opening the player. Then the details page is on its
                    // way out and must not take focus back from it.
                    var waitStartedPlayback by remember { mutableStateOf(false) }
                    // Clearing the pending episode must clear its marker too: otherwise tapping the
                    // same episode again would look like its new search had already run, and it
                    // could give up before the scraper even started.
                    fun clearEpisodeWait() {
                        autoPlayEpisodeId = null
                        episodeSearchSeen = null
                    }
                    val toastContext = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(autoPlayEpisodeId, state.selectedEpisode?.id, state.streamSources, sourceHealth, state.isSearchingSources) {
                        val pending = autoPlayEpisodeId ?: return@LaunchedEffect
                        // Wait until the tap has taken effect, and the new search has actually begun - before
                        // that the list is empty and "not searching" only because it is between episodes.
                        if (state.selectedEpisode?.id != pending) return@LaunchedEffect
                        if (state.isSearchingSources) {
                            episodeSearchSeen = pending
                            return@LaunchedEffect
                        }
                        if (episodeSearchSeen != pending) return@LaunchedEffect
                        // Same wait as a long-press Play: the scrape can end while its last sources
                        // are still being probed, and the one that works can be seconds behind.
                        if (state.streamSources.anyStillChecking(sourceHealth)) return@LaunchedEffect
                        clearEpisodeWait()
                        val best = com.ominix.vidiio.data.repository.pickStreamToPlay(
                            state.streamSources, sourceHealth, searching = false, giveUp = false
                        )
                        if (best != null) {
                            waitStartedPlayback = true
                            onPlay(state.movie, state.selectedEpisode, best)
                        } else {
                            android.widget.Toast.makeText(
                                toastContext,
                                "No working stream found for this episode",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    // Opened from the TV Guide with an episode in mind: pick it and play the best stream.
                    var guideHandled by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(state.movie.seasons, guideHandled) {
                        if (guideHandled || autoPlaySeason == null || autoPlayEpisode == null || state.movie.seasons.isEmpty()) return@LaunchedEffect
                        guideHandled = true
                        val target = state.movie.seasons.firstOrNull { it.seasonNumber == autoPlaySeason }
                            ?.episodes?.firstOrNull { it.episodeNumber == autoPlayEpisode }
                        if (target != null) {
                            viewModel.selectEpisode(target)
                            episodeSearchSeen = null
                            autoPlayEpisodeId = target.id
                        } else {
                            android.widget.Toast.makeText(toastContext, "That episode is not listed yet", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    // A long-press Play on a poster lands here with autoPlay set: go straight to the
                    // best stream instead of showing a second Play button to press. The wait is shown
                    // over the details page, since there is nothing to do there until it ends.
                    var autoPlayWaiting by remember(autoPlay) {
                        mutableStateOf(autoPlay && !viewModel.autoPlayStarted)
                    }
                    LaunchedEffect(state.streamSources, state.isSearchingSources, sourceHealth) {
                        if (!autoPlay || viewModel.autoPlayStarted) {
                            autoPlayWaiting = false
                            return@LaunchedEffect
                        }
                        // The first source to pass its check is usually not the best one - the rest
                        // of the scrape is still adding and checking others - so only choose once
                        // the search has settled. Waiting on a timer instead gave up at 20s while
                        // the scrape still had up to 45s to run, and then did nothing at all.
                        if (state.isSearchingSources) return@LaunchedEffect
                        // The scrape ending is not the search ending: its last sources are probed in
                        // the background, and the one that works can land seconds later. Saying
                        // "no stream" now would be wrong just before one starts working.
                        if (state.streamSources.anyStillChecking(sourceHealth)) return@LaunchedEffect
                        autoPlayWaiting = false
                        viewModel.autoPlayStarted = true
                        val best = com.ominix.vidiio.data.repository.pickStreamToPlay(
                            state.streamSources, sourceHealth, searching = false, giveUp = false
                        )
                        if (best != null) {
                            waitStartedPlayback = true
                            onPlay(state.movie, state.selectedEpisode, best)
                        } else {
                            // Settled with nothing playable: say so, and leave the sources on screen.
                            android.widget.Toast.makeText(
                                toastContext,
                                "No working stream found",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val waitOverlay = autoPlayEpisodeId != null || autoPlayWaiting
                    val waitFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                    var waitHadFocus by remember { mutableStateOf(false) }
                    LaunchedEffect(waitOverlay) {
                        if (waitOverlay) {
                            waitHadFocus = true
                            waitStartedPlayback = false
                            // Focus has to be on the overlay, or the remote keeps driving the
                            // controls hidden underneath it. It is attached by now; retry once.
                            if (runCatching { waitFocus.requestFocus() }.isFailure) {
                                kotlinx.coroutines.delay(80)
                                runCatching { waitFocus.requestFocus() }
                            }
                        } else if (waitHadFocus) {
                            waitHadFocus = false
                            val playing = waitStartedPlayback
                            waitStartedPlayback = false
                            if (!playing) {
                                // The overlay took focus with it when it closed. Give it back to
                                // Play, or the top bar when there is no Play button to land on.
                                kotlinx.coroutines.delay(120)
                                if (runCatching { playFocus.requestFocus() }.isFailure) {
                                    runCatching { topFocus.requestFocus() }
                                }
                            }
                        }
                    }
                    androidx.activity.compose.BackHandler(enabled = waitOverlay) {
                        if (autoPlayEpisodeId != null) {
                            clearEpisodeWait()
                        } else {
                            // Stop waiting, and stay on the details page where the sources are.
                            autoPlayWaiting = false
                            viewModel.autoPlayStarted = true
                        }
                    }

                    MovieDetailContent(
                        movie = state.movie,
                        streamSources = state.streamSources,
                        sourceHealth = sourceHealth,
                        unavailableCount = unavailableCount,
                        showUnavailable = showUnavailable,
                        onShowUnavailableChange = viewModel::setShowUnavailable,
                        selectedEpisode = state.selectedEpisode,
                        isSearchingSources = state.isSearchingSources,
                        sourcesNote = state.sourcesNote,
                        similar = similar,
                        onSimilarClick = onMovieClick,
                        onEpisodeSelected = { episode ->
                            viewModel.selectEpisode(episode)
                            // A new tap has not been searched yet, even if it is the same episode
                            // that was just tried and failed.
                            episodeSearchSeen = null
                            autoPlayEpisodeId = episode.id
                        },
                        onPlayClick = { source -> onPlay(state.movie, state.selectedEpisode, source) },
                        onDownloadClick = { source ->
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                            viewModel.downloadSource(source)
                        },
                        onToggleSourceSelector = { showSourceSelector = true },
                        onFocusTop = { runCatching { topFocus.requestFocus() } },
                        playFocus = playFocus
                    )
                    if (waitOverlay) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .pointerInput(Unit) { }
                                .focusRequester(waitFocus)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    // Nothing hidden under the overlay may fire while it waits:
                                    // swallow the remote, including the directions that would
                                    // move focus back into the page. Back stays with BackHandler.
                                    event.key != Key.Back
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(16.dp))
                                Text("Finding the best stream...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("Back to cancel", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                is DetailsUiState.Error -> ErrorState(
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    if (showSourceSelector && uiState is DetailsUiState.Success) {
        SourceSelectorBottomSheet(
            onDismiss = { showSourceSelector = false },
            viewModel = viewModel
        )
    }
}

/**
 * The first frame after tapping a poster: show that poster and title immediately, with the
 * spinner in place of the controls, instead of a blank page that fills in a second later.
 */
@Composable
private fun DetailsLoadingPlaceholder(movie: Movie?, modifier: Modifier = Modifier) {
    if (movie == null) {
        Box(modifier, contentAlignment = Alignment.Center) { RiveLoader() }
        return
    }
    Box(modifier) {
        AsyncImage(
            model = movie.backdropUrl ?: movie.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 240f
                    )
                )
        )
        // In landscape the bottom of this box is behind the navigation bar, so the title goes to
        // the top there instead of disappearing under it while the details load.
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val compactLandscape = configuration.screenWidthDp > configuration.screenHeightDp &&
            configuration.screenHeightDp < 420
        Column(
            modifier = Modifier
                .align(if (compactLandscape) Alignment.TopStart else Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = movie.title,
                style = if (compactLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Loading details\u2026",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MovieDetailContent(
    movie: Movie,
    streamSources: List<StreamSource>,
    sourceHealth: Map<String, SourceHealth>,
    unavailableCount: Int,
    showUnavailable: Boolean,
    onShowUnavailableChange: (Boolean) -> Unit,
    selectedEpisode: Episode?,
    isSearchingSources: Boolean,
    /** Why addon sources are missing, when there is something to say (PenguPlay's sign-in clip). */
    sourcesNote: String? = null,
    /** "More like this": similar titles, empty until they load or when there are none. */
    similar: List<Movie> = emptyList(),
    onSimilarClick: (Movie) -> Unit = {},
    onEpisodeSelected: (Episode) -> Unit,
    onPlayClick: (StreamSource) -> Unit,
    onDownloadClick: (StreamSource) -> Unit,
    onToggleSourceSelector: () -> Unit,
    onFocusTop: () -> Unit = {},
    /** Focused again when the finding overlay closes, so the remote is not left with nothing. */
    playFocus: androidx.compose.ui.focus.FocusRequester? = null
) {
    var selectedSeasonIndex by remember(movie.id) {
        mutableIntStateOf(movie.seasons.indexOfFirst { it.seasonNumber == selectedEpisode?.seasonNumber }.coerceAtLeast(0))
    }
    var trailerMuted by remember(movie.id) { mutableStateOf(true) }
    // The inline trailer is a WebView, the heaviest thing on this page. On low-memory devices (1 GB TV
    // sticks) the system kills it and the viewer sees a browser crash page, so offer the external
    // Trailer button there instead of autoplaying.
    val lowRamDevice = androidx.compose.ui.platform.LocalContext.current.let { c ->
        (c.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.let { it.isLowRamDevice || it.memoryClass <= 192 } == true
    }
    var trailerFailed by remember(movie.id) { mutableStateOf(lowRamDevice) }
    var trailerFullscreen by remember(movie.id) { mutableStateOf(false) }
    var showPlayChooser by remember(movie.id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // A phone in landscape is about 360dp tall. The 400dp hero then filled the whole first screen
    // by itself, so the title and Play sat below the fold: the compact layout replaces it with a
    // poster-and-title strip that leaves the Play button on the first screen.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val compactLandscape = configuration.screenWidthDp > configuration.screenHeightDp &&
        configuration.screenHeightDp < 420
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val trailerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Pause the inline trailer once the header scrolls (mostly) out of view.
    val headerVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 700
        }
    }

    val trailerKey = movie.trailerKey
    if (trailerFullscreen && trailerKey != null) {
        Dialog(
            onDismissRequest = { trailerFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { trailerFullscreen = false }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                com.ominix.vidiio.ui.components.TrailerPlayer(
                    videoId = trailerKey,
                    playing = true,
                    muted = false,
                    controls = true,
                    interactive = true,
                    startMuted = false,
                    fit = com.ominix.vidiio.ui.components.TrailerFit.CONTAIN,
                    modifier = Modifier.fillMaxSize(),
                    onUnavailable = { trailerFailed = true; trailerFullscreen = false }
                )
                IconButton(
                    onClick = { trailerFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            if (compactLandscape) {
                CompactDetailsHeader(
                    movie = movie,
                    hasTrailer = trailerKey != null,
                    onOpenTrailer = {
                        // The embedded player is the nicer option; when this device cannot run it,
                        // fall back to the same external trailer the full hero offers.
                        if (trailerFailed) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                    )
                                )
                            }
                        } else {
                            trailerFullscreen = true
                        }
                    }
                )
                return@item
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                val trailerActive = trailerKey != null && !trailerFailed
                if (trailerActive) {
                    com.ominix.vidiio.ui.components.TrailerPlayer(
                        videoId = trailerKey!!,
                        playing = headerVisible && !trailerFullscreen,
                        muted = trailerMuted,
                        modifier = Modifier.fillMaxSize(),
                        onUnavailable = { trailerFailed = true }
                    )
                } else {
                    AsyncImage(
                        model = movie.backdropUrl ?: movie.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 300f
                            )
                        )
                )
                if (trailerActive) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { trailerMuted = !trailerMuted },
                            modifier = Modifier.focusRequester(trailerFocus).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = if (trailerMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (trailerMuted) "Unmute trailer" else "Mute trailer",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { trailerFullscreen = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen trailer", tint = Color.White)
                        }
                    }
                } else if (trailerKey != null) {
                    // Trailer can't be embedded — offer to open it externally.
                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Trailer")
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        movie.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        ) {
                            Text(
                                text = "HD",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        item {
            // The compact layout has about 168dp between the top bar and the bottom bar, so the
            // Play button gets a tighter frame there to keep it on the first screen.
            Column(modifier = Modifier.padding(if (compactLandscape) 8.dp else 16.dp)) {
                if (streamSources.isNotEmpty()) {
                    val playShape = RoundedCornerShape(24.dp)
                    val playHeight = if (compactLandscape) 48.dp else 56.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlayClick(streamSources.first()) },
                            modifier = Modifier
                                .weight(1f)
                                .height(playHeight)
                                .then(playFocus?.let { Modifier.focusRequester(it) } ?: Modifier)
                                .tvFocusRing(playShape)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        // Scroll back up to the trailer and put focus on its controls; with no
                                        // trailer there, fall back to the top bar.
                                        scope.launch {
                                            listState.scrollToItem(0)
                                            kotlinx.coroutines.delay(150)
                                            runCatching { trailerFocus.requestFocus() }.onFailure { onFocusTop() }
                                        }
                                        true
                                    } else false
                                },
                            shape = playShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (movie.type == MovieType.TV_SHOW && selectedEpisode != null) "Play S${selectedEpisode.seasonNumber}:E${selectedEpisode.episodeNumber}" else "Play",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (streamSources.size > 1) {
                            Spacer(Modifier.width(8.dp))
                            // More than one source: the choice belongs next to Play, not only in the
                            // list below, so a remote never has to scroll to change it.
                            FilledIconButton(
                                onClick = { showPlayChooser = true },
                                modifier = Modifier
                                    .size(playHeight)
                                    .tvFocusRing(playShape),
                                shape = playShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose a source")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                movie.synopsis?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Seasons and episodes come first: the links below are for the episode picked here.
        if (movie.type == MovieType.TV_SHOW && movie.seasons.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedSeasonIndex,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    movie.seasons.forEachIndexed { index, season ->
                        Tab(
                            selected = selectedSeasonIndex == index,
                            onClick = { selectedSeasonIndex = index },
                            text = {
                                Text(
                                    season.name ?: "Season ${season.seasonNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            val currentSeason = movie.seasons.getOrNull(selectedSeasonIndex)
            if (currentSeason != null) {
                items(currentSeason.episodes.size) { index ->
                    val episode = currentSeason.episodes[index]
                    EpisodeListItem(
                        episode = episode,
                        isSelected = episode.id == selectedEpisode?.id,
                        onClick = { onEpisodeSelected(episode) }
                    )
                }
            }
        }

        if (similar.isNotEmpty()) {
            item(key = "more-like-this") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "More like this",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(similar) { other ->
                            MovieItem(
                                movie = other,
                                onClick = { onSimilarClick(other) },
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (isSearchingSources) {
                    RiveLoader(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (unavailableCount > 0) {
                    TextButton(onClick = { onShowUnavailableChange(!showUnavailable) }) {
                        Text(
                            text = if (showUnavailable) "Hide not working" else "Show not working ($unavailableCount)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                IconButton(onClick = onToggleSourceSelector) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Select Sources", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        if (sourcesNote != null) {
            item(key = "sources-note") {
                Text(
                    text = sourcesNote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
        }

        if (streamSources.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (isSearchingSources) {
                        RiveLoader(modifier = Modifier.size(100.dp))
                    } else {
                        Text(text = "No sources found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(streamSources.size) { index ->
                val source = streamSources[index]
                SourceItem(
                    source = source,
                    health = sourceHealth[source.url],
                    onClick = { onPlayClick(source) },
                    onDownload = { onDownloadClick(source) }
                )
            }
        }

        if (compactLandscape) {
            // The bottom bar is drawn over this screen, so the last source needs room to scroll
            // clear of it instead of sitting underneath.
            item { Spacer(Modifier.height(104.dp)) }
        }
    }

    if (showPlayChooser) {
        PlaySourceSheet(
            sources = streamSources,
            sourceHealth = sourceHealth,
            onDismiss = { showPlayChooser = false },
            onPlay = { source ->
                showPlayChooser = false
                onPlayClick(source)
            },
            onDownload = onDownloadClick
        )
    }
}

/**
 * Pick the source Play should use without scrolling down to the list. The rows are the same
 * focusable glass cards as the list, health badge and download button included, so a remote can
 * work the whole sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaySourceSheet(
    sources: List<StreamSource>,
    sourceHealth: Map<String, SourceHealth>,
    onDismiss: () -> Unit,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Choose a source",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Play uses the best match. Pick another one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(sources.size) { index ->
                val source = sources[index]
                SourceItem(
                    source = source,
                    health = sourceHealth[source.url],
                    onClick = { onPlay(source) },
                    onDownload = { onDownload(source) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * The landscape-phone header. There is only about 168dp between the top bar and the bottom bar,
 * which the full-bleed hero filled by itself, so the artwork, title and meta share one strip and
 * leave the Play button on the first screen. The trailer moves to a button on the artwork that
 * opens the existing fullscreen player.
 */
@Composable
private fun CompactDetailsHeader(
    movie: Movie,
    hasTrailer: Boolean,
    onOpenTrailer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = movie.backdropUrl ?: movie.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (hasTrailer) {
                IconButton(
                    onClick = onOpenTrailer,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        Icons.Rounded.PlayCircle,
                        contentDescription = "Play trailer",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                // One line, so a long title can never grow the strip and push Play off screen.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                movie.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "HD",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectorBottomSheet(
    onDismiss: () -> Unit,
    viewModel: DetailsViewModel
) {
    // Fully expanded: in landscape the half-height resting state cut the second source off.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val enabledSources by viewModel.enabledSources.collectAsState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Select Sources",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            val sources = listOf(
                "knaben" to "Knaben",
                "tg" to "TorrentGalaxy"
            )
            
            LazyColumn {
                items(sources.size) { index ->
                    val (id, name) = sources[index]
                    val isEnabled = enabledSources.contains(id)
                    
                    GlassCard(
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { viewModel.toggleSource(id) },
                        alpha = 0.05f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = { viewModel.toggleSource(id) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SourceItem(
    source: StreamSource,
    health: SourceHealth?,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        alpha = 0.1f,
        borderAlpha = 0.15f,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                source.url.startsWith("magnet:") -> Icons.Rounded.CloudDownload
                else -> Icons.Rounded.PlayCircle
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.serverName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.sourceName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    source.quality?.let {
                        Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    source.size?.let {
                        Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    source.seeders?.let {
                        Text(text = " • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "👥 $it", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
                SourceHealthBadge(health = health, modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: Episode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        alpha = if (isSelected) 0.15f else 0.05f,
        borderAlpha = if (isSelected) 0.3f else 0.1f,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray)
            ) {
                if (episode.stillPath != null) {
                    AsyncImage(
                        model = episode.stillPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f
                            )
                        )
                )
                Text(
                    text = "${episode.episodeNumber}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                episode.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
