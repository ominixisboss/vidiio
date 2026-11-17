package com.ominix.vidiio.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

data class TraktIds(
    val trakt: Int? = null,
    val slug: String? = null,
    val tvdb: Int? = null,
    val imdb: String? = null,
    val tmdb: Int? = null
)

data class TraktMovie(
    val title: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val rating: Float? = null,
    val ids: TraktIds? = null
)

data class TraktEpisode(
    val season: Int? = null,
    val number: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    val rating: Float? = null,
    val ids: TraktIds? = null
)

data class TraktSeason(
    val number: Int? = null,
    val episodes: List<TraktEpisode>? = null
)

interface TraktService {

    @GET("movies/{id}?extended=full")
    suspend fun getMovieDetails(
        @Path("id") id: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String = CLIENT_ID
    ): TraktMovie

    @GET("shows/{id}?extended=full")
    suspend fun getShowDetails(
        @Path("id") id: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String = CLIENT_ID
    ): TraktMovie

    @GET("shows/{id}/seasons?extended=episodes")
    suspend fun getShowSeasons(
        @Path("id") id: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String = CLIENT_ID
    ): List<TraktSeason>

    companion object {
        const val CLIENT_ID = "d601d320d75471d87e07e60e86b24df41f6c4424efb4d241777d1a2eb3a958e0"
    }
}
