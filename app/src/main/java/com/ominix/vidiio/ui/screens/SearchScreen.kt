package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.ui.components.EmptyState
import com.ominix.vidiio.ui.components.ErrorState
import com.ominix.vidiio.ui.components.FilterRow
import com.ominix.vidiio.ui.components.MovieItem
import com.ominix.vidiio.ui.components.PosterGridSkeleton
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.tv.LocalIsTv
import com.ominix.vidiio.ui.tv.LocalTvFocusMemory
import com.ominix.vidiio.ui.tv.LocalTvRailOpener
import com.ominix.vidiio.ui.viewmodel.SearchUiState
import com.ominix.vidiio.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToDetails: (Movie) -> Unit,
    /** Long-press "Play": open the title and start its best stream. */
    onPlay: (Movie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val progressById by viewModel.progress.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Movie?>(null) }
    val isTv = LocalIsTv.current
    val searchFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val openRail = LocalTvRailOpener.current
    // With results on screen the page's first control is a filter chip, so leaving the rail needs
    // to know that the field, not the chip, is where the remote was.
    val focusMemory = LocalTvFocusMemory.current
    val hasResults = (uiState as? SearchUiState.Success)?.results?.isNotEmpty() == true
    // Opening Search from the bar should put the cursor in the field, not make you tap it first.
    // On a TV the remote stays on the menu, and a query carried in from Home already has results.
    LaunchedEffect(Unit) {
        if (!isTv && query.isBlank()) {
            kotlinx.coroutines.delay(250)
            runCatching { searchFocus.requestFocus() }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = {
                            viewModel.onQueryChange(it)
                            if (it.isBlank()) selectedFilter = "All"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .focusRequester(searchFocus)
                            .onFocusChanged {
                                isSearchFocused = it.isFocused
                                if (it.isFocused) focusMemory?.record(searchFocus)
                            }
                            // The TV keyboard owns the D-pad while it is up, so Left belongs to
                            // the on-screen keys. Take only Menu here, before the keyboard sees
                            // it, so the rail stays reachable while typing.
                            .onPreInterceptKeyBeforeSoftKeyboard { event ->
                                if (!isTv || openRail == null || event.key != Key.Menu) {
                                    return@onPreInterceptKeyBeforeSoftKeyboard false
                                }
                                if (event.type == KeyEventType.KeyDown) {
                                    keyboard?.hide()
                                    openRail()
                                }
                                true
                            }
                            // A remote has no way past the field: the text field takes Down for
                            // cursor movement, so the results below are unreachable. Down leaves
                            // the field and lands on the first poster instead.
                            .onPreviewKeyEvent { event ->
                                if (!isTv || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    // The field takes Left for the cursor, so a remote without a Menu
                                    // key gets into the menu with Left once the keyboard is down. While
                                    // the keyboard is up the IME owns the D-pad, and Menu (taken above)
                                    // is the way in.
                                    event.key == Key.DirectionLeft && openRail != null -> {
                                        keyboard?.hide()
                                        openRail()
                                        true
                                    }
                                    event.key == Key.DirectionDown && hasResults -> {
                                        keyboard?.hide()
                                        runCatching { firstResultFocus.requestFocus() }.isSuccess
                                    }
                                    else -> false
                                }
                            }
                            .border(
                                width = if (isSearchFocused) 2.dp else 0.5.dp,
                                color = if (isSearchFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        placeholder = { Text("Search movies, shows...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.search()
                            // On a TV the keyboard takes the D-pad, so it must go away on submit
                            // or the results below stay unreachable.
                            if (isTv) keyboard?.hide()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is SearchUiState.Idle -> EmptyState(
                    title = "Find your next favorite",
                    message = "Search for movies, TV shows, and anime.",
                    icon = Icons.Rounded.Search,
                    modifier = Modifier.align(Alignment.Center)
                )
                is SearchUiState.Loading -> {
                    PosterGridSkeleton(modifier = Modifier.fillMaxSize())
                }
                is SearchUiState.Success -> {
                    val filteredResults = remember(state.results, selectedFilter) {
                        state.results.filter { movie ->
                            when (selectedFilter) {
                                "Movies" -> movie.type == MovieType.MOVIE
                                "TV Shows" -> movie.type == MovieType.TV_SHOW
                                else -> true
                            }
                        }
                    }

                    if (state.results.isEmpty()) {
                        EmptyState(
                            title = "No results for \"$query\"",
                            message = "Try a different spelling, or search for an actor or director.",
                            icon = Icons.Rounded.Search,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FilterRow(
                                options = listOf("All", "Movies", "TV Shows"),
                                selected = selectedFilter,
                                onSelect = { selectedFilter = it },
                                trailing = "${filteredResults.size}"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (filteredResults.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        title = "No ${selectedFilter.lowercase()} in these results",
                                        message = "Try another filter to see everything that was found.",
                                        icon = Icons.Rounded.Search
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 104.dp),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(filteredResults) { index, movie ->
                                        MovieItem(
                                            movie = movie,
                                            onClick = { onNavigateToDetails(movie) },
                                            modifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
                                            progress = progressById[movie.id],
                                            onLongClick = { actionsFor = movie },
                                            // Back from the details page comes back to this card.
                                            returnKey = "search-${movie.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is SearchUiState.Error -> ErrorState(
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
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
