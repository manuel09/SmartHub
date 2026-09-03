package com.smarthub.player.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class Movie(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double,
    @SerializedName("media_type") val mediaType: String? = "movie"
)

@Immutable
data class MovieResponse(
    @SerializedName("results") val results: List<Movie>
)

@Immutable
data class Cast(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("character") val character: String,
    @SerializedName("profile_path") val profilePath: String?
)

@Immutable
data class CreditsResponse(
    @SerializedName("cast") val cast: List<Cast>
)

@Immutable
data class Season(
    @SerializedName("id") val id: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episode_count") val episodeCount: Int,
    @SerializedName("name") val name: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("episodes") val episodes: List<Episode>? = null
)

@Immutable
data class Episode(
    @SerializedName("id") val id: Int,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String,
    @SerializedName("still_path") val stillPath: String?,
    @SerializedName("air_date") val airDate: String?
)

@Immutable
data class TvShowDetails(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String,
    @SerializedName("seasons") val seasons: List<Season>
)

@Immutable
data class SeasonResponse(
    @SerializedName("episodes") val episodes: List<Episode>
)

@Immutable
data class Genre(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

@Immutable
data class GenreResponse(
    @SerializedName("genres") val genres: List<Genre>
)
