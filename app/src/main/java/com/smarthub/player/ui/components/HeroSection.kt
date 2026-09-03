package com.smarthub.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.smarthub.player.data.model.Movie
import com.smarthub.player.ui.LocalWindowSize
import com.smarthub.player.ui.theme.VixRed

@Composable
fun HeroSection(
    movie: Movie,
    onPlayClick: () -> Unit
) {
    val windowSize = LocalWindowSize.current
    val height: Dp = when {
        windowSize.isExpanded -> 480.dp
        windowSize.isMedium -> 500.dp
        else -> 550.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data("https://image.tmdb.org/t/p/w1280${movie.backdropPath}")
                .size(coil.size.Size(1280, 720))
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color(0xFF0F0F0F).copy(alpha = 0.8f),
                            Color(0xFF0F0F0F)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 40.dp)
        ) {
            Text(
                text = (movie.title ?: movie.name ?: "").uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                lineHeight = 38.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = VixRed),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "RIPRODUCI ORA",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}
