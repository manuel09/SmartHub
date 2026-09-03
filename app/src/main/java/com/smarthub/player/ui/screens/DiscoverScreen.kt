package com.smarthub.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthub.player.data.model.Movie
import com.smarthub.player.ui.MovieViewModel
import com.smarthub.player.ui.components.GenreChips
import com.smarthub.player.ui.components.MovieCard
import com.smarthub.player.ui.theme.VixRed

@Composable
fun DiscoverScreen(
    viewModel: MovieViewModel,
    onMovieClick: (Movie) -> Unit
) {
    val genres by viewModel.genres.collectAsState()
    val selectedGenreId by viewModel.selectedGenreId.collectAsState()
    val genreMovies by viewModel.genreMovies.collectAsState()
    val genreTvShows by viewModel.genreTvShows.collectAsState()
    val isLoadingGenre by viewModel.isLoadingGenre.collectAsState()

    var mode by remember { mutableStateOf(DiscoverMode.Movies) }

    LaunchedEffect(Unit) {
        if (genres.isEmpty()) viewModel.fetchGenres()
    }

    val movies = if (mode == DiscoverMode.Movies) genreMovies else genreTvShows

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F)).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "SCOPRI",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            // Toggle Film / Serie TV
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiscoverModeToggle(
                    label = "Film",
                    selected = mode == DiscoverMode.Movies,
                    onClick = { mode = DiscoverMode.Movies }
                )
                DiscoverModeToggle(
                    label = "Serie TV",
                    selected = mode == DiscoverMode.Tv,
                    onClick = { mode = DiscoverMode.Tv }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Genere chips
            GenreChips(
                genres = genres,
                selectedGenreId = selectedGenreId,
                onGenreSelected = { id -> viewModel.selectGenre(id) }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoadingGenre -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VixRed)
                        }
                    }

                    selectedGenreId == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Scegli una categoria per iniziare",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }

                    movies.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Nessun risultato in questa categoria",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(movies, key = { it.id }) { movie ->
                                MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DiscoverMode { Movies, Tv }

@Composable
private fun DiscoverModeToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) VixRed else Color(0xFF1A1A1A),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
