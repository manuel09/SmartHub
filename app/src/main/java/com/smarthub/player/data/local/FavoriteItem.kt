package com.smarthub.player.data.local

import androidx.compose.runtime.Immutable
import com.smarthub.player.data.model.Movie

@Immutable
data class FavoriteItem(
    val movieId: Int,
    val type: String, // "movie" or "tv"
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val overview: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMovie(): Movie = Movie(
        id = movieId,
        title = if (type == "movie") title else null,
        name = if (type == "tv") title else null,
        overview = overview ?: "",
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        mediaType = type
    )
}
