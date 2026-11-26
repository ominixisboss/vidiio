package com.ominix.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.toFavoriteMovie
import com.ominix.vidiio.data.repository.FavoriteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the long-press menu on a poster. It owns one thing the grid itself does not know:
 * whether the title is already in My List, so the menu can offer Add or Remove.
 */
class PosterActionsViewModel(
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    val favoriteIds: StateFlow<Set<String>> = favoriteRepository.favorites
        .map { favorites -> favorites.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Returns the state the title is headed for, so the caller can confirm it. */
    fun toggle(movie: Movie): Boolean {
        val wasFavorite = movie.id in favoriteIds.value
        viewModelScope.launch {
            val favorite = movie.toFavoriteMovie()
            if (wasFavorite) {
                favoriteRepository.removeFavorite(favorite)
            } else {
                favoriteRepository.addFavorite(favorite)
            }
        }
        return !wasFavorite
    }
}
