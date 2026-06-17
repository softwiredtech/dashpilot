package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

/**
 * Landing screen with two tabs: Connect (data-source setup) and Control
 * (vehicle commands). The Control tab is only useful while a DashKit link is
 * active; ControlScreen handles the disconnected case itself.
 */
@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    bleManager: DashKitBleManager?,
    preselectDashKit: Boolean,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onNext: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Connect", "Control")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkColors.Background,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AccentColor
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    selectedContentColor = Color.White,
                    unselectedContentColor = DarkColors.TextMuted,
                    text = { Text(title) }
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> SetupScreen(
                    connectionStatus = connectionStatus,
                    preselectDashKit = preselectDashKit,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onNext = onNext,
                    onSettingsClick = onSettingsClick
                )
                1 -> ControlScreen(bleManager = bleManager)
            }
        }
    }
}
