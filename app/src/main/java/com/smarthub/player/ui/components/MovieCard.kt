package com.smarthub.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.smarthub.player.data.model.Movie
import com.smarthub.player.ui.focusBorder

@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .focusBorder(color = Color.White, cornerRadius = 12.dp, borderWidth = 3.dp)
            .padding(6.dp)
            .clip(RoundedCornerShape(12.dp))
            .focusable()
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data("https://image.tmdb.org/t/p/w342${movie.posterPath}")
                .size(coil.size.Size(500, 750))
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(false)
                .build(),
            contentDescription = movie.title,
            modifier = Modifier.fillMaxSize().background(Color(0xFF151515)),
            contentScale = ContentScale.Crop
        )

        // Rating Badge
        if (movie.voteAverage > 0) {
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                shape = RoundedCornerShape(bottomStart = 8.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = String.format("%.1f", movie.voteAverage),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
