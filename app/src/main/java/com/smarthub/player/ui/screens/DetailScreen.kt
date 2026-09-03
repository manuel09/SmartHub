package com.smarthub.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smarthub.player.data.model.*
import com.smarthub.player.ui.LocalWindowSize
import com.smarthub.player.ui.theme.VixRed
import com.smarthub.player.ui.components.ShimmerCard
import com.smarthub.player.ui.components.ShimmerTextLine
import com.smarthub.player.ui.components.shimmer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    movie: Movie,
    cast: List<Cast>,
    tvDetails: TvShowDetails? = null,
    episodes: List<Episode> = emptyList(),
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onSeasonSelected: (Int) -> Unit = {},
    onBackClick: () -> Unit,
    onPlayClick: (Movie, Episode?) -> Unit,
    isLoadingStream: Boolean = false,
    isLoadingCast: Boolean = false
) {
    val scrollState = rememberScrollState()
    var expandedSeasonNumber by remember { mutableStateOf<Int?>(null) }
    // Map season number -> episodes (fetched on expand)
    val seasonEpisodesMap = remember { mutableStateMapOf<Int, List<Episode>>() }

    val windowSize = LocalWindowSize.current
    val backdropHeight: Dp = when {
        windowSize.isExpanded -> 420.dp
        windowSize.isMedium -> 440.dp
        else -> 400.dp
    }

    // Keep track of episodes loaded from ViewModel per-season
    LaunchedEffect(episodes, expandedSeasonNumber) {
        expandedSeasonNumber?.let { sn ->
            if (episodes.isNotEmpty() && episodes.first().seasonNumber == sn) {
                seasonEpisodesMap[sn] = episodes
            }
        }
    }

    // Auto-expand season 1 for TV shows
    LaunchedEffect(tvDetails) {
        if (tvDetails != null && tvDetails.seasons.isNotEmpty()) {
            val firstReal = tvDetails.seasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                ?: tvDetails.seasons.first().seasonNumber
            expandedSeasonNumber = firstReal
            onSeasonSelected(firstReal)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ─── Hero / Backdrop ───────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(backdropHeight)) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w780${movie.backdropPath}",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF151515)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF0F0F0F).copy(alpha = 0.6f),
                                    Color(0xFF0F0F0F)
                                )
                            )
                        )
                )

                // ─── Back Button (inside backdrop for focus traversal) ───
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }

                // ─── Favorite Button ───
                onToggleFavorite?.let { favCb ->
                    IconButton(
                        onClick = favCb,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Preferiti",
                            tint = if (isFavorite) VixRed else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        text = (movie.title ?: movie.name ?: "").uppercase(),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 30.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            movie.releaseDate?.take(4) ?: "",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Text(" • ", color = Color.Gray)
                        Text(
                            if (movie.title != null) "Film" else "Serie TV",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            " ${"%.1f".format(movie.voteAverage)}",
                            color = Color(0xFFFFD700),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ─── Play Button (only for movies or when no TV) ──────────
            if (tvDetails == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayClick(movie, null) },
                        colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoadingStream
                    ) {
                        if (isLoadingStream) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Caricamento...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Riproduci", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    onToggleFavorite?.let { favCb ->
                        IconButton(
                            onClick = favCb,
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Preferiti",
                                tint = if (isFavorite) VixRed else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Overview ─────────────────────────────────────────────
            Text(
                text = movie.overview,
                color = Color.LightGray.copy(alpha = 0.8f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))

            // ─── Cast ────────────────────────────────────────────────
            Text(
                text = "Cast",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (isLoadingCast && cast.isEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(4) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A)).shimmer())
                            Spacer(Modifier.height(5.dp))
                            ShimmerTextLine(width = 60.dp, height = 10.dp)
                            Spacer(Modifier.height(2.dp))
                            ShimmerTextLine(width = 50.dp, height = 8.dp)
                        }
                    }
                }
            } else if (cast.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(cast, key = { it.id }) { person ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(80.dp)
                        ) {
                            AsyncImage(
                                model = "https://image.tmdb.org/t/p/w185${person.profilePath}",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF151515)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = person.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = person.character,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ─── Seasons & Episodes (Accordion) ───────────────────────
            if (tvDetails != null) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Stagioni",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val visibleSeasons = tvDetails.seasons.filter { it.seasonNumber > 0 }
                visibleSeasons.forEach { season ->
                    val isExpanded = expandedSeasonNumber == season.seasonNumber
                    val epList = seasonEpisodesMap[season.seasonNumber] ?: emptyList()

                    SeasonAccordionItem(
                        season = season,
                        isExpanded = isExpanded,
                        episodes = epList,
                        isLoadingStream = isLoadingStream,
                        onToggle = {
                            if (isExpanded) {
                                expandedSeasonNumber = null
                            } else {
                                expandedSeasonNumber = season.seasonNumber
                                if (!seasonEpisodesMap.containsKey(season.seasonNumber)) {
                                    onSeasonSelected(season.seasonNumber)
                                }
                            }
                        },
                        onEpisodePlay = { episode ->
                            onPlayClick(movie, episode)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SeasonAccordionItem(
    season: Season,
    isExpanded: Boolean,
    episodes: List<Episode>,
    isLoadingStream: Boolean,
    onToggle: () -> Unit,
    onEpisodePlay: (Episode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        // ─── Season Header Card ───────────────────────────────────────
        Surface(
            onClick = onToggle,
            color = Color(0xFF1A1A1A),
            shape = if (isExpanded)
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            else
                RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w92${season.posterPath}",
                    contentDescription = null,
                    modifier = Modifier
                        .width(58.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151515)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = season.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${season.episodeCount} episodi",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ─── Episodes List (expanded) ─────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = Color(0xFF141414),
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (episodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(3) {
                                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A1A)).shimmer())
                                }
                            }
                        }
                    } else {
                        episodes.forEach { episode ->
                            EpisodeRow(
                                episode = episode,
                                isLoadingStream = isLoadingStream,
                                onPlay = { onEpisodePlay(episode) }
                            )
                            Divider(
                                color = Color.White.copy(alpha = 0.05f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun EpisodeRow(
    episode: Episode,
    isLoadingStream: Boolean,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w300${episode.stillPath}",
                contentDescription = null,
                modifier = Modifier
                    .width(110.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF151515)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${season_ep_label(episode.seasonNumber, episode.episodeNumber)} • ${episode.name}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.overview.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = episode.overview,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
            episode.airDate?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, color = Color.Gray.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }

        // Play button
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onPlay,
            modifier = Modifier
                .size(42.dp)
                .background(VixRed, CircleShape),
            enabled = !isLoadingStream
        ) {
            if (isLoadingStream) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Riproduci",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun season_ep_label(season: Int, episode: Int): String = "${season}x${"%02d".format(episode)}"
