package com.smarthub.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smarthub.player.data.model.StreamItem
import com.smarthub.player.ui.theme.VixRed

@Composable
fun SourcePickerDialog(
    streams: List<StreamItem>,
    onSelect: (StreamItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            color = Color(0xFF161616),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Scegli la fonte",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${streams.size} fonte${if (streams.size != 1) "i" else ""} disponibile${if (streams.size != 1) "i" else ""}",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Chiudi", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Stream list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(streams) { index, stream ->
                        StreamOptionRow(
                            stream = stream,
                            isFirst = index == 0,
                            onSelect = { onSelect(stream) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamOptionRow(
    stream: StreamItem,
    isFirst: Boolean,
    onSelect: () -> Unit
) {
    val sourceColor = when (stream.source.orEmpty().lowercase()) {
        "vixsrc" -> VixRed
        "vidxgo" -> Color(0xFF4FC3F7)
        else -> Color.Gray
    }

    val sourceIcon: ImageVector = when (stream.source.orEmpty().lowercase()) {
        "vidxgo" -> Icons.Default.Public
        "vixsrc" -> Icons.Default.Cloud
        else -> Icons.Default.PlayArrow
    }

    Surface(
        onClick = onSelect,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source icon with colored background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(sourceColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    sourceIcon,
                    contentDescription = null,
                    tint = sourceColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stream.sourceLabel,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stream.title != null && stream.title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stream.title,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quality badge
            Box(
                modifier = Modifier
                    .background(
                        if (stream.qualityLabel == "1080p" || stream.qualityLabel == "4K")
                            Color(0xFF1B5E20)
                        else
                            Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = if (stream.qualityLabel == "1080p" || stream.qualityLabel == "4K")
                            Color(0xFF4CAF50)
                        else
                            Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stream.qualityLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
    }

    // Tag for best quality
    if (isFirst) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .offset(y = (-4).dp)
                .background(VixRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                "MIGLIORE",
                color = VixRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}
