package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.FavoriteDao
import com.ominix.vidiio.data.model.FavoriteMovie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

/** My List is per profile: every read is scoped to the active profile and follows switches. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavoriteRepository(
    private val favoriteDao: FavoriteDao,
    private val settingsRepository: SettingsRepository
) {
    private val profileId = settingsRepository.activeProfileIdFlow.distinctUntilChanged()

    val favorites: Flow<List<FavoriteMovie>> =
        profileId.flatMapLatest { favoriteDao.getAllFavorites(it) }

    fun isFavorite(movieId: String): Flow<Boolean> =
        profileId.flatMapLatest { favoriteDao.isFavorite(movieId, it) }

    suspend fun addFavorite(movie: FavoriteMovie) {
        favoriteDao.insert(movie.copy(profileId = settingsRepository.activeProfileIdFlow.first()))
    }

    suspend fun removeFavorite(movie: FavoriteMovie) {
        favoriteDao.delete(movie.copy(profileId = settingsRepository.activeProfileIdFlow.first()))
    }
}
