package com.ominix.vidiio.data.scraper

import android.util.Log
import com.ominix.vidiio.data.model.Category
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.data.model.Movie
import com.ominix.vidiio.data.model.MovieType
import com.ominix.vidiio.data.model.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AllInOneVidiioScraper(private val client: OkHttpClient) : Scraper {
    override val name: String = "All in One Vidiio"
    override val sourceId: String = "allinone_vidiio"
    override val baseUrl: String = "https://raw.githubusercontent.com/NuvioPlugin/All-in-One-Nuvio/main/"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    override suspend fun getHomeCategories(): List<Category> = emptyList()
    override suspend fun search(query: String): List<Movie> = emptyList()
    override suspend fun getMovieDetails(movie: Movie): Movie = movie

    override suspend fun getStreamSources(movie: Movie, episode: Episode?): List<StreamSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<StreamSource>()
        val isTv = movie.type == MovieType.TV_SHOW
        val imdbId = movie.imdbId?.takeIf { it.startsWith("tt") } ?: movie.id.takeIf { it.startsWith("tt") }
        val tmdbId = movie.id.filter { it.isDigit() }.takeIf { it.isNotEmpty() }

        val s = episode?.seasonNumber ?: 1
        val e = episode?.episodeNumber ?: 1

        coroutineScope {
            val jobs = listOf(
                async { fetchAutoEmbed(tmdbId, isTv, s, e) },
                async { fetchVidSrcPro(tmdbId, imdbId, isTv, s, e) },
                async { fetchFilmPalace(tmdbId, isTv, s, e) },
                async { fetchCineZone(movie.title, movie.year, isTv, s, e) },
                async { fetchOneShows(imdbId, tmdbId, isTv, s, e) },
                async { fetchMultiEmbed(tmdbId, isTv, s, e) }
            )
            jobs.awaitAll().forEach { sources.addAll(it) }
        }

        sources.distinctBy { it.url }
    }

    private fun fetchAutoEmbed(tmdbId: String?, isTv: Boolean, season: Int, episode: Int): List<StreamSource> {
        if (tmdbId == null) return emptyList()
        val list = mutableListOf<StreamSource>()
        try {
            val endpoint = if (isTv) "https://autoembed.cc/embed/tv/$tmdbId/$season/$episode"
            else "https://autoembed.cc/embed/movie/$tmdbId"

            val req = Request.Builder().url(endpoint).header("User-Agent", userAgent).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    list.add(
                        StreamSource(
                            serverName = "All in One Vidiio (AutoEmbed 1080p)",
                            url = endpoint,
                            isM3u8 = true,
                            sourceId = sourceId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "AutoEmbed error", e)
        }
        return list
    }

    private fun fetchVidSrcPro(tmdbId: String?, imdbId: String?, isTv: Boolean, season: Int, episode: Int): List<StreamSource> {
        val id = tmdbId ?: imdbId ?: return emptyList()
        val list = mutableListOf<StreamSource>()
        try {
            val path = if (isTv) "tv/$id/$season/$episode" else "movie/$id"
            val embedUrl = "https://vidsrc.pro/embed/$path"
            list.add(
                StreamSource(
                    serverName = "All in One Vidiio (VidSrcPro 4K/1080p)",
                    url = embedUrl,
                    isM3u8 = true,
                    sourceId = sourceId
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "VidSrcPro error", e)
        }
        return list
    }

    private fun fetchFilmPalace(tmdbId: String?, isTv: Boolean, season: Int, episode: Int): List<StreamSource> {
        if (tmdbId == null) return emptyList()
        val list = mutableListOf<StreamSource>()
        try {
            val url = if (isTv) "https://filmpalace.net/embed/tv/$tmdbId/$season/$episode"
            else "https://filmpalace.net/embed/movie/$tmdbId"
            list.add(
                StreamSource(
                    serverName = "All in One Vidiio (FilmPalace HD)",
                    url = url,
                    isM3u8 = true,
                    sourceId = sourceId
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "FilmPalace error", e)
        }
        return list
    }

    private fun fetchCineZone(title: String, year: Int?, isTv: Boolean, season: Int, episode: Int): List<StreamSource> {
        val list = mutableListOf<StreamSource>()
        try {
            val cleanTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val url = "https://cinezone.to/search?keyword=$cleanTitle"
            list.add(
                StreamSource(
                    serverName = "All in One Vidiio (CineZone Multi)",
                    url = url,
                    isM3u8 = false,
                    sourceId = sourceId
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "CineZone error", e)
        }
        return list
    }

    private fun fetchOneShows(imdbId: String?, tmdbId: String?, isTv: Boolean, season: Int, episode: Int): List<StreamSource> {
        val id = tmdbId ?: imdbId ?: return emptyList()
        val list = mutableListOf<StreamSource>()
        try {
            val url = if (isTv) "https://1shows.to/embed/tv/$id/$season/$episode"
            else "https://1shows.to/embed/movie/$id"
            list.add(
                StreamSource(
                    serverName = "All in One Vidiio (1Shows 1080p)",
                    url = url,
                    isM3u8 = true,
                    sourceId = sourceId
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "1Shows error", e)
        }
        return list
    }

    private fun fetchMultiEmbed(tmdbId: String?, isTv: Boolean, season: Int, episode: Int): List<StreamSource> {
        if (tmdbId == null) return emptyList()
        val list = mutableListOf<StreamSource>()
        try {
            val url = if (isTv) "https://multiembed.mov/directstream.php?video_id=$tmdbId&s=$season&e=$episode"
            else "https://multiembed.mov/directstream.php?video_id=$tmdbId"
            list.add(
                StreamSource(
                    serverName = "All in One Vidiio (MultiEmbed Fast)",
                    url = url,
                    isM3u8 = true,
                    sourceId = sourceId
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "MultiEmbed error", e)
        }
        return list
    }

    companion object {
        private const val TAG = "AllInOneVidiioScraper"
    }
}
