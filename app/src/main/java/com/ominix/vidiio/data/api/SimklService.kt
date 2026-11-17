package com.ominix.vidiio.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

data class SimklIds(
    val simkl: Int? = null,
    val imdb: String? = null,
    val tmdb: Int? = null
)

data class SimklMovie(
    val title: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val users_recommendations: Float? = null,
    val poster: String? = null,
    val fanart: String? = null,
    val ids: SimklIds? = null
)

data class SimklEpisode(
    val season: Int? = null,
    val episode: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val img: String? = null
)

interface SimklService {

    @GET("movies/{id}?extended=full")
    suspend fun getMovieDetails(
        @Path("id") id: String,
        @Header("simkl-api-key") clientId: String = CLIENT_ID
    ): SimklMovie

    @GET("tv/{id}?extended=full")
    suspend fun getTVDetails(
        @Path("id") id: String,
        @Header("simkl-api-key") clientId: String = CLIENT_ID
    ): SimklMovie

    @GET("tv/episodes/{id}")
    suspend fun getTVEpisodes(
        @Path("id") id: String,
        @Header("simkl-api-key") clientId: String = CLIENT_ID
    ): List<SimklEpisode>

    companion object {
        const val CLIENT_ID = "69894e66d3a95782bd596d1eb251e604724a737be74e6cbb732788e2ae446e16"
    }
}
