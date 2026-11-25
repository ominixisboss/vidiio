package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.toMovie
import com.ominix.vidiio.data.repository.FavoriteRepository
import com.ominix.vidiio.data.repository.WatchProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(
        val movies: List<Movie>,
        /** How far into each title the viewer is, keyed by movie id. */
        val progress: Map<String, Float> = emptyMap(),
    ) : FavoritesUiState
    data class Error(val message: String) : FavoritesUiState
}

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository,
    watchProgressRepository: WatchProgressRepository,
) : ViewModel() {

    /**
     * Read straight from the database. There is no retry: a failed local read cannot be fixed by
     * asking again, so the screen shows the error without a button.
     */
    val uiState: StateFlow<FavoritesUiState> = combine(
        favoriteRepository.favorites,
        watchProgressRepository.all
    ) { favorites, progress ->
        FavoritesUiState.Success(
            movies = favorites.map { it.toMovie() },
            progress = progress.associate { it.id to it.progressFraction }
        ) as FavoritesUiState
    }
        .catch { error -> emit(FavoritesUiState.Error(error.message ?: "Couldn't read your list")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FavoritesUiState.Loading
        )
}
