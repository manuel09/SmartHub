package com.smarthub.player.data.local

data class WatchProgress(
    val movieId: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val type: String, // "movie" or "tv"
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val lastPosition: Long = 0L,
    val duration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
