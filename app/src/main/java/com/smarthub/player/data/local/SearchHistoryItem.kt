package com.smarthub.player.data.local

import androidx.compose.runtime.Immutable

@Immutable
data class SearchHistoryItem(
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
