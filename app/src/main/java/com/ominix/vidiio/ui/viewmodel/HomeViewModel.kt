package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.WatchProgress
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseList
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.CancellationException

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val categories: List<Category>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val movieRepository: MovieRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val continueWatching: StateFlow<List<WatchProgress>> =
        watchProgressRepository.continueWatching
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeStyle: StateFlow<HomeStyle> = settingsRepository.homeStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStyle.FLIX)

    val showVpnReminder: StateFlow<Boolean> = settingsRepository.showVpnReminderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissVpnReminder(dontShowAgain: Boolean) {
        if (dontShowAgain) {
            viewModelScope.launch {
                settingsRepository.setShowVpnReminder(false)
            }
        }
    }

    // Declared before init: refresh() runs from init on the main dispatcher at once, and touches these.
    /** Home rows that can grow as they scroll, and the TMDB list behind each. */
    private val pagedRows = mapOf(
        "Trending Movies" to (BrowseKind.MOVIES to BrowseList.TRENDING),
        "Popular Movies" to (BrowseKind.MOVIES to BrowseList.POPULAR),
        "Popular TV Shows" to (BrowseKind.TV to BrowseList.POPULAR),
    )
    private val rowPages = HashMap<String, Int>()
    private val rowsLoading = HashSet<String>()

    init {
        refresh()
    }

    fun refresh(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullToRefresh) movieRepository.invalidateCache()
            rowPages.clear()
            if (isPullToRefresh) {
                _isRefreshing.value = true
            } else {
                _uiState.value = HomeUiState.Loading
            }
            try {
                val categories = movieRepository.getHomeCategories()
                _uiState.value = HomeUiState.Success(categories)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "HomeViewModel.refresh() failed", e)
                if (!isPullToRefresh) {
                    _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun canLoadMore(row: String) = row in pagedRows

    /** Appends the next page to the Home row named [row], if it is one that pages. */
    fun loadMore(row: String) {
        val (kind, list) = pagedRows[row] ?: return
        if (row in rowsLoading || (rowPages[row] ?: 1) >= MAX_ROW_PAGES) return
        rowsLoading += row
        viewModelScope.launch {
            try {
                val next = (rowPages[row] ?: 1) + 1
                val more = movieRepository.browsePage(kind, list, next)
                rowPages[row] = next
                val current = _uiState.value as? HomeUiState.Success ?: return@launch
                _uiState.value = HomeUiState.Success(current.categories.map { c ->
                    if (c.name != row) c
                    else {
                        val seen = c.movies.mapTo(HashSet()) { it.id }
                        c.copy(movies = c.movies + more.filter { it.id !in seen })
                    }
                })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "More of $row failed", e)
            } finally {
                rowsLoading -= row
            }
        }
    }

    fun removeContinueWatching(id: String) {
        viewModelScope.launch { watchProgressRepository.remove(id) }
    }
}

private const val TAG = "HomeViewModel"
private const val MAX_ROW_PAGES = 25