package com.smarthub.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.smarthub.player.data.local.ContinueWatchingItem
import com.smarthub.player.ui.theme.VixRed

@Composable
fun ContinueWatchingSection(
    items: List<ContinueWatchingItem>,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onRemove: (ContinueWatchingItem) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = "CONTINUA A GUARDARE",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${it.movieId}_${it.type}_${it.seasonNumber}_${it.episodeNumber}" }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
    val typeLabel = if (item.type == "tv") "Serie TV" else "Film"
    val epLabel = if (item.type == "tv") "S${item.seasonNumber}E${"%02d".format(item.episodeNumber)}" else null

    Box(modifier = Modifier.width(140.dp)) {
        Column(
            modifier = Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .clickable(onClick = onClick)
        ) {
            Box(modifier = Modifier.width(140.dp).height(90.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(posterUrl)
                        .size(coil.size.Size(300, 170))
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
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(item.progress)
                        .height(3.dp)
                        .background(VixRed)
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeLabel,
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    epLabel?.let {
                        Text(" • $it", color = VixRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
        }
    }
}
