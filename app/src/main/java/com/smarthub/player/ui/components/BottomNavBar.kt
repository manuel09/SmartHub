package com.smarthub.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthub.player.ui.theme.VixRed
import com.smarthub.player.ui.theme.GlassBg

@Composable
fun BottomNavBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = GlassBg,
        tonalElevation = 8.dp,
        modifier = Modifier.height(72.dp)
    ) {
        val items = listOf(
            Triple("Home", Icons.Default.Home, 0),
            Triple("Cerca", Icons.Default.Search, 1),
            Triple("Film", Icons.Default.PlayArrow, 2),
            Triple("Serie", Icons.Default.List, 3),
            Triple("Profilo", Icons.Default.Person, 4)
        )

        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 10.sp) },
                selected = selectedItem == index,
                onClick = { onItemSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VixRed,
                    unselectedIconColor = Color.Gray.copy(alpha = 0.6f),
                    indicatorColor = Color.Transparent,
                    selectedTextColor = VixRed,
                    unselectedTextColor = Color.Gray.copy(alpha = 0.6f)
                )
            )
        }
    }
}
