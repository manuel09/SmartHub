package com.smarthub.player.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.focusBorder(
    color: Color = Color.White,
    cornerRadius: Dp = 8.dp,
    borderWidth: Dp = 3.dp
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { isFocused = it.isFocused }
        .drawBehind {
            if (isFocused) {
                drawRoundRect(
                    color = color.copy(alpha = 0.8f),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth.toPx())
                )
            }
        }
}
