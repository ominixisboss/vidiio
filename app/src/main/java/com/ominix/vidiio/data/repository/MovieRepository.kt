package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.api.TMDBService
import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.TOP10_LABEL
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.Season
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.StreamSource
import com.ominix.vidiio.data.model.stremio.StremioMeta
import com.ominix.vidiio.data.model.tmdb.TMDBMovie
import com.ominix.vidiio.data.model.tmdb.bestTrailerKey
import com.ominix.vidiio.data.model.tmdb.TMDBSeason
import com.ominix.vidiio.data.model.tmdb.TMDBEpisode
import com.ominix.vidiio.data.scraper.Scraper
import com.ominix.vidiio.data.stremio.AddonManager
import com.ominix.vidiio.data.stremio.toStreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.util.Log
import kotlinx.coroutines.CancellationException

class MovieRepository(
    private val tmdbService: TMDBService,
    private val scrapers: List<Scraper>,
    private val settingsRepository: SettingsRepository,
    private val addonManager: AddonManager,
    private val sourceVerifier: SourceVerifier,
    private val traktService: com.ominix.vidiio.data.api.TraktService? = null,
    private val simklService: com.ominix.vidiio.data.api.SimklService? = null,
    private val profileRepository: ProfileRepository? = null
) {

    /** Probes one scraped source for whether it is actually playable. See [SourceVerifier]. */
    suspend fun checkSource(source: StreamSource): SourceHealth = sourceVerifier.check(source)

    private companion object {
        /**
         * Per-scraper budget. Must exceed the OkHttp connect timeout (15s) plus a couple of
         * round-trips — the crypto/multi-request providers (Cinejoy queries 5 servers, VidSrc
         * chains resolvers) reliably need more than the old 10s.
         */
        const val SCRAPER_TIMEOUT_MS = 25_000L

        /** One Pace video-id prefix → arc name, for nicer season labels. */
        val ONE_PACE_ARCS = mapOf(
            "RO" to "Romance Dawn", "OR" to "Orange Town", "SY" to "Syrup Village",
            "GA" to "Gaimon", "BA" to "Baratie", "AR" to "Arlong Park", "LO" to "Loguetown",
            "RM" to "Reverse Mountain", "WH" to "Whisky Peak", "LI" to "Little Garden",
            "DI" to "Drum Island", "AL" to "Arabasta", "JA" to "Jaya", "SK" to "Skypiea",
            "LR" to "Long Ring Long Land", "WS" to "Water Seven", "EN" to "Enies Lobby",
            "PEN" to "Post-Enies Lobby", "TB" to "Thriller Bark", "SAB" to "Sabaody Archipelago",
            "AM" to "Amazon Lily", "IM" to "Impel Down", "MA" to "Marineford", "PW" to "Post-War",
            "RTS" to "Return to Sabaody", "FI" to "Fish-Man Island", "PH" to "Punk Hazard",
            "DR" to "Dressrosa", "ZO" to "Zou", "WC" to "Whole Cake Island", "REV" to "Reverie",
            "WA" to "Wano", "EH" to "Egghead",
        )

    }

    private suspend fun getEnabledScrapers(): List<Scraper> {
        val enabledSources = settingsRepository.sourcesFlow.first()
        return scrapers.filter { it.sourceId in enabledSources }
    }

    /**
     * Home is a short front page, not every shelf: a handful of what is hot, plus anything the
     * user's own addons offer. Everything else (genres, top rated, anime, One Pace, ...) lives on
     * the dedicated Movies, TV and Anime pages.
     */
    /** Short-lived memory cache for the lists every screen opens with, so they are ready before the screen is. */
    private val listCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Any>>()

    fun invalidateCache() = listCache.clear()

    /** US ratings already looked up, so a title seen on several shelves costs one request. */
    private val kidsRatingCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Caps concurrent certification lookups: one Kids page opens a dozen shelves at once. */
    private val kidsLookupLimiter = Semaphore(6)

    private suspend fun isKidsMode(): Boolean = try {
        profileRepository?.getActiveProfile()?.isKids == true
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }

    /**
     * True when TMDB's US age rating puts [movie] at PG or below. Unrated titles, and titles from
     * sources that carry no US rating at all, are excluded: a kids profile fails closed.
     */
    private suspend fun isKidSafe(movie: Movie): Boolean {
        val tmdbId = movie.id.toIntOrNull() ?: return false
        if (movie.source != "tmdb") return false
        val key = "${movie.type}-$tmdbId"
        kidsRatingCache[key]?.let { return it }
        val safe = try {
            kidsLookupLimiter.withPermit {
                when (movie.type) {
                    MovieType.MOVIE -> {
                        val certs = tmdbService.getMovieReleaseDates(tmdbId).results
                            .firstOrNull { it.country == "US" }
                            ?.releaseDates.orEmpty()
                            .mapNotNull { it.certification?.trim()?.takeIf(String::isNotEmpty) }
                        certs.isNotEmpty() && certs.all { it == "G" || it == "PG" }
                    }
                    MovieType.TV_SHOW -> {
                        val rating = tmdbService.getTvContentRatings(tmdbId).results
                            .firstOrNull { it.country == "US" }
                            ?.rating?.trim()
                        rating != null && KIDS_TV_RATINGS.any { rating == it || rating.startsWith("$it-") }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
        kidsRatingCache[key] = safe
        return safe
    }

    /**
     * Whether the active profile may open [movie]. Adults may open anything; a kids profile only
     * gets titles that carry a US rating at PG/TV-PG or below.
     */
    suspend fun isAllowedForActiveProfile(movie: Movie): Boolean =
        !isKidsMode() || isKidSafe(movie)

    /** Drops everything the active kids profile must not see. Answer cached per title. */
    private suspend fun List<Movie>.forActiveProfile(): List<Movie> {
        if (isEmpty() || !isKidsMode()) return this
        val allowed = mutableListOf<Movie>()
        for (batch in chunked(10)) {
            val verdicts = coroutineScope {
                batch.map { movie -> async { movie to isKidSafe(movie) } }.awaitAll()
            }
            allowed += verdicts.filter { it.second }.map { it.first }
        }
        return allowed
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        val hit = listCache[key]
        if (hit != null && System.currentTimeMillis() - hit.first < LIST_TTL_MS) return hit.second as T
        return load().also { if (it !is List<*> || it.isNotEmpty()) listCache[key] = System.currentTimeMillis() to it }
    }

    suspend fun getHomeCategories(): List<Category> {
        val kids = isKidsMode()
        return cached(if (kids) "home-kids" else "home") {
            if (kids) loadKidsHomeCategories() else loadHomeCategories()
        }
    }

    /**
     * Kids Home only carries certified titles: films from TMDB discover with a US certification at
     * PG or below, and TV shows whose US content rating is TV-PG or gentler.
     */
    private suspend fun loadKidsHomeCategories(): List<Category> = coroutineScope {
        try {
            fun movieRow(genres: String? = null) = async {
                runCatching {
                    tmdbService.discoverMoviesFiltered(
                        withGenres = genres,
                        certificationCountry = "US",
                        certificationLte = "PG",
                        includeAdult = false
                    ).results.map { it.toMovie(MovieType.MOVIE) }.filter { it.posterUrl.isNotBlank() }
                }.getOrDefault(emptyList())
            }

            fun tvRow(genres: String) = async {
                runCatching {
                    tmdbService.discoverTvFiltered(withGenres = genres, voteCountGte = 20)
                        .results.map { it.toMovie(MovieType.TV_SHOW) }
                        .filter { it.posterUrl.isNotBlank() }
                        .forActiveProfile()
                }.getOrDefault(emptyList())
            }

            val familyMovies = movieRow()
            val animatedMovies = movieRow("16")
            val familyComedies = movieRow("35")
            val kidsShows = tvRow("10762")
            val familyShows = tvRow("10751")

            listOf(
                Category("Family Movies", familyMovies.await()),
                Category("Animated Movies", animatedMovies.await()),
                Category("Kids TV Shows", kidsShows.await()),
                Category("Family TV Shows", familyShows.await()),
                Category("Family Comedies", familyComedies.await()),
            ).filter { it.movies.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MovieRepository", "Error fetching kids home", e)
            emptyList()
        }
    }

    private suspend fun loadHomeCategories(): List<Category> = coroutineScope {
        try {
            val trendingMovies = async { tmdbService.getTrendingMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            val popularMovies = async { tmdbService.getPopularMovies().results.map { it.toMovie(MovieType.MOVIE) } }
            val popularTV = async { tmdbService.getPopularTVShows().results.map { it.toMovie(MovieType.TV_SHOW) } }
            val trendingAnime = async { getTrendingAnime() }

            val actionBlockbusters = async {
                runCatching { tmdbService.discoverMovies(withGenres = "28").results.map { it.toMovie(MovieType.MOVIE) } }.getOrDefault(emptyList())
            }
            val sciFiFantasy = async {
                runCatching { tmdbService.discoverMovies(withGenres = "878|14").results.map { it.toMovie(MovieType.MOVIE) } }.getOrDefault(emptyList())
            }
            val crimeThrillers = async {
                runCatching { tmdbService.discoverMovies(withGenres = "53|80").results.map { it.toMovie(MovieType.MOVIE) } }.getOrDefault(emptyList())
            }
            val comedyHits = async {
                runCatching { tmdbService.discoverMovies(withGenres = "35").results.map { it.toMovie(MovieType.MOVIE) } }.getOrDefault(emptyList())
            }

            val stremioCatalogs = async {
                addonManager.getCatalogs().map { (name, metas) ->
                    Category(name, metas.map { it.toMovie() })
                }
            }

            val categories = mutableListOf(
                Category("Trending Movies", trendingMovies.await()),
                Category(TOP10_LABEL, trendingMovies.await().take(10)),
                Category("Popular TV Shows", popularTV.await()),
                Category("Trending Anime", trendingAnime.await()),
                Category("Popular Movies", popularMovies.await()),
            )

            actionBlockbusters.await().takeIf { it.isNotEmpty() }?.let { categories.add(Category("Action Blockbusters", it)) }
            sciFiFantasy.await().takeIf { it.isNotEmpty() }?.let { categories.add(Category("Sci-Fi & Fantasy", it)) }
            crimeThrillers.await().takeIf { it.isNotEmpty() }?.let { categories.add(Category("Crime & Thrillers", it)) }
            comedyHits.await().takeIf { it.isNotEmpty() }?.let { categories.add(Category("Comedy Hits", it)) }

            // One shelf per streaming service: its trending films and series, interleaved.
            val service = com.ominix.vidiio.data.repository.BrowseOptions.services.map { svc ->
                async {
                    val films = runCatching { streamingCatalog(BrowseKind.MOVIES, svc.id) }.getOrDefault(emptyList())
                    val shows = runCatching { streamingCatalog(BrowseKind.TV, svc.id) }.getOrDefault(emptyList())
                    val mixed = (0 until maxOf(films.size, shows.size)).flatMap { i -> listOfNotNull(films.getOrNull(i), shows.getOrNull(i)) }.take(24)
                    Category("Trending on ${svc.label}", mixed)
                }
            }.awaitAll().filter { it.movies.isNotEmpty() }
            categories.addAll(service)
            // A user-installed copy of the Streaming Catalogs addon would repeat the shelves above; skip its rows.
            val serviceNames = com.ominix.vidiio.data.repository.BrowseOptions.services.map { it.label.lowercase() }
            categories.addAll(stremioCatalogs.await().filter { c -> c.movies.isNotEmpty() && c.name.split(" ").filter { it.isNotBlank() }.joinToString(" ").lowercase() !in serviceNames })
            categories
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MovieRepository", "Error fetching from TMDB", e)
            emptyList()
        }
    }

    /** Trending titles on one streaming service, for the Movies (movie) or TV (series) page. */
    suspend fun streamingCatalog(kind: BrowseKind, serviceId: String): List<Movie> {
        val kids = isKidsMode()
        return cached(if (kids) "kids-svc-$kind-$serviceId" else "svc-$kind-$serviceId") {
            val type = if (kind == BrowseKind.MOVIES) "movie" else "series"
            addonManager.streamingCatalog(type, serviceId)
                // Hand these over as ordinary TMDB titles when the addon names the TMDB id: the Details page then
                // has seasons, episodes and the full set of links for them. (The catalogue addon itself has no
                // episode lists or streams.)
                .map { meta ->
                    val type = if (kind == BrowseKind.MOVIES) MovieType.MOVIE else MovieType.TV_SHOW
                    val base = meta.toMovie(type)
                    meta.tmdbId?.let { base.copy(id = it.toString(), source = "tmdb", imdbId = meta.id.takeIf { id -> id.startsWith("tt") }) } ?: base
                }
                .filter { it.posterUrl.isNotBlank() }
                .forActiveProfile()
        }
    }

    /** One shelf on the Movies or TV page. */
    suspend fun browse(kind: BrowseKind, list: BrowseList): List<Movie> {
        val kids = isKidsMode()
        return cached(if (kids) "kids-browse-$kind-$list" else "browse-$kind-$list") {
            loadBrowse(kind, list, 1).forActiveProfile()
        }
    }

    /** A further page (2, 3, ...) of a browse shelf, for rows that load more as they scroll. Not cached. */
    suspend fun browsePage(kind: BrowseKind, list: BrowseList, page: Int): List<Movie> =
        if (page <= 1) browse(kind, list) else loadBrowse(kind, list, page).forActiveProfile()

    private suspend fun loadBrowse(kind: BrowseKind, list: BrowseList, page: Int): List<Movie> {
        val type = if (kind == BrowseKind.MOVIES) MovieType.MOVIE else MovieType.TV_SHOW
        val response = when (kind) {
            BrowseKind.MOVIES -> when (list) {
                BrowseList.TRENDING -> tmdbService.getTrendingMovies(page)
                BrowseList.POPULAR -> tmdbService.getPopularMovies(page)
                BrowseList.TOP_RATED -> tmdbService.getTopRatedMovies(page)
                BrowseList.FRESH -> tmdbService.getNowPlayingMovies(page)
                BrowseList.SOON -> tmdbService.getUpcomingMovies(page)
            }
            BrowseKind.TV -> when (list) {
                BrowseList.TRENDING -> tmdbService.getTrendingTVShows(page)
                BrowseList.POPULAR -> tmdbService.getPopularTVShows(page)
                BrowseList.TOP_RATED -> tmdbService.getTopRatedTVShows(page)
                BrowseList.FRESH -> tmdbService.getOnTheAirTVShows(page)
                BrowseList.SOON -> tmdbService.getAiringTodayTVShows(page)
            }
        }
        return response.results.map { it.toMovie(type) }.filter { it.posterUrl.isNotBlank() }
    }

    /**
     * The Top 100 Halloween Movies: horror films tagged "halloween" on TMDB with a real number of votes
     * (about 240 qualify), ranked by weighted rating so obscure titles with a handful of perfect scores
     * do not outrank classics. Fetched in parallel and not cached here - the caller keeps it.
     */
    suspend fun halloweenTop100(): List<Movie> {
        if (isKidsMode()) return emptyList()
        return coroutineScope {
            val pages = (1..HALLOWEEN_PAGES).map { page ->
                async {
                    runCatching {
                        tmdbService.discoverMoviesFiltered(
                            withGenres = "27", withKeywords = HALLOWEEN_KEYWORD_ID.toString(),
                            sortBy = "vote_count.desc", voteCountGte = 250, page = page
                        ).results
                    }.getOrDefault(emptyList())
                }
            }
            rankHalloween(pages.awaitAll().flatten()).map { it.toMovie(MovieType.MOVIE) }
        }
    }

    /** One filtered list for the Movies or TV page: any mix of genre or mood, decade, studio or network. */
    suspend fun browseFiltered(kind: BrowseKind, filter: BrowseFilter): List<Movie> {
        val sort = when (filter.sort) {
            BrowseSort.POPULAR -> "popularity.desc"
            BrowseSort.TOP_RATED -> "vote_average.desc"
            BrowseSort.NEWEST -> if (kind == BrowseKind.MOVIES) "primary_release_date.desc" else "first_air_date.desc"
        }
        // Top rated needs a vote floor or it fills with titles rated 10 by three people; Newest must not
        // list films that have not come out.
        val minVotes = when (filter.sort) {
            BrowseSort.TOP_RATED -> if (kind == BrowseKind.MOVIES) 500 else 200
            BrowseSort.NEWEST -> 15
            BrowseSort.POPULAR -> if (kind == BrowseKind.MOVIES) 100 else 50
        }
        val today = java.time.LocalDate.now().toString()
        val from = filter.decade?.let { "${it.startYear}-01-01" }
        val toDate = (filter.decade?.let { "${it.endYear}-12-31" })
            ?: if (filter.sort == BrowseSort.NEWEST) today else null
        val to = toDate?.let { if (it > today) today else it }
        val results = if (kind == BrowseKind.MOVIES) {
            tmdbService.discoverMoviesFiltered(
                withGenres = filter.genreExpr, withCompanies = filter.provider?.id?.toString(),
                releaseGte = from, releaseLte = to, sortBy = sort, voteCountGte = minVotes
            ).results.map { it.toMovie(MovieType.MOVIE) }
        } else {
            tmdbService.discoverTvFiltered(
                withGenres = filter.genreExpr, withNetworks = filter.provider?.id?.toString(),
                airedGte = from, airedLte = to, sortBy = sort, voteCountGte = minVotes
            ).results.map { it.toMovie(MovieType.TV_SHOW) }
        }
        return results.filter { it.posterUrl.isNotBlank() }.forActiveProfile()
    }


    /** A genre shelf: TMDB discover, most popular first. */
    suspend fun browseGenre(kind: BrowseKind, genreId: Int): List<Movie> = when (kind) {
        BrowseKind.MOVIES -> tmdbService.discoverMovies(withGenres = genreId.toString())
            .results.map { it.toMovie(MovieType.MOVIE) }
        BrowseKind.TV -> tmdbService.discoverTV(withGenres = genreId.toString(), voteCountGte = 100)
            .results.map { it.toMovie(MovieType.TV_SHOW) }
    }.filter { it.posterUrl.isNotBlank() }.forActiveProfile()

    /** Bundled anime addons (One Pace) as shelves, for the Anime page. */
    suspend fun getAnimeAddonCategories(): List<Category> = if (isKidsMode()) emptyList() else runCatching {
        addonManager.getAnimeCatalogs().map { (name, metas) ->
            Category(name, metas.map { it.toMovie(forceType = MovieType.TV_SHOW) })
        }.filter { it.movies.isNotEmpty() }
    }.getOrDefault(emptyList())

    /**
     * Search that reports a failed lookup instead of returning an empty list. The search screen
     * needs to tell "nothing matched" apart from "the request failed", otherwise being offline
     * looks exactly like a query with no results.
     */
    suspend fun searchOrThrow(query: String): List<Movie> =
        tmdbService.searchMulti(query).results.map { it.toMovie() }.forActiveProfile()

    /**
     * Titles like [movie], for the details screen's rail. Empty when TMDB has nothing to say, or
     * when the id did not come from TMDB at all (an addon id is not a number).
     */
    suspend fun similar(movie: Movie): List<Movie> = try {
        val kids = isKidsMode()
        cached(if (kids) "kids-similar-${movie.type}-${movie.id}" else "similar-${movie.type}-${movie.id}") {
            val tmdbId = movie.id.toIntOrNull()
            if (tmdbId == null) {
                emptyList()
            } else {
                val results = if (movie.type == MovieType.TV_SHOW) {
                    tmdbService.getSimilarTVShows(tmdbId)
                } else {
                    tmdbService.getSimilarMovies(tmdbId)
                }
                results.results
                    .filter { it.id.toString() != movie.id && it.posterPath != null }
                    .map { it.toMovie(movie.type) }
                    .take(20)
                    .forActiveProfile()
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e("MovieRepository", "Similar error", e)
        emptyList()
    }

    suspend fun search(query: String): List<Movie> = try {
        searchOrThrow(query)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e("MovieRepository", "Search error", e)
        emptyList()
    }

    suspend fun getTrendingAnime(): List<Movie> = try {
        tmdbService.discoverTV(
            withGenres = "16",
            withOriginalLanguage = "ja",
            sortBy = "popularity.desc"
        ).results.map { it.toMovie(MovieType.TV_SHOW) }.forActiveProfile()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "MovieRepository failed", e)
        emptyList()
    }

    suspend fun getMovieDetails(movie: Movie): Movie {
        val activeProvider = settingsRepository.metadataProviderFlow.first()

        if (activeProvider == MetadataProvider.TRAKT && traktService != null) {
            return try {
                if (movie.type == MovieType.MOVIE) {
                    val trakt = traktService.getMovieDetails(movie.id)
                    movie.copy(
                        title = trakt.title ?: movie.title,
                        synopsis = trakt.overview ?: movie.synopsis,
                        rating = trakt.rating?.toDouble() ?: movie.rating,
                        year = trakt.year ?: movie.year
                    )
                } else {
                    val trakt = traktService.getShowDetails(movie.id)
                    val seasons = traktService.getShowSeasons(movie.id).map { s ->
                        Season(
                            seasonNumber = s.number ?: 1,
                            episodes = s.episodes.orEmpty().map { ep ->
                                Episode(
                                    id = ep.ids?.imdb ?: ep.ids?.trakt?.toString() ?: "${movie.id}_${ep.season}_${ep.number}",
                                    name = ep.title ?: "Episode ${ep.number ?: 1}",
                                    overview = ep.overview,
                                    episodeNumber = ep.number ?: 1,
                                    seasonNumber = s.number ?: 1
                                )
                            }
                        )
                    }
                    movie.copy(
                        title = trakt.title ?: movie.title,
                        synopsis = trakt.overview ?: movie.synopsis,
                        rating = trakt.rating?.toDouble() ?: movie.rating,
                        year = trakt.year ?: movie.year,
                        seasons = seasons
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Trakt getMovieDetails failed", e)
                movie
            }
        }

        if (activeProvider == MetadataProvider.SIMKL && simklService != null) {
            return try {
                if (movie.type == MovieType.MOVIE) {
                    val simkl = simklService.getMovieDetails(movie.id)
                    movie.copy(
                        title = simkl.title ?: movie.title,
                        synopsis = simkl.overview ?: movie.synopsis,
                        posterUrl = simkl.poster?.let { "https://simkl.in/posters/${it}_m.jpg" } ?: movie.posterUrl,
                        backdropUrl = simkl.fanart?.let { "https://simkl.in/fanart/${it}_medium.jpg" } ?: movie.backdropUrl,
                        year = simkl.year ?: movie.year
                    )
                } else {
                    val simkl = simklService.getTVDetails(movie.id)
                    val episodesList = simklService.getTVEpisodes(movie.id)
                    val seasonsMap = episodesList.groupBy { it.season ?: 1 }
                    val seasons = seasonsMap.map { (seasonNo, eps) ->
                        Season(
                            seasonNumber = seasonNo,
                            episodes = eps.map { ep ->
                                Episode(
                                    id = "${movie.id}_${ep.season}_${ep.episode}",
                                    name = ep.title ?: "Episode ${ep.episode ?: 1}",
                                    overview = ep.description,
                                    episodeNumber = ep.episode ?: 1,
                                    seasonNumber = seasonNo
                                )
                            }
                        )
                    }
                    movie.copy(
                        title = simkl.title ?: movie.title,
                        synopsis = simkl.overview ?: movie.synopsis,
                        posterUrl = simkl.poster?.let { "https://simkl.in/posters/${it}_m.jpg" } ?: movie.posterUrl,
                        backdropUrl = simkl.fanart?.let { "https://simkl.in/fanart/${it}_medium.jpg" } ?: movie.backdropUrl,
                        year = simkl.year ?: movie.year,
                        seasons = seasons
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Simkl getMovieDetails failed", e)
                movie
            }
        }

        if (movie.source == "stremio" || activeProvider == MetadataProvider.ADDONS) {
            return try {
                val stremioType = if (movie.type == MovieType.MOVIE) "movie" else "series"
                val meta = withTimeoutOrNull(12_000) { addonManager.getMeta(stremioType, movie.id) }
                    ?: return movie
                val fallbackStill = meta.background ?: meta.poster
                val seasons = meta.videos.orEmpty()
                    .groupBy { it.season ?: 1 }
                    .toSortedMap()
                    .map { (seasonNo, vids) ->
                        val sorted = vids.sortedBy { it.episode ?: 0 }
                        val arc = sorted.firstOrNull()?.id?.substringBefore('_')
                            ?.let { ONE_PACE_ARCS[it] }
                        Season(
                            seasonNumber = seasonNo,
                            name = arc,
                            episodes = sorted.mapIndexed { i, v ->
                                Episode(
                                    id = v.id,
                                    name = v.title ?: v.name ?: "Episode ${v.episode ?: i + 1}",
                                    overview = v.overview,
                                    episodeNumber = v.episode ?: (i + 1),
                                    seasonNumber = seasonNo,
                                    stillPath = v.thumbnail ?: fallbackStill
                                )
                            }
                        )
                    }
                movie.copy(
                    title = meta.name,
                    posterUrl = meta.poster ?: movie.posterUrl,
                    backdropUrl = meta.background ?: movie.backdropUrl,
                    synopsis = meta.description ?: movie.synopsis,
                    seasons = seasons
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("MovieRepository", "Stremio meta error", e)
                movie
            }
        }

        if (movie.source != "tmdb") {
            val scraper = scrapers.find { it.sourceId == movie.source }
                ?: scrapers.find { it.name.lowercase().contains(movie.source.lowercase()) }
                ?: return movie
            return try {
                withTimeoutOrNull(10000) {
                    scraper.getMovieDetails(movie)
                } ?: movie
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "MovieRepository.getMovieDetails() failed", e)
                movie
            }
        }

        return try {
            val id = movie.id.toIntOrNull() ?: return movie
            if (movie.type == MovieType.MOVIE) {
                val tmdbMovie = tmdbService.getMovieDetails(id)
                tmdbMovie.toMovie(movie.type)
            } else {
                val tmdbMovie = tmdbService.getTVShowDetails(id)
                val seasons = tmdbMovie.seasons?.map { season ->
                    try {
                        tmdbService.getTVSeasonDetails(id, season.seasonNumber).toSeason()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "MovieRepository.getMovieDetails() failed", e)
                        season.toSeason()
                    }
                } ?: emptyList()
                tmdbMovie.toMovie(movie.type).copy(seasons = seasons)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MovieRepository", "Error getting movie details", e)
            movie
        }
    }

    /** One cached search: when it ran, what it found, and anything it had to explain. */
    private data class CachedSources(val foundAt: Long, val sources: List<StreamSource>, val note: String?)

    /** Sources found for an episode, kept briefly so coming back to it (or the next-episode prefetch) is instant. */
    private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, CachedSources>()

    fun getStreamSources(
        movie: Movie,
        episode: Episode? = null,
        /** Called when the addon search has something to explain, e.g. PenguPlay's sign-in clip. */
        onNote: ((String) -> Unit)? = null,
    ): Flow<StreamSource> = kotlinx.coroutines.flow.flow {
        val key = "${movie.id}:${episode?.seasonNumber}:${episode?.episodeNumber}"
        val hit = sourceCache[key]
        if (hit != null && System.currentTimeMillis() - hit.foundAt < SOURCE_TTL_MS) {
            // The note is cached too: a re-scrape served from here must not drop the explanation
            // the first run gave (settings emissions re-run the search straight after it).
            hit.note?.let { onNote?.invoke(it) }
            hit.sources.forEach { emit(it) }
            return@flow
        }
        val found = mutableListOf<StreamSource>()
        var note: String? = null
        loadStreamSources(movie, episode) { n -> note = n; onNote?.invoke(n) }.collect { found += it; emit(it) }
        // Only a complete run is worth keeping; a cancelled one falls out above without reaching here.
        if (found.isNotEmpty()) sourceCache[key] = CachedSources(System.currentTimeMillis(), found.toList(), note)
    }

    private fun loadStreamSources(
        movie: Movie,
        episode: Episode? = null,
        onNote: ((String) -> Unit)? = null,
    ): Flow<StreamSource> = channelFlow {
        val enabledScrapers = getEnabledScrapers()

        // Several scrapers need the IMDb id (Stremio addons) or match better with it.
        // Resolve once and hand every scraper a Movie that carries it.
        val imdbId = if (movie.source == "tmdb") getImdbId(movie) else movie.id.takeIf { it.startsWith("tt") }
        val target = if (imdbId != null) movie.copy(imdbId = imdbId) else movie

        supervisorScope {
            // Scraper sources
            enabledScrapers.forEach { scraper ->
                launch {
                    try {
                        val sources = withTimeoutOrNull(SCRAPER_TIMEOUT_MS) {
                            if (target.type == MovieType.TV_SHOW && episode != null) {
                                scraper.getStreamSources(target, episode)
                            } else {
                                scraper.getStreamSources(target)
                            }
                        }
                        when {
                            sources == null -> Log.w("MovieRepository", "Scraper timed out: ${scraper.name}")
                            sources.isEmpty() -> Log.i("MovieRepository", "Scraper ${scraper.name}: 0 sources")
                            else -> {
                                Log.i("MovieRepository", "Scraper ${scraper.name}: ${sources.size} sources")
                                sources.forEach { send(it.copy(sourceId = scraper.sourceId)) }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("MovieRepository", "Scraper error: ${scraper.name}", e)
                    }
                }
            }

            // Stremio addon sources
            launch {
                try {
                    val stremioType = if (movie.type == MovieType.MOVIE) "movie" else "series"
                    // Addon-native id (e.g. One Pace uses "RO_1" per episode, "pp_onepace" per series).
                    val nativeId: String? = when {
                        movie.source != "stremio" -> null
                        movie.type == MovieType.TV_SHOW && episode != null -> episode.id
                        else -> movie.id
                    }
                    // IMDb-keyed id for the big torrent addons (Torrentio etc).
                    val imdbStremioId: String? = imdbId?.takeIf { it.startsWith("tt") }?.let { tt ->
                        if (movie.type == MovieType.TV_SHOW && episode != null)
                            "$tt:${episode.seasonNumber}:${episode.episodeNumber}"
                        else tt
                    }

                    listOfNotNull(nativeId, imdbStremioId).distinct().forEach { sid ->
                        // Each addon is bounded inside AddonManager. A shared deadline here used to
                        // cancel the whole batch, so one slow addon (PenguPlay) meant no links from
                        // any addon - including the ones that had already answered.
                        val answer = addonManager.getStreams(stremioType, sid)
                        if (answer.penguSignInOnly) {
                            onNote?.invoke(com.ominix.vidiio.data.stremio.SIGN_IN_CLIP_NOTE)
                        }
                        answer.streams.forEach { stremioStream ->
                            stremioStream.toStreamSource()?.let { send(it) }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("MovieRepository", "Stremio error", e)
                }
            }
        }
    }

    /**
     * Resolves a TMDB id to an IMDb id, or returns the id unchanged if it already is one.
     *
     * Public because subtitle lookup needs it too: Stremio subtitle addons key on IMDb
     * ids (OpenSubtitles v3 declares idPrefixes ["tt"]), and a Movie that came from TMDB
     * carries a numeric id and a null imdbId until something resolves it.
     */
    suspend fun getImdbId(movie: Movie): String? {
        return try {
            if (movie.id.startsWith("tt")) return movie.id
            val id = movie.id.toIntOrNull() ?: return null
            if (movie.type == MovieType.MOVIE) {
                tmdbService.getMovieDetails(id).imdbId
            } else {
                tmdbService.getTVShowDetails(id).externalIds?.imdbId
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "MovieRepository.getImdbId() failed", e)
            null
        }
    }

    private fun StremioMeta.toMovie(forceType: MovieType? = null): Movie {
        return Movie(
            id = id,
            title = name,
            posterUrl = poster ?: "",
            backdropUrl = background,
            synopsis = description,
            year = releaseInfo?.take(4)?.toIntOrNull(),
            rating = imdbRating?.toDoubleOrNull(),
            type = forceType ?: if (type == "movie") MovieType.MOVIE else MovieType.TV_SHOW,
            source = "stremio"
        )
    }

    private fun TMDBMovie.toMovie(defaultType: MovieType? = null): Movie {
        val isMovie = mediaType == "movie" || (defaultType == MovieType.MOVIE) || (title != null && name == null)
        return Movie(
            id = id.toString(),
            title = title ?: name ?: "Unknown",
            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: "",
            backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
                ?: posterPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            synopsis = overview,
            year = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull(),
            rating = voteAverage,
            type = if (isMovie) MovieType.MOVIE else MovieType.TV_SHOW,
            source = "tmdb",
            trailerKey = videos.bestTrailerKey()
        )
    }

    private fun TMDBSeason.toSeason(): Season {
        return Season(
            seasonNumber = seasonNumber,
            episodes = episodes?.map { it.toEpisode() } ?: emptyList()
        )
    }

    private fun TMDBEpisode.toEpisode(): Episode {
        return Episode(
            id = id.toString(),
            name = name,
            overview = overview,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            stillPath = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        )
    }
}

private const val TAG = "MovieRepository"
/** TV content ratings a kids profile may see, from TMDB's US content ratings. */
private val KIDS_TV_RATINGS = setOf("TV-Y", "TV-Y7", "TV-G", "TV-PG")

/** TMDB keyword id for "halloween", and how many pages of it to read (20 films a page). */
private const val HALLOWEEN_KEYWORD_ID = 3335
private const val HALLOWEEN_PAGES = 12
private const val LIST_TTL_MS = 5 * 60 * 1000L
private const val SOURCE_TTL_MS = 10 * 60 * 1000L

/**
 * Orders candidate Halloween films best-first by weighted rating and keeps the top 100. Pure, so it
 * can be tested; drops duplicates and films with no poster.
 */
internal fun rankHalloween(candidates: List<com.ominix.vidiio.data.model.tmdb.TMDBMovie>): List<com.ominix.vidiio.data.model.tmdb.TMDBMovie> =
    candidates
        .distinctBy { it.id }
        .filter { !it.posterPath.isNullOrBlank() }
        .sortedByDescending { weightedRating(it.voteAverage ?: 0.0, it.voteCount ?: 0) }
        .take(100)

/** Which of the dedicated browse pages a request is for. */
enum class BrowseKind { MOVIES, TV }

/** The standard shelves every browse page has. What FRESH and SOON mean depends on the page. */
enum class BrowseList {
    TRENDING, POPULAR, TOP_RATED,

    /** Movies: in theatres now. TV: currently on the air. */
    FRESH,

    /** Movies: coming soon. TV: airing today. */
    SOON,
}
