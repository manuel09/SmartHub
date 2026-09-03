package com.smarthub.player.data.model

import androidx.compose.runtime.Immutable

data class StreamResponse(
    val streams: List<StreamItem>,
    val altadefinizioneExpired: Boolean = false
)

@Immutable
data class StreamItem(
    val url: String? = null,
    val title: String? = null,
    val name: String? = null,
    val source: String? = null,
    val quality: String? = null,
    val behaviorHints: Map<String, Any>? = null
) {
    val displayLabel: String
        get() {
            val src = source ?: name ?: "Sconosciuto"
            val qual = quality ?: ""
            return if (qual.isNotBlank()) "$src · $qual" else src
        }

    val sourceLabel: String
        get() = source ?: name ?: "Sorgente"

    val qualityLabel: String
        get() = quality ?: "Auto"
}
