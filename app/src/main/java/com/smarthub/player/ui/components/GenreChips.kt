package com.smarthub.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthub.player.data.model.Genre
import com.smarthub.player.ui.theme.VixRed
import com.smarthub.player.ui.theme.SurfaceVariant
import com.smarthub.player.ui.focusBorder

@Composable
fun GenreChips(
    genres: List<Genre>,
    selectedGenreId: Int?,
    onGenreSelected: (Int?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GenreChipItem(
                label = "Tutti",
                isSelected = selectedGenreId == null,
                onClick = { onGenreSelected(null) }
            )
        }
        items(genres, key = { it.id }) { genre ->
            GenreChipItem(
                label = genre.name,
                isSelected = genre.id == selectedGenreId,
                onClick = { onGenreSelected(genre.id) }
            )
        }
    }
}

@Composable
private fun GenreChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .focusBorder(color = Color.White, cornerRadius = 20.dp, borderWidth = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) VixRed else SurfaceVariant)
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
