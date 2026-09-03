package com.smarthub.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthub.player.data.model.Movie
import com.smarthub.player.data.local.ContinueWatchingItem
import com.smarthub.player.data.model.Genre
import com.smarthub.player.ui.components.*
import com.smarthub.player.ui.theme.VixRed
import com.smarthub.player.ui.MovieViewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun HomeScreen(
    featuredMovie: Movie,
    sections: Map<String, List<Movie>>,
    onMovieClick: (Movie) -> Unit,
    viewModel: MovieViewModel,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)? = null,
    isLoading: Boolean = false
) {
    val genres by viewModel.genres.collectAsState()
    val selectedGenreId by viewModel.selectedGenreId.collectAsState()
    val genreMovies by viewModel.genreMovies.collectAsState()
    val genreTvShows by viewModel.genreTvShows.collectAsState()
    val isLoadingGenre by viewModel.isLoadingGenre.collectAsState()

    val proxyActive by viewModel.proxyActive.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val isGenreSelected = selectedGenreId != null
    val showShimmer = isLoading && sections.values.all { it.isEmpty() } && continueWatching.isEmpty() && favorites.isEmpty()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        if (showShimmer) {
            ShimmerHomeContent()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    Box {
                        HeroSection(movie = featuredMovie, onPlayClick = { onMovieClick(featuredMovie) })

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 40.dp, end = 16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (proxyActive) Color.Green else Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Proxy: ${if (proxyActive) "Attivo" else "Inattivo"}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item(key = "genres") {
                    GenreChips(
                        genres = genres,
                        selectedGenreId = selectedGenreId,
                        onGenreSelected = { viewModel.selectGenre(it) }
                    )
                }

                if (isGenreSelected) {
                    if (isLoadingGenre) {
                        item(key = "genre_loading") {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                ShimmerTextLine(width = 60.dp, height = 14.dp)
                            }
                        }
                    } else {
                        if (genreMovies.isNotEmpty()) {
                            item(key = "genre_movies") {
                                SectionRow(title = "FILM DEL GENERE", movies = genreMovies, onMovieClick = onMovieClick)
                            }
                        }
                        if (genreTvShows.isNotEmpty()) {
                            item(key = "genre_tv") {
                                SectionRow(title = "SERIE TV DEL GENERE", movies = genreTvShows, onMovieClick = onMovieClick)
                            }
                        }
                        if (genreMovies.isEmpty() && genreTvShows.isEmpty()) {
                            item(key = "genre_empty") {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Nessun risultato per questo genere", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else {
                    if (continueWatching.isNotEmpty()) {
                        item(key = "continue_watching") {
                            ContinueWatchingSection(
                                items = continueWatching,
                                onItemClick = { item -> onMovieClick(item.toMovie()) },
                                onRemove = { item -> viewModel.removeContinueWatching(item.movieId, item.type) }
                            )
                        }
                    }

                    if (favorites.isNotEmpty()) {
                        item(key = "favorites") {
                            SectionRow(
                                title = "PREFERITI",
                                movies = favorites.map { it.toMovie() },
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    sections.forEach { (title, movies) ->
                        item(key = title) {
                            SectionRow(title = title.uppercase(), movies = movies, onMovieClick = onMovieClick)
                        }
                    }
                }

                item(key = "spacer") {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().height(90.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VIXSTREAM",
                        color = com.smarthub.player.ui.theme.VixRed,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        letterSpacing = (-2).sp
                    )
                }
            }
        }
    }
}
