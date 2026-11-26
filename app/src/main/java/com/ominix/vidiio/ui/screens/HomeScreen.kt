package com.ominix.vidiio.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.TOP10_LABEL
import com.ominix.vidiio.data.model.WatchProgress
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.ui.components.ErrorState
import com.ominix.vidiio.ui.components.RiveLoader
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.components.tvFocusRing
import com.ominix.vidiio.ui.tv.LocalIsTv
import com.ominix.vidiio.ui.browse.LocalBrowseShortcuts
import com.ominix.vidiio.ui.browse.ShortcutButtons
import androidx.compose.foundation.layout.statusBarsPadding
import com.ominix.vidiio.ui.viewmodel.HomeUiState
import com.ominix.vidiio.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetails: (Movie) -> Unit,
    onSearchQuery: (String) -> Unit = {},
    onResumeWatching: (WatchProgress) -> Unit = {},
    onOpenMovies: () -> Unit = {},
    onOpenTv: () -> Unit = {},
    onOpenAnime: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenCollection: (String, Int) -> Unit = { _, _ -> },
    /** Long-press "Play": open the title and start its best stream. */
    onPlay: (Movie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val homeStyle by viewModel.homeStyle.collectAsState()
    val showVpnReminder by viewModel.showVpnReminder.collectAsState()
    var vpnDialogDismissed by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Movie?>(null) }

    val scrollState = rememberLazyListState()

    val themeAccent = MaterialTheme.colorScheme.primary
    val spec = remember(homeStyle, themeAccent) { homeStyle.spec(themeAccent) }
    val background = spec.background ?: MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize().background(background)) {
        if (spec.style == HomeStyle.VIDIIO || spec.style == HomeStyle.FLIX) {
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .offset(x = 120.dp, y = (-40).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                spec.accent.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .offset(x = (-80).dp, y = 350.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                spec.accent.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        val heroScope = androidx.compose.runtime.rememberCoroutineScope()
        val isRefreshing by viewModel.isRefreshing.collectAsState()

        when (val state = uiState) {
            is HomeUiState.Loading -> {
                HomeSkeletonLoading(spec)
            }
            is HomeUiState.Success -> {
                val heroMovies = remember(state.categories) {
                    state.categories.firstOrNull()?.movies?.take(5) ?: emptyList()
                }
                val rows = remember(state.categories) {
                    state.categories.filter { it.movies.isNotEmpty() }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(isPullToRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (spec.style == HomeStyle.FLIX) {
                        val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.ominix.vidiio.VidiioApplication
                        val moviesVm: com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            key = "browse_movies",
                            factory = com.ominix.vidiio.ui.viewmodel.VidiioViewModelFactories.browse(app, com.ominix.vidiio.data.repository.BrowseKind.MOVIES)
                        )
                        val tvVm: com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            key = "browse_tv",
                            factory = com.ominix.vidiio.ui.viewmodel.VidiioViewModelFactories.browse(app, com.ominix.vidiio.data.repository.BrowseKind.TV)
                        )
                        val movies by moviesVm.state.collectAsState()
                        val tv by tvVm.state.collectAsState()
                        // Shelves the Flix layout already draws itself; what is left of the home catalogues follows.
                        val covered = setOf("Trending Movies", "Popular Movies", TOP10_LABEL)
                        val shelves = remember(movies, tv, rows) {
                            buildList {
                                add(FlixShelf("Today's Hot Movies", movies.trending.orEmpty(), onLoadMore = { moviesVm.loadMore(com.ominix.vidiio.data.repository.BrowseList.TRENDING) }))
                                add(FlixShelf("Top Rated Movies", movies.topRated.orEmpty(), onLoadMore = { moviesVm.loadMore(com.ominix.vidiio.data.repository.BrowseList.TOP_RATED) }))
                                add(FlixShelf("Latest Uploaded Movies", movies.fresh.orEmpty(), onLoadMore = { moviesVm.loadMore(com.ominix.vidiio.data.repository.BrowseList.FRESH) }))
                                val tvLatest = if (tv.soon.orEmpty().isNotEmpty()) com.ominix.vidiio.data.repository.BrowseList.SOON else com.ominix.vidiio.data.repository.BrowseList.FRESH
                                add(FlixShelf("Latest TV Updates", tv.soon.orEmpty().ifEmpty { tv.fresh.orEmpty() }, todayRibbon = true, onLoadMore = { tvVm.loadMore(tvLatest) }))
                                add(FlixShelf("Popular TV Shows", tv.popular.orEmpty(), onLoadMore = { tvVm.loadMore(com.ominix.vidiio.data.repository.BrowseList.POPULAR) }))
                                rows.filter { row -> row.name !in covered && row.name != "Popular TV" && row.name != "Popular TV Shows" &&
                                    com.ominix.vidiio.data.repository.BrowseOptions.services.none { svc -> row.name.split(" ").filter { it.isNotBlank() }.joinToString(" ").equals(svc.label, ignoreCase = true) } }
                                    .forEach { add(FlixShelf(it.name, it.movies)) }
                            }
                        }
                        FlixHomeContent(
                            heroMovies = movies.trending.orEmpty().filter { !it.backdropUrl.isNullOrBlank() }.take(5),
                            shelves = shelves,
                            continueWatching = continueWatching,
                            spec = spec,
                            listState = scrollState,
                            onOpen = onNavigateToDetails,
                            onResume = onResumeWatching,
                            onRemove = { viewModel.removeContinueWatching(it) },
                            onOpenMovies = onOpenMovies,
                            onOpenTv = onOpenTv,
                            onOpenGuide = onOpenGuide,
                            onOpenCollection = onOpenCollection,
                        )
                    } else LazyColumn(
                        state = scrollState,
                        // Clear the navigation bar, which sits over the last shelf in landscape.
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(spec.rowGap)
                    ) {
                        if (heroMovies.isNotEmpty()) {
                            item(key = "hero") {
                                // Focus scrolling can stop short of the top; once the hero is focused, snap there.
                                androidx.compose.foundation.layout.Box(
                                    Modifier.onFocusChanged { focus ->
                                        if (focus.hasFocus) heroScope.launch {
                                            kotlinx.coroutines.delay(150)
                                            scrollState.scrollToItem(0)
                                        }
                                    }
                                ) {
                                    if (spec.heroKind == HeroKind.CAROUSEL) {
                                        HeroCarousel(heroMovies, spec, onNavigateToDetails)
                                    } else {
                                        HeroStatic(heroMovies.first(), spec, onNavigateToDetails)
                                    }
                                }
                            }
                        }

                        item(key = "categories") {
                            CategoryLinks(spec = spec, onOpenMovies = onOpenMovies, onOpenTv = onOpenTv, onOpenAnime = onOpenAnime, onOpenGuide = onOpenGuide)
                        }
                        if (continueWatching.isNotEmpty()) {
                            item {
                                ContinueWatchingRow(
                                    entries = continueWatching,
                                    spec = spec,
                                    onResume = onResumeWatching,
                                    onRemove = { viewModel.removeContinueWatching(it) }
                                )
                            }
                        }
                        items(rows) { category ->
                            when {
                                category.name == TOP10_LABEL && spec.showTop10 ->
                                    Top10Section(category.movies, spec, onNavigateToDetails)
                                category.name == TOP10_LABEL -> Unit // hidden for this style
                                else -> CategorySection(
                                    category.name, category.movies, spec, onNavigateToDetails,
                                    onMovieLongClick = { actionsFor = it },
                                    onLoadMore = if (viewModel.canLoadMore(category.name)) ({ viewModel.loadMore(category.name) }) else null
                                )
                            }
                        }
                    }
                }
            }
            is HomeUiState.Error -> ErrorState(
                onRetry = { viewModel.refresh() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LocalBrowseShortcuts.current?.takeIf { !LocalIsTv.current }?.let { shortcuts ->
            ShortcutButtons(
                shortcuts,
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 8.dp, top = 4.dp)
            )
        }

        if (showVpnReminder && !vpnDialogDismissed) {
            VpnReminderDialog(
                onDismiss = { dontShowAgain ->
                    vpnDialogDismissed = true
                    viewModel.dismissVpnReminder(dontShowAgain)
                }
            )
        }

        actionsFor?.let { movie ->
            com.ominix.vidiio.ui.components.PosterActionsSheet(
                movie = movie,
                onDismiss = { actionsFor = null },
                onPlay = onPlay,
                onDetails = onNavigateToDetails
            )
        }
    }
}

@Composable
fun VpnReminderDialog(
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val confirmFocus = remember { FocusRequester() }
    // On a TV nothing was focused when the reminder appeared: the remote looked dead until a key
    // was pressed, and after dismissing it the page had no highlighted control either. Start on
    // the confirmation button so the remote always has somewhere to act.
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(150)
            runCatching { confirmFocus.requestFocus() }
        }
    }

    // A landscape phone only leaves a dialog about 250dp before the system bars, and the stock
    // AlertDialog gave the body a single line at 1.5x fonts. Size this one explicitly and keep the
    // icon beside the title, so the body gets a real viewport instead of collapsing.
    val dialogHeight = minOf(
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.85f,
        340f
    ).coerceAtLeast(220f).dp
    // Height left for the title and body once the action row and padding are accounted for. The
    // dialog wraps to its content, so a short message keeps it compact and only a tall one scrolls.
    val maxScrollHeight = (dialogHeight - 108.dp).coerceAtLeast(80.dp)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = dialogHeight),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // The title scrolls with the body: at 1.5x fonts it wraps to two lines, and keeping
                // it outside a fixed viewport made the content overflow the dialog's height cap.
                Column(
                    modifier = Modifier
                        .heightIn(max = maxScrollHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "VPN & Privacy Reminder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "For your privacy and security while streaming content, we strongly recommend using an active VPN or configuring a SOCKS5/HTTP Proxy in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Keep the option beside the button: inside the scroll area it disappeared below
                // the fold at 1.5x fonts, leaving no hint that it existed.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { dontShowAgain = !dontShowAgain }
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Don't show again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = { onDismiss(dontShowAgain) },
                        modifier = if (isTv) Modifier.focusRequester(confirmFocus) else Modifier,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("I Understand", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeSkeletonLoading(spec: HomeStyleSpec) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(spec.rowGap)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(spec.heroHeight)
                .background(Color.White.copy(alpha = alpha))
        )
        repeat(2) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .width(spec.cardWidth)
                                .height(spec.cardHeight)
                                .clip(RoundedCornerShape(spec.cardCorner))
                                .background(Color.White.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------- App Customizations ----------------------------- */

@Composable
fun DisneyBrandHubsRow(onBrandClick: (String) -> Unit = {}) {
    val brands = listOf(
        "DISNEY" to listOf(Color(0xFF0038A8), Color(0xFF001552)),
        "PIXAR" to listOf(Color(0xFF1E2640), Color(0xFF0D1222)),
        "MARVEL" to listOf(Color(0xFF8B0000), Color(0xFF4A0000)),
        "STAR WARS" to listOf(Color(0xFF1F2937), Color(0xFF0B0F19)),
        "NAT GEO" to listOf(Color(0xFF5A4800), Color(0xFF261D00))
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(brands) { (name, colors) ->
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .width(138.dp)
                    .height(74.dp)
                    .tvClickable(
                        onClick = { onBrandClick(name) },
                        shape = shape,
                        focusScale = 1.1f
                    )
                    .background(Brush.verticalGradient(colors = colors), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), shape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun HeroTagBadge(spec: HomeStyleSpec) {
    Unit
}

/* ----------------------------- Hero variants ----------------------------- */

@Composable
private fun HeroButtons(movie: Movie, spec: HomeStyleSpec, center: Boolean, onClick: (Movie) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val playBg = spec.accent
        val playText = Color.White

        Button(
            onClick = { onClick(movie) },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = playBg, contentColor = playText),
            modifier = (if (center) Modifier.weight(1f).height(48.dp) else Modifier.height(48.dp))
                .tvFocusRing(RoundedCornerShape(24.dp))
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(12.dp))

        Button(
            onClick = { onClick(movie) },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray.copy(alpha = 0.75f),
                contentColor = Color.White
            ),
            modifier = (if (center) Modifier.weight(1f) else Modifier)
                .height(48.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .tvFocusRing(RoundedCornerShape(24.dp))
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Info", fontWeight = FontWeight.Bold)
        }
    }
}

/** Backdrop image with a slow, looping zoom + pan (Ken Burns). */
@Composable
fun KenBurnsImage(model: Any?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "kenburns")
    val scale by t.animateFloat(
        initialValue = 1f, targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kbScale"
    )
    val pan by t.animateFloat(
        initialValue = -0.035f, targetValue = 0.035f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse),
        label = "kbPan"
    )
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = size.width * pan
        }
    )
}

@Composable
private fun heroScrim(bottom: Color) = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.35f),
        Color.Transparent,
        bottom.copy(alpha = 0.85f),
        bottom
    )
)

