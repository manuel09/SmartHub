package com.smarthub.player.data.local

import androidx.compose.runtime.Immutable
import com.smarthub.player.data.model.Movie

@Immutable
data class ContinueWatchingItem(
    val movieId: Int,
    val type: String, // "movie" or "tv"
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val lastPosition: Long = 0L,
    val duration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMovie(): Movie = Movie(
        id = movieId,
        title = if (type == "movie") title else null,
        name = if (type == "tv") title else null,
        overview = "",
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        mediaType = type
    )

    val progress: Float
        get() = if (duration > 0) lastPosition.toFloat() / duration.toFloat() else 0f
}
