package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

private data class MainTab(val label: Int, val icon: ImageVector)
private val mainTabs = listOf(
    MainTab(R.string.home_drive, Icons.Rounded.DirectionsCar),
    MainTab(R.string.navigation_commands, Icons.Rounded.Tune),
    MainTab(R.string.home_automations, Icons.Rounded.AutoAwesome),
    MainTab(R.string.settings_title, Icons.Rounded.Settings)
)

@Composable
fun AppBottomNavigation(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = DarkColors.Surface) {
        mainTabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentColor, selectedTextColor = AccentColor,
                    indicatorColor = AccentColor.copy(alpha = 0.14f),
                    unselectedIconColor = DarkColors.TextMuted, unselectedTextColor = DarkColors.TextMuted
                )
            )
        }
    }
}

@Composable
fun AppNavigationRail(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().width(108.dp),
        containerColor = DarkColors.Surface,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
    ) {
        Spacer(Modifier.weight(1f))
        mainTabs.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = selectedTab == index,
                onClick = { onSelect(index) },
                modifier = Modifier.height(64.dp),
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), fontSize = 11.sp, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = AccentColor, selectedTextColor = AccentColor,
                    indicatorColor = AccentColor.copy(alpha = 0.14f),
                    unselectedIconColor = DarkColors.TextMuted, unselectedTextColor = DarkColors.TextMuted
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