@Composable
fun HeroCarousel(movies: List<Movie>, spec: HomeStyleSpec, onMovieClick: (Movie) -> Unit) {
    val isTv = LocalIsTv.current
    val pagerState = rememberPagerState(pageCount = { movies.size })
    val scrim = spec.background ?: Color.Black

    LaunchedEffect(pagerState.isScrollInProgress, movies.size) {
        // Never auto-advance under a remote: the hero changing by itself while you navigate moves
        // the page out from under the cursor.
        if (!isTv && !pagerState.isScrollInProgress && movies.isNotEmpty()) {
            while (true) {
                delay(6000)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % movies.size)
            }
        }
    }

    // Keep the title and buttons above the bottom bar on a short landscape phone; at the fixed
    // 460-500dp height they were entirely below the first screenful.
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val compact = !isTv && screenHeight < 420
    val heroHeight = if (compact) (screenHeight - 104).coerceAtLeast(220).dp else spec.heroHeight
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val movie = movies[page]
            Box(Modifier.fillMaxSize().tvClickable(onClick = { onMovieClick(movie) }, enabled = !isTv)) {
                if (spec.kenBurns && !isTv) {
                    KenBurnsImage(movie.backdropUrl ?: movie.posterUrl, Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = movie.backdropUrl ?: movie.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(Modifier.fillMaxSize().background(heroScrim(scrim)))
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HeroTagBadge(spec)
                    Text(
                        text = movie.title,
                        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    HeroButtons(movie, spec, center = true, onClick = onMovieClick)
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(movies.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) spec.accent else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
fun HeroStatic(movie: Movie, spec: HomeStyleSpec, onMovieClick: (Movie) -> Unit) {
    val isTv = LocalIsTv.current
    val scrim = spec.background ?: Color.Black
    val inset = spec.heroKind == HeroKind.SPOTLIGHT
    val corner = if (inset) 20.dp else 0.dp

    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val compact = !isTv && screenHeight < 420
    val heroHeight = if (compact) (screenHeight - 104).coerceAtLeast(220).dp else spec.heroHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .then(if (inset) Modifier.padding(horizontal = 12.dp, vertical = 8.dp) else Modifier)
            .tvClickable(onClick = { onMovieClick(movie) }, shape = RoundedCornerShape(corner), enabled = !isTv)
    ) {
        if (spec.kenBurns) {
            KenBurnsImage(movie.backdropUrl ?: movie.posterUrl, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = movie.backdropUrl ?: movie.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(heroScrim(scrim)))

        val alignment = if (spec.heroCenterText) Alignment.BottomCenter else Alignment.BottomStart
        Column(
            modifier = Modifier
                .align(alignment)
                .padding(bottom = if (spec.heroKind == HeroKind.COMPACT) 20.dp else 32.dp),
            horizontalAlignment = if (spec.heroCenterText) Alignment.CenterHorizontally else Alignment.Start
        ) {
            HeroTagBadge(spec)
            Text(
                text = movie.title,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = if (spec.heroCenterText) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            movie.synopsis?.takeIf { spec.heroKind == HeroKind.BANNER }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(0.85f)
                )
            }
            Spacer(Modifier.height(14.dp))
            if (spec.heroKind == HeroKind.COMPACT) {
                Button(
                    onClick = { onMovieClick(movie) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = spec.accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp).height(46.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            } else {
                HeroButtons(movie, spec, center = spec.heroCenterText, onClick = onMovieClick)
            }
        }
    }
}

/* ----------------------------- Rows ----------------------------- */

/** A row's title with optional See All arrow button. */
@Composable
private fun RowHeader(
    text: String,
    spec: HomeStyleSpec,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (spec.uppercaseHeaders) text.uppercase() else text,
            fontSize = spec.rowHeaderSize,
            fontWeight = spec.rowHeaderWeight,
            letterSpacing = if (spec.uppercaseHeaders) 1.sp else 0.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .tvClickable(onClick = onSeeAll, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "See all",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "See all $text",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun HomeCard(
    movie: Movie,
    spec: HomeStyleSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    // No entrance animation with a remote: cards fading and sliding in as you scroll is motion
    // on top of the scroll itself, and reads as the page shaking.
    val isTv = LocalIsTv.current
    var appeared by remember { mutableStateOf(!spec.staggerIn || isTv) }
    LaunchedEffect(Unit) { appeared = true }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(350),
        label = "cardAppear"
    )
    val shape = RoundedCornerShape(spec.cardCorner)

    Column(modifier = modifier.width(spec.cardWidth)) {
        Box(
            modifier = Modifier
                .width(spec.cardWidth)
                .height(spec.cardHeight)
                .graphicsLayer { alpha = appear }
                .tvClickable(onClick = onClick, onLongClick = onLongClick, shape = shape, focusScale = 1.08f)
        ) {
            AsyncImage(
                model = if (spec.card == CardKind.LANDSCAPE) (movie.backdropUrl ?: movie.posterUrl) else movie.posterUrl,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (spec.ratingBadge) {
                movie.rating?.takeIf { it > 0.0 }?.let { rating ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = spec.accent, modifier = Modifier.size(11.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }
        if (spec.cardShowTitle) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).width(spec.cardWidth)
            )
        }
    }
}

@Composable
fun CategorySection(
    name: String,
    movies: List<Movie>,
    spec: HomeStyleSpec,
    onMovieClick: (Movie) -> Unit,
    /** Long press on a poster: play, My List, details. */
    onMovieLongClick: ((Movie) -> Unit)? = null,
    /** Set for rows that can fetch more titles; called as the row nears its end. */
    onLoadMore: (() -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        RowHeader(name, spec, onSeeAll = onSeeAll)
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        com.ominix.vidiio.ui.components.LoadMoreOnScroll(rowState, movies.size, onLoadMore)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                HomeCard(
                    movie = movie,
                    spec = spec,
                    onClick = { onMovieClick(movie) },
                    onLongClick = onMovieLongClick?.let { long -> { long(movie) } }
                )
            }
        }
    }
}

@Composable
fun Top10Section(
    movies: List<Movie>,
    spec: HomeStyleSpec,
    onMovieClick: (Movie) -> Unit
) {
    Column {
        RowHeader("Top 10 This Week", spec)
        LazyRow(
            contentPadding = PaddingValues(start = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(movies.take(10)) { index, movie ->
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Black,
                        color = spec.accent.copy(alpha = 0.4f),
                        letterSpacing = (-4).sp
                    )
                    HomeCard(movie, spec, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingRow(
    entries: List<WatchProgress>,
    spec: HomeStyleSpec,
    onResume: (WatchProgress) -> Unit,
    onRemove: (String) -> Unit
) {
    Column {
        RowHeader("Continue Watching", spec)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { entry ->
                val shape = RoundedCornerShape(spec.cardCorner.coerceAtLeast(8.dp))
                Column(modifier = Modifier.width(240.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(135.dp)
                            .tvClickable(
                                onClick = { onResume(entry) },
                                onLongClick = { onRemove(entry.id) },
                                shape = shape
                            )
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), shape)
                    ) {
                        AsyncImage(
                            model = entry.backdropUrl ?: entry.posterUrl,
                            contentDescription = entry.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "${(entry.progressFraction * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            Icons.Rounded.PlayCircleFilled,
                            contentDescription = "Resume",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(44.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemove(entry.id) }
                                .padding(3.dp)
                        ) {
                            Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        LinearProgressIndicator(
                            progress = { entry.progressFraction },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                            color = spec.accent,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    entry.episodeLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Category chips under the hero. "All" is this page; the others open the dedicated Movies, TV
 * Shows and Anime pages, so Home itself stays a short, curated front page instead of a wall of
 * every shelf.
 */
@Composable
private fun CategoryLinks(
    spec: HomeStyleSpec,
    onOpenMovies: () -> Unit,
    onOpenTv: () -> Unit,
    onOpenAnime: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    // The same strip the Flix layout uses, so every Home style shares one main menu.
    com.ominix.vidiio.ui.browse.FlixTabs(
        selected = 0,
        labels = listOf("Featured", "Movies", "TV Shows", "TV Guide"),
        onSelect = { index ->
            when (index) {
                1 -> onOpenMovies()
                2 -> onOpenTv()
                3 -> onOpenGuide()
            }
        }
    )
}
