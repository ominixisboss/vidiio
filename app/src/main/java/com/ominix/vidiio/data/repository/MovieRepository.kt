package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.api.TMDBService
import com.ominix.vidiio.data.db.KidsRatingDao
import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.KidsMaturity
import com.ominix.vidiio.data.model.KidsRatingVerdict
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
import kotlinx.coroutines.delay
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
    private val profileRepository: ProfileRepository? = null,
    private val kidsRatingDao: KidsRatingDao? = null,
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

    /** Verdicts already looked up, so a title seen on several shelves costs one request. */
    private val kidsRatingCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Caps concurrent certification lookups: one Kids page opens a dozen shelves at once. */
    private val kidsLookupLimiter = Semaphore(6)

    private enum class KidsVerdict { ALLOWED, BLOCKED, UNCHECKED }

    /** One search's allowed results, plus how many could not be checked because TMDB failed. */
    data class SearchResult(val movies: List<Movie>, val unchecked: Int = 0)

    /**
     * The ceiling for the active profile, or null for an adult profile.
     *
     * If the profile cannot be read the answer is the stricter kids tier, never "adult": a
     * database hiccup must not switch every filter off.
     */
    private suspend fun activeKidsMaturity(): KidsMaturity? = try {
        val profile = profileRepository?.getActiveProfile()
        if (profile?.isKids == true) KidsMaturity.fromDb(profile.kidsMaturity) else null
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Could not read the active profile; keeping kids restrictions on", e)
        KidsMaturity.LITTLE
    }

    /**
     * Whether the active profile may open [movie]. Adults may open anything; a kids profile only
     * gets titles whose US rating is within its ceiling. A title whose rating could not be
     * checked is blocked, and the failure is not remembered.
     */
    suspend fun isAllowedForActiveProfile(movie: Movie): Boolean {
        val maturity = activeKidsMaturity() ?: return true
        return kidsVerdict(movie, maturity) == KidsVerdict.ALLOWED
    }

    /** Drops everything the active kids profile must not see. Answer cached per title and level. */
    private suspend fun List<Movie>.forActiveProfile(): List<Movie> = filterForActiveProfile().movies

    private suspend fun List<Movie>.filterForActiveProfile(): SearchResult {
        if (isEmpty()) return SearchResult(this)
        val maturity = activeKidsMaturity() ?: return SearchResult(this)
        var unchecked = 0
        val allowed = mutableListOf<Movie>()
        for (batch in chunked(10)) {
            val verdicts = coroutineScope {
                batch.map { movie -> async { movie to kidsVerdict(movie, maturity) } }.awaitAll()
            }
            unchecked += verdicts.count { it.second == KidsVerdict.UNCHECKED }
            allowed += verdicts.filter { it.second == KidsVerdict.ALLOWED }.map { it.first }
        }
        return SearchResult(allowed, unchecked)
    }

    private suspend fun kidsVerdict(movie: Movie, maturity: KidsMaturity): KidsVerdict {
        // Anything not from TMDB carries no age rating this app can verify.
        if (movie.source != "tmdb") return KidsVerdict.BLOCKED
        val tmdbId = movie.id.toIntOrNull() ?: return KidsVerdict.BLOCKED
        val key = "${maturity.name}-${movie.type}-$tmdbId"

        kidsRatingCache[key]?.let { cached ->
            return if (cached) KidsVerdict.ALLOWED else KidsVerdict.BLOCKED
        }

        val dao = kidsRatingDao
        if (dao != null) {
            val stored = runCatching { dao.get(key) }.getOrNull()
            if (stored != null && System.currentTimeMillis() - stored.checkedAt < KIDS_RATING_TTL_MS) {
                kidsRatingCache[key] = stored.allowed
                return if (stored.allowed) KidsVerdict.ALLOWED else KidsVerdict.BLOCKED
            }
        }

        val allowed = lookupKidSafe(tmdbId, movie.type, maturity) ?: return KidsVerdict.UNCHECKED
        kidsRatingCache[key] = allowed
        if (dao != null) {
            runCatching {
                dao.put(KidsRatingVerdict(key, allowed, System.currentTimeMillis()))
                dao.pruneBefore(System.currentTimeMillis() - KIDS_RATING_TTL_MS)
            }
        }
        return if (allowed) KidsVerdict.ALLOWED else KidsVerdict.BLOCKED
    }

    /** One retry, because a dropped connection is not the same as an unrated title. */
    private suspend fun lookupKidSafe(tmdbId: Int, type: MovieType, maturity: KidsMaturity): Boolean? {
        repeat(2) { attempt ->
            try {
                return kidsLookupLimiter.withPermit { computeKidSafe(tmdbId, type, maturity) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (attempt == 1) {
                    Log.w(TAG, "Kids rating lookup failed for $type/$tmdbId", e)
                    return null
                }
                delay(250)
            }
        }
        return null
    }

    private suspend fun computeKidSafe(tmdbId: Int, type: MovieType, maturity: KidsMaturity): Boolean =
        when (type) {
            MovieType.MOVIE -> {
                val releases = tmdbService.getMovieReleaseDates(tmdbId).results
                    .firstOrNull { it.country == "US" }
                    ?.releaseDates.orEmpty()
                    .map { UsRelease(type = it.type, certification = it.certification) }
                isMovieCertificationAllowed(representativeMovieCertification(releases), maturity)
            }
            MovieType.TV_SHOW -> {
                val rating = tmdbService.getTvContentRatings(tmdbId).results
                    .firstOrNull { it.country == "US" }
                    ?.rating
                isTvRatingAllowed(rating, maturity)
            }
        }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        val hit = listCache[key]
        if (hit != null && System.currentTimeMillis() - hit.first < LIST_TTL_MS) return hit.second as T
        return load().also { if (it !is List<*> || it.isNotEmpty()) listCache[key] = System.currentTimeMillis() to it }
    }

    suspend fun getHomeCategories(): List<Category> {
        val maturity = activeKidsMaturity()
        return cached(if (maturity != null) "home-kids-${maturity.name}" else "home") {
            if (maturity != null) loadKidsHomeCategories(maturity) else loadHomeCategories()
        }
    }

    /**
     * Kids Home only carries certified titles: films from TMDB discover with a US certification at
     * PG or below, and TV shows whose US content rating is TV-PG or gentler.
     */
    private suspend fun loadKidsHomeCategories(maturity: KidsMaturity): List<Category> = coroutineScope {
        fun movieRow(genres: String?, sort: String = "popularity.desc", minVotes: Int = 100) = async {
            runCatching {
                tmdbService.discoverMoviesFiltered(
                    withGenres = genres,
                    certificationCountry = "US",
                    certificationLte = maturity.movieMax,
                    includeAdult = false,
                    sortBy = sort,
                    voteCountGte = minVotes
                ).results.map { it.toMovie(MovieType.MOVIE) }.filter { it.posterUrl.isNotBlank() }
                    // Discover's certification filter uses whichever release is listed; the
                    // strictest-cert rule still has to run per title, exactly as on Details.
                    .forActiveProfile()
            }
        }

        fun tvRow(genres: String, minVotes: Int = 20) = async {
            runCatching {
                tmdbService.discoverTvFiltered(withGenres = genres, voteCountGte = minVotes)
                    .results.map { it.toMovie(MovieType.TV_SHOW) }
                    .filter { it.posterUrl.isNotBlank() }
                    .forActiveProfile()
            }
        }

        val rows = listOf(
            "Family Movies" to movieRow(null),
            "Animated Movies" to movieRow("16"),
            "Adventure for Kids" to movieRow("12"),
            "Top Rated Family Movies" to movieRow(null, sort = "vote_average.desc", minVotes = 500),
            "Family Comedies" to movieRow("35"),
            "Kids TV Shows" to tvRow("10762"),
            "Family TV Shows" to tvRow("10751"),
            "Kid-Safe Anime" to async {
                runCatching {
                    tmdbService.discoverTV(
                        withGenres = "16",
                        withOriginalLanguage = "ja",
                        sortBy = "popularity.desc",
                        voteCountGte = 100
                    ).results.map { it.toMovie(MovieType.TV_SHOW) }
                        .filter { it.posterUrl.isNotBlank() }
                        .forActiveProfile()
                }
            },
        )

        val settled = rows.map { (name, answer) -> name to answer.await() }
        // A Home that is empty because the network failed must be able to say so; only a
        // successful-but-empty answer means there is genuinely nothing to show.
        if (settled.all { it.second.isFailure }) {
            throw settled.first().second.exceptionOrNull()
                ?: IllegalStateException("Kids home could not be loaded")
        }
        settled.mapNotNull { (name, answer) ->
            answer.getOrNull()?.takeIf { it.isNotEmpty() }?.let { Category(name, it) }
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
        val maturity = activeKidsMaturity()
        return cached(if (maturity != null) "kids-${maturity.name}-svc-$kind-$serviceId" else "svc-$kind-$serviceId") {
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
        val maturity = activeKidsMaturity()
        return cached(if (maturity != null) "kids-${maturity.name}-browse-$kind-$list" else "browse-$kind-$list") {
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
        if (activeKidsMaturity() != null) return emptyList()
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
    suspend fun getAnimeAddonCategories(): List<Category> = if (activeKidsMaturity() != null) emptyList() else runCatching {
        addonManager.getAnimeCatalogs().map { (name, metas) ->
            Category(name, metas.map { it.toMovie(forceType = MovieType.TV_SHOW) })
        }.filter { it.movies.isNotEmpty() }
    }.getOrDefault(emptyList())

    /**
     * Search that reports a failed lookup instead of returning an empty list. The search screen
     * needs to tell "nothing matched" apart from "the request failed", otherwise being offline
     * looks exactly like a query with no results.
     */
    suspend fun searchOrThrow(query: String): SearchResult =
        tmdbService.searchMulti(query).results.map { it.toMovie() }
            .filterForActiveProfile()
            .let { SearchResult(it.movies, it.unchecked) }

    /**
     * Titles like [movie], for the details screen's rail. Empty when TMDB has nothing to say, or
     * when the id did not come from TMDB at all (an addon id is not a number).
     */
    suspend fun similar(movie: Movie): List<Movie> = try {
        val maturity = activeKidsMaturity()
        cached(if (maturity != null) "kids-${maturity.name}-similar-${movie.type}-${movie.id}" else "similar-${movie.type}-${movie.id}") {
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
        searchOrThrow(query).movies
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
        // The cache belongs to the configured scrapers and addons as well as the episode.
        // Without this, adding an addon in Settings within the cache window kept serving
        // the old list, so the new addon's stream paths were never looked up.
        val key = sourceCacheKey(
            movieId = movie.id,
            seasonNumber = episode?.seasonNumber,
            episodeNumber = episode?.episodeNumber,
            enabledSources = settingsRepository.sourcesFlow.first(),
            addonUrls = settingsRepository.stremioAddonsFlow.first(),
        )
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

/** How long a cached age-rating answer stays valid. */
private const val KIDS_RATING_TTL_MS = 30L * 24 * 60 * 60 * 1000

/** TMDB keyword id for "halloween", and how many pages of it to read (20 films a page). */
private const val HALLOWEEN_KEYWORD_ID = 3335
private const val HALLOWEEN_PAGES = 12
private const val LIST_TTL_MS = 5 * 60 * 1000L
private const val SOURCE_TTL_MS = 10 * 60 * 1000L

/**
 * Cache identity for one source search: the title, the episode, and the installed
 * scraper/addon configuration.
 *
 * The configuration belongs in the key because adding an addon in Settings must not
 * keep serving the list cached before it existed - the key changes, the old entry is
 * missed, and every installed addon is queried.
 */
internal fun sourceCacheKey(
    movieId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    enabledSources: Collection<String>,
    addonUrls: Collection<String>,
): String = buildString {
    append(movieId)
    append(':').append(seasonNumber)
    append(':').append(episodeNumber)
    append('|').append(enabledSources.sorted().joinToString(","))
    append('|').append(addonUrls.sorted().joinToString(","))
}

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
