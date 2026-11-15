package com.ominix.vidiio.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.repository.BrowseFilter
import com.ominix.vidiio.data.repository.BrowseKind
import com.ominix.vidiio.data.repository.BrowseList
import com.ominix.vidiio.data.repository.BrowseSort
import com.ominix.vidiio.data.repository.Decade
import com.ominix.vidiio.data.repository.Mood
import com.ominix.vidiio.data.repository.MovieRepository
import com.ominix.vidiio.data.repository.Provider
import com.ominix.vidiio.data.repository.StreamingService
import com.ominix.vidiio.data.repository.BrowseOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a Movies or TV page has so far. Each shelf loads on its own and appears as soon as it is
 * ready; `null` means still loading.
 */
data class BrowseState(
    val trending: List<Movie>? = null,
    val popular: List<Movie>? = null,
    val fresh: List<Movie>? = null,
    val soon: List<Movie>? = null,
    val topRated: List<Movie>? = null,
    /** What the viewer has narrowed the page to, and the matching titles (null while loading). */
    val filter: BrowseFilter = BrowseFilter(),
    val filterResults: List<Movie>? = null,
    /** Which ready-made list the page shows while no filter is set. */
    val list: BrowseList = BrowseList.TRENDING,
    /** A streaming service picked from the tile row, and its trending titles (null while loading). */
    val service: StreamingService? = null,
    val serviceResults: List<Movie>? = null,
    /** Trending titles per streaming service id, for the shelf layout. */
    val serviceShelves: Map<String, List<Movie>> = emptyMap(),
    val isRefreshing: Boolean = false,
    val failures: Int = 0,
) {
    val isLoadingEverything: Boolean get() = trending == null && popular == null && failures == 0
    val hasNothing: Boolean get() = failures > 0 && trending.isNullOrEmpty() && popular.isNullOrEmpty()
}

/** A TMDB genre. The ids differ between movies and TV, so each page has its own list. */
data class Genre(val id: Int, val name: String)

