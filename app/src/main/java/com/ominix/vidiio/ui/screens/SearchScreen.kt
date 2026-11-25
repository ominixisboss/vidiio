package com.ominix.vidiio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.ominix.vidiio.ui.components.RiveAnimation
import com.ominix.vidiio.ui.components.RiveLoader
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.tv.LocalIsTv
import com.ominix.vidiio.ui.viewmodel.SearchUiState
import com.ominix.vidiio.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToDetails: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val progressById by viewModel.progress.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val searchFocus = remember { FocusRequester() }
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
                            .onFocusChanged { isSearchFocused = it.isFocused }
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
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
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
                is SearchUiState.Idle -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RiveAnimation(
                            url = "https://public.rive.app/community/runtime-files/2042-4040-avatar-pack-use-case.riv",
                            modifier = Modifier.size(200.dp),
                            fallback = {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp).alpha(0.3f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Find your next favorite",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Search for movies, TV shows, and anime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                                    items(filteredResults) { movie ->
                                        MovieItem(
                                            movie = movie,
                                            onClick = { onNavigateToDetails(movie) },
                                            progress = progressById[movie.id]
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
}
