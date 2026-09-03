package com.smarthub.player.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.runtime.compositionLocalOf

data class WindowSize(
    val widthClass: WindowWidthSizeClass,
    val heightClass: WindowHeightSizeClass
) {
    val isCompact: Boolean get() = widthClass == WindowWidthSizeClass.Compact
    val isMedium: Boolean get() = widthClass == WindowWidthSizeClass.Medium
    val isExpanded: Boolean get() = widthClass == WindowWidthSizeClass.Expanded
    val isTablet: Boolean get() = widthClass != WindowWidthSizeClass.Compact
}

val LocalWindowSize = compositionLocalOf { WindowSize(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Compact) }