/** Drives the Movies page or the TV page; [kind] picks which. */
class MediaBrowseViewModel(
    val kind: BrowseKind,
    private val repository: MovieRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var filterJob: Job? = null

    /** In-memory cache, so coming back to the page does not refetch it. */
    private val cache = HashMap<String, List<Movie>>()

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        loadJob?.cancel()
        if (force) {
            cache.clear()
            repository.invalidateCache()
            pages.clear()
            exhausted.clear()
        }
        _state.update { it.copy(isRefreshing = force, failures = 0) }
        loadJob = viewModelScope.launch {
            coroutineScope {
                launch { shelf(BrowseList.TRENDING) { s, v -> s.copy(trending = v) } }
                launch { shelf(BrowseList.POPULAR) { s, v -> s.copy(popular = v) } }
                launch { shelf(BrowseList.FRESH) { s, v -> s.copy(fresh = v) } }
                launch { shelf(BrowseList.SOON) { s, v -> s.copy(soon = v) } }
                launch { shelf(BrowseList.TOP_RATED) { s, v -> s.copy(topRated = v) } }
                BrowseOptions.services.forEach { s -> launch { loadServiceShelf(s) } }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
        if (!_state.value.filter.isEmpty) applyFilter(_state.value.filter, force = true)
    }

    /** Loads one shelf; a failure is counted, not thrown, so the other shelves still arrive. */
    private suspend fun shelf(list: BrowseList, apply: (BrowseState, List<Movie>) -> BrowseState) {
        try {
            val movies = cache.getOrPut("list-$list") { repository.browse(kind, list) }
            _state.update { apply(it, movies) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Browse shelf $list failed", e)
            _state.update { it.copy(failures = it.failures + 1) }
        }
    }

    /** The last page loaded per shelf, and the shelves fetching their next page or out of pages. */
    private val pages = HashMap<BrowseList, Int>()
    private val loadingMore = HashSet<BrowseList>()
    private val exhausted = HashSet<BrowseList>()

    /** Appends the next page of [list] (about 20 titles), for rows and grids that grow as they scroll. */
    fun loadMore(list: BrowseList) {
        if (list in loadingMore || list in exhausted) return
        val current = list.get(_state.value) ?: return
        loadingMore += list
        viewModelScope.launch {
            try {
                val next = (pages[list] ?: 1) + 1
                val more = repository.browsePage(kind, list, next)
                pages[list] = next
                val seen = current.mapTo(HashSet()) { it.id }
                val fresh = more.filter { it.id !in seen }
                if (more.isEmpty() || next >= MAX_PAGES) exhausted += list
                if (fresh.isNotEmpty()) _state.update { s -> list.set(s, (list.get(s) ?: emptyList()) + fresh) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "More of $list failed", e)
            } finally {
                loadingMore -= list
            }
        }
    }

    private fun BrowseList.get(s: BrowseState): List<Movie>? = when (this) {
        BrowseList.TRENDING -> s.trending
        BrowseList.POPULAR -> s.popular
        BrowseList.TOP_RATED -> s.topRated
        BrowseList.FRESH -> s.fresh
        BrowseList.SOON -> s.soon
    }

    private fun BrowseList.set(s: BrowseState, v: List<Movie>): BrowseState = when (this) {
        BrowseList.TRENDING -> s.copy(trending = v)
        BrowseList.POPULAR -> s.copy(popular = v)
        BrowseList.TOP_RATED -> s.copy(topRated = v)
        BrowseList.FRESH -> s.copy(fresh = v)
        BrowseList.SOON -> s.copy(soon = v)
    }

    /** One shelf per streaming service. Optional furniture: a failure just leaves it out. */
    private suspend fun loadServiceShelf(service: StreamingService) {
        val titles = runCatching { repository.streamingCatalog(kind, service.id) }.getOrDefault(emptyList())
        if (titles.isNotEmpty()) _state.update { it.copy(serviceShelves = it.serviceShelves + (service.id to titles)) }
    }


    // ── Filters ──────────────────────────────────────────────────────────────

    fun setGenre(genre: Genre?) = update { it.copy(genreId = genre?.id, mood = if (genre != null) null else it.mood) }

    fun setMood(mood: Mood?) = update { it.copy(mood = mood, genreId = if (mood != null) null else it.genreId) }

    fun setDecade(decade: Decade?) = update { it.copy(decade = decade) }

    fun setProvider(provider: Provider?) = update { it.copy(provider = provider) }

    /** Lists a streaming service's trending titles; picking it again (or any filter) goes back. */
    fun setService(service: StreamingService?) {
        filterJob?.cancel()
        _state.update { it.copy(service = service, serviceResults = null, filter = BrowseFilter(sort = it.filter.sort), filterResults = null) }
        if (service == null) return
        filterJob = viewModelScope.launch {
            val movies = runCatching { repository.streamingCatalog(kind, service.id) }.getOrDefault(emptyList())
            _state.update { if (it.service == service) it.copy(serviceResults = movies) else it }
        }
    }

    /** Picks a ready-made list (Trending, Top rated, ...) and drops any filters. */
    fun setList(list: BrowseList) {
        _state.update { it.copy(list = list) }
        clearFilters()
    }

    fun setSort(sort: BrowseSort) = update { it.copy(sort = sort) }

    fun clearFilters() = update { BrowseFilter(sort = it.sort) }

    private fun update(change: (BrowseFilter) -> BrowseFilter) {
        // Choosing a filter leaves any streaming-service list.
        if (_state.value.service != null) _state.update { it.copy(service = null, serviceResults = null) }
        applyFilter(change(_state.value.filter), force = false)
    }

    private fun applyFilter(filter: BrowseFilter, force: Boolean) {
        filterJob?.cancel()
        if (filter.isEmpty) {
            _state.update { it.copy(filter = filter, filterResults = null) }
            return
        }
        _state.update { it.copy(filter = filter, filterResults = null) }
        filterJob = viewModelScope.launch {
            val key = "filter-${filter.genreExpr}-${filter.decade?.startYear}-${filter.provider?.id}-${filter.sort}"
            try {
                val results = if (force) repository.browseFiltered(kind, filter).also { cache[key] = it }
                else cache.getOrPut(key) { repository.browseFiltered(kind, filter) }
                _state.update { if (it.filter == filter) it.copy(filterResults = results) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Filter load failed", e)
                _state.update { if (it.filter == filter) it.copy(filterResults = emptyList()) else it }
            }
        }
    }

    private companion object {
        const val TAG = "MediaBrowseViewModel"

        /** TMDB serves at most 500 pages; stop well before so a row never grows without bound. */
        const val MAX_PAGES = 25
    }
}
