package com.ominix.vidiio.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.ominix.vidiio.VidiioApplication
import com.ominix.vidiio.navigation.Navigator
import com.ominix.vidiio.navigation.VidiioRoute
import com.ominix.vidiio.navigation.rememberNavigationState
import com.ominix.vidiio.navigation.toEntries
import com.ominix.vidiio.data.model.toMovie
import com.ominix.vidiio.ui.screens.*
import com.ominix.vidiio.ui.viewmodel.VidiioViewModelFactories
import com.ominix.vidiio.ui.pointer.VirtualPointerOverlay
import com.ominix.vidiio.ui.tv.LocalIsTv
import com.ominix.vidiio.ui.browse.BrowseShortcuts
import com.ominix.vidiio.ui.browse.LocalBrowseShortcuts
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.ui.tv.ProvideTvScrolling
import com.ominix.vidiio.ui.tv.TvNavItem
import com.ominix.vidiio.ui.tv.TvNavScaffold
import com.ominix.vidiio.ui.tv.isTvDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VidiioApp() {
    val navigationState = rememberNavigationState(
        startRoute = VidiioRoute.Splash,
        topLevelRoutes = setOf(VidiioRoute.Home, VidiioRoute.Movies, VidiioRoute.TvShows, VidiioRoute.Anime, VidiioRoute.LiveTv, VidiioRoute.Library, VidiioRoute.Search(), VidiioRoute.Favorites, VidiioRoute.Downloads, VidiioRoute.Settings, VidiioRoute.Splash)
    )

    val navigator = remember { Navigator(navigationState) }
    val context = LocalContext.current
    val isTv = remember { isTvDevice(context) }
    val application = context.applicationContext as VidiioApplication
    
    // Screens get their dependencies from VidiioViewModelFactories, which reads them off
    // `application` directly - only what this composable itself uses is hoisted here.
    val settingsRepository = application.settingsRepository

    // Whose list is this? Profiles live in Settings, but the answer should be visible while browsing.
    val profiles by application.profileRepository.allProfiles
        .collectAsState(initial = emptyList<com.ominix.vidiio.data.model.UserProfile>())
    val activeProfileId by settingsRepository.activeProfileIdFlow.collectAsState(initial = "default")
    val activeProfileName = profiles.firstOrNull { it.id == activeProfileId }?.name

    val homeStyle by settingsRepository.homeStyleFlow
        .collectAsState(initial = com.ominix.vidiio.data.repository.HomeStyle.VIDIIO)

    val mouseToggle by settingsRepository.mouseToggleFlow.collectAsState(initial = false)

    val themeAccent = MaterialTheme.colorScheme.primary
    val spec = remember(homeStyle, themeAccent) { homeStyle.spec(themeAccent) }

    val entryProvider = entryProvider<NavKey> {
        entry<VidiioRoute.Splash> {
            SplashScreen(
                onSplashFinished = {
                    navigator.navigate(VidiioRoute.Home)
                }
            )
        }
        entry<VidiioRoute.Home>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.HomeViewModel = viewModel(
                factory = VidiioViewModelFactories.home(application)
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigateToDetails = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                onSearchQuery = { query -> navigator.navigate(VidiioRoute.Search(query)) },
                onResumeWatching = { wp ->
                    navigator.navigate(VidiioRoute.Player(wp.toMovie(), wp.episodeId, null))
                },
                onOpenMovies = { navigator.navigate(VidiioRoute.Movies) },
                onOpenTv = { navigator.navigate(VidiioRoute.TvShows) },
                onOpenAnime = { navigator.navigate(VidiioRoute.Anime) },
                onOpenGuide = { navigator.navigate(VidiioRoute.TvGuide) },
                onOpenCollection = { title, studio -> navigator.navigate(VidiioRoute.Collection(title, studio)) },
                onPlay = { movie -> navigator.navigate(VidiioRoute.Details(movie, autoPlay = true)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Movies>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel = viewModel(
                key = "browse_movies",
                factory = VidiioViewModelFactories.browse(application, BrowseKind.MOVIES)
            )
            MediaBrowseScreen(
                viewModel = viewModel,
                homeStyle = homeStyle,
                onOpenMovie = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                onPlay = { movie -> navigator.navigate(VidiioRoute.Details(movie, autoPlay = true)) },
                onTab = { index ->
                    when (index) {
                        0 -> navigator.navigate(VidiioRoute.Home)
                        1 -> navigator.navigate(VidiioRoute.Movies)
                        2 -> navigator.navigate(VidiioRoute.TvShows)
                        else -> navigator.navigate(VidiioRoute.TvGuide)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.TvGuide> {
            val viewModel: com.ominix.vidiio.ui.viewmodel.TvGuideViewModel = viewModel(factory = VidiioViewModelFactories.tvGuide(application))
            TvGuideScreen(
                viewModel = viewModel,
                onOpenShow = { movie, season, episode ->
                    navigator.navigate(VidiioRoute.Details(movie, season, episode))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.LiveTv>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.LiveTvViewModel = viewModel(factory = VidiioViewModelFactories.liveTv(application))
            LiveTvScreen(
                viewModel = viewModel,
                onPlay = { movie, source -> navigator.navigate(VidiioRoute.Player(movie, null, source)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Collection> { key ->
            val viewModel: com.ominix.vidiio.ui.viewmodel.CollectionViewModel = viewModel(
                key = "collection_${key.studioId}",
                factory = VidiioViewModelFactories.collection(application, key.title, key.studioId)
            )
            CollectionScreen(
                title = key.title,
                viewModel = viewModel,
                onOpenMovie = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.TvShows>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.MediaBrowseViewModel = viewModel(
                key = "browse_tv",
                factory = VidiioViewModelFactories.browse(application, BrowseKind.TV)
            )
            MediaBrowseScreen(
                viewModel = viewModel,
                homeStyle = homeStyle,
                onOpenMovie = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                onPlay = { movie -> navigator.navigate(VidiioRoute.Details(movie, autoPlay = true)) },
                onTab = { index ->
                    when (index) {
                        0 -> navigator.navigate(VidiioRoute.Home)
                        1 -> navigator.navigate(VidiioRoute.Movies)
                        2 -> navigator.navigate(VidiioRoute.TvShows)
                        else -> navigator.navigate(VidiioRoute.TvGuide)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Library> {
            LibraryScreen(
                onOpen = { route -> navigator.navigate(route) },
                profileName = activeProfileName,
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Anime>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.AnimeViewModel = viewModel(
                factory = VidiioViewModelFactories.anime(application)
            )
            AnimeScreen(
                viewModel = viewModel,
                onOpenMovie = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Search>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) { key ->
            val viewModel: com.ominix.vidiio.ui.viewmodel.SearchViewModel = viewModel(
                key = "search_${key.query}",
                factory = VidiioViewModelFactories.search(application)
            )
            LaunchedEffect(key.query) {
                if (key.query.isNotBlank()) {
                    viewModel.onQueryChange(key.query)
                    viewModel.search()
                }
            }
            SearchScreen(
                viewModel = viewModel,
                onNavigateToDetails = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                onPlay = { movie -> navigator.navigate(VidiioRoute.Details(movie, autoPlay = true)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Favorites>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.FavoritesViewModel = viewModel(
                factory = VidiioViewModelFactories.favorites(application)
            )
            FavoritesScreen(
                viewModel = viewModel,
                onMovieClick = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                onPlay = { movie -> navigator.navigate(VidiioRoute.Details(movie, autoPlay = true)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Downloads>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.DownloadsViewModel = viewModel(
                factory = VidiioViewModelFactories.downloads(application)
            )
            DownloadsScreen(
                viewModel = viewModel,
                onPlayLocal = { movie, path ->
                    val uri = android.net.Uri.fromFile(java.io.File(path)).toString()
                    navigator.navigate(VidiioRoute.Player(movie, null, com.ominix.vidiio.data.model.StreamSource(serverName = "Local", url = uri, quality = "Local", isM3u8 = false)))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Settings>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.topLevelTransition() }
            }
        ) {
            val viewModel: com.ominix.vidiio.ui.viewmodel.SettingsViewModel = viewModel(
                factory = VidiioViewModelFactories.settings(application)
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        entry<VidiioRoute.Details>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) { homeStyle.detailTransition() }
            }
        ) { key ->
            val viewModel: com.ominix.vidiio.ui.viewmodel.DetailsViewModel = viewModel(
                key = key.movie.id,
                factory = VidiioViewModelFactories.details(application, key.movie)
            )
            DetailsScreen(
                viewModel = viewModel,
                autoPlaySeason = key.autoPlaySeason,
                autoPlayEpisode = key.autoPlayEpisode,
                autoPlay = key.autoPlay,
                initialMovie = key.movie,
                onBack = { navigator.goBack() },
                onPlay = { movie, episode, source -> 
                    navigator.navigate(VidiioRoute.Player(movie, episode?.id, source)) 
                },
                onMovieClick = { movie -> navigator.navigate(VidiioRoute.Details(movie)) },
                modifier = Modifier.fillMaxSize()
            )
        }
        @androidx.media3.common.util.UnstableApi
        entry<VidiioRoute.Player>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                }
            }
        ) { key ->
            val playerViewModel: com.ominix.vidiio.ui.player.PlayerViewModel = viewModel(
                key = "player_${key.movie.id}_${key.episodeId ?: ""}",
                factory = VidiioViewModelFactories.player(application, key.movie, key.episodeId, key.source)
            )
            PlayerScreen(
                playerViewModel = playerViewModel,
                initialSource = key.source,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val currentRoute = navigationState.backStacks[navigationState.topLevelRoute]?.lastOrNull()
    val isPlayerScreen = currentRoute is VidiioRoute.Player
    val isSplashScreen = currentRoute is VidiioRoute.Splash
    val isSearchScreen = currentRoute is VidiioRoute.Search
    val pointerEnabled = mouseToggle && !isPlayerScreen && !isSplashScreen && !isSearchScreen

    // Back at the very base of a section would leave the app, and people press Back repeatedly
    // meaning "take me to the menu". So: from another section, Back goes Home; from Home it asks.
    var confirmExit by remember { mutableStateOf(false) }
    val atBase = (navigationState.backStacks[navigationState.topLevelRoute]?.size ?: 0) <= 1
    androidx.activity.compose.BackHandler(enabled = atBase && !isSplashScreen) {
        if (navigationState.topLevelRoute == VidiioRoute.Home) confirmExit = true
        else navigator.navigate(VidiioRoute.Home)
    }
    if (confirmExit) {
        val stayFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { stayFocus.requestFocus() } }
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave Vidiio?") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = { (context as? android.app.Activity)?.finish() }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmExit = false },
                    modifier = Modifier.focusRequester(stayFocus)
                ) { Text("Stay") }
            }
        )
    }

    val layoutType = if (isPlayerScreen || isSplashScreen) {
        NavigationSuiteType.None
    } else {
        val windowSizeClass = adaptiveInfo.windowSizeClass
        when {
            // A phone on its side is wide but short: a rail would run off the bottom, so keep the bar.
            !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> NavigationSuiteType.NavigationBar
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> NavigationSuiteType.NavigationRail
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> NavigationSuiteType.NavigationRail
            else -> NavigationSuiteType.NavigationBar
        }
    }

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = spec.accent,
            indicatorColor = spec.accent.copy(alpha = 0.35f),
            unselectedIconColor = Color.White.copy(alpha = 0.6f),
            unselectedTextColor = Color.White.copy(alpha = 0.6f)
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = spec.accent,
            indicatorColor = spec.accent.copy(alpha = 0.35f),
            unselectedIconColor = Color.White.copy(alpha = 0.6f),
            unselectedTextColor = Color.White.copy(alpha = 0.6f)
        )
    )

    // The sections. The side menu (TV, tablets) lists them all; the portrait phone's bottom bar
    // has room for six, so My List / Downloads / Settings sit behind the Library tab there.
    fun item(route: androidx.navigation3.runtime.NavKey, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) =
        TvNavItem(selected = selected, icon = icon, label = label, onClick = { navigator.navigate(route) })

    val top = navigationState.topLevelRoute
    val home = item(VidiioRoute.Home, top == VidiioRoute.Home, Icons.Rounded.Home, "Home")
    val movies = item(VidiioRoute.Movies, top == VidiioRoute.Movies, Icons.Rounded.Movie, "Movies")
    val tv = item(VidiioRoute.TvShows, top == VidiioRoute.TvShows, Icons.Rounded.Tv, "TV Shows")
    val anime = item(VidiioRoute.Anime, top == VidiioRoute.Anime, Icons.Rounded.AutoAwesome, "Anime")
    val liveTv = item(VidiioRoute.LiveTv, top == VidiioRoute.LiveTv, Icons.Rounded.LiveTv, "Live TV")
    val search = item(VidiioRoute.Search(), top is VidiioRoute.Search, Icons.Rounded.Search, "Search")
    val railItems = listOf(
        home, movies, tv, anime, liveTv, search,
        item(VidiioRoute.Favorites, top == VidiioRoute.Favorites, Icons.Rounded.Favorite, "My List"),
        item(VidiioRoute.Downloads, top == VidiioRoute.Downloads, Icons.Rounded.Download, "Downloads"),
        item(VidiioRoute.Settings, top == VidiioRoute.Settings, Icons.Rounded.Settings, "Settings"),
    )
    val useBottomBar = layoutType == NavigationSuiteType.NavigationBar
    // Navigation labels have to fit a fixed-width bar or rail. Cap them at 13.5sp effective: at
    // 1.5x the system font "Settings" ran off the right edge of the bar, "Downloads" filled the
    // whole side menu, and a three-button navigation bar took even more room. The rest of the app
    // keeps the full scale.
    val navFontScale = LocalDensity.current.fontScale
    val navLabelSize = (minOf(12f * navFontScale, 13.5f) / navFontScale).sp
    // A phone held sideways has a bar that is wide but short: unlike the six-tab portrait bar it
    // has room for Settings. Put Settings on it, because a scroll down the Library page made it
    // easy to miss entirely. In portrait Settings still lives behind the Library tab.
    val settingsOnBar = useBottomBar && !adaptiveInfo.windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val settingsNeedsLibraryTab = useBottomBar && !settingsOnBar
    val libraryTop = top == VidiioRoute.Library || top == VidiioRoute.Favorites ||
        top == VidiioRoute.Downloads || (top == VidiioRoute.Settings && settingsNeedsLibraryTab)
    val library = item(VidiioRoute.Library, libraryTop, Icons.Rounded.VideoLibrary, "Library")
    // Six tabs on a phone is tight: the bar uses short labels so none of them is cut off.
    val barItems = buildList {
        add(item(VidiioRoute.Home, top == VidiioRoute.Home, Icons.Rounded.Home, "Home"))
        add(item(VidiioRoute.Movies, top == VidiioRoute.Movies, Icons.Rounded.Movie, "Movies"))
        add(item(VidiioRoute.TvShows, top == VidiioRoute.TvShows, Icons.Rounded.Tv, "TV"))
        add(item(VidiioRoute.Anime, top == VidiioRoute.Anime, Icons.Rounded.AutoAwesome, "Anime"))
        // A landscape phone's bar is wide enough for Live TV; the portrait bar's six tabs are not.
        if (adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            add(item(VidiioRoute.LiveTv, top == VidiioRoute.LiveTv, Icons.Rounded.LiveTv, "Live TV"))
        }
        add(item(VidiioRoute.Search(), top is VidiioRoute.Search, Icons.Rounded.Search, "Search"))
        add(library)
        if (settingsOnBar) {
            add(item(VidiioRoute.Settings, top == VidiioRoute.Settings, Icons.Rounded.Settings, "Settings"))
        }
    }

    // Phones get search + library shortcuts on the pages' headers; TVs and tablets have the menu.
    val shortcuts = remember(useBottomBar, isTv) {
        if (useBottomBar && !isTv) {
            BrowseShortcuts(
                onSearch = { navigator.navigate(VidiioRoute.Search()) },
                onLibrary = { navigator.navigate(VidiioRoute.Library) }
            )
        } else null
    }

    val pageContent: @Composable () -> Unit = {
        VirtualPointerOverlay(enabled = pointerEnabled) {
            NavDisplay(
                entries = navigationState.toEntries(entryProvider),
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    CompositionLocalProvider(LocalIsTv provides isTv, LocalBrowseShortcuts provides shortcuts) {
        ProvideTvScrolling(enabled = isTv) {
            if (isTv) {
                // A remote-driven TV gets a side menu that hides itself while you browse.
                TvNavScaffold(
                    items = railItems,
                    railEnabled = !(isPlayerScreen || isSplashScreen),
                    railColors = navItemColors.navigationRailItemColors,
                    pageKey = currentRoute ?: "none",
                    autoFocus = !isSearchScreen,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        pageContent()
                    }
                }
            } else {
                NavigationSuiteScaffold(
                    layoutType = layoutType,
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationSuiteItems = {
                        (if (useBottomBar) barItems else railItems).forEach { nav ->
                            item(
                                selected = nav.selected,
                                onClick = nav.onClick,
                                icon = { Icon(nav.icon, contentDescription = nav.label) },
                                label = {
                                    Text(
                                        nav.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = navLabelSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = navItemColors
                            )
                        }
                    }
                ) {
                    pageContent()
                }
            }
        }
        // After the splash, so the first frame isn't held up and the dialog lands on a real page.
        com.ominix.vidiio.ui.update.UpdatePrompt(application.appUpdater, checkOnStart = !isSplashScreen && !isPlayerScreen)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun VidiioAppPhonePreview() {
    com.ominix.vidiio.ui.theme.VidiioTheme {
        VidiioApp()
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun VidiioAppTabletPreview() {
    com.ominix.vidiio.ui.theme.VidiioTheme {
        VidiioApp()
    }
}
