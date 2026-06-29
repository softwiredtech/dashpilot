package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.controls.ControlActionButton
import com.softwiredtech.dashpilot.ui.controls.vehicleControls
import com.softwiredtech.dashpilot.ui.theme.DarkColors

/**
 * Vehicle control screen. Exposes the available DashKit commands as single
 * toggle/action buttons. Disabled when there is no active DashKit connection.
 * Long-pressing a control pins it to the home screen.
 */
@Composable
fun ControlScreen(
    bleManager: DashKitBleManager?,
    pinnedControlId: String?,
    onTogglePin: (String) -> Unit,
    onBack: () -> Unit
) {
    // In debug builds the controls behave as if connected (commands are
    // simulated) so the flow can be exercised without a DashKit.
    val enabled = bleManager != null || BuildConfig.DEBUG

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Controls",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect to DashKit to send commands",
                    color = DarkColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            vehicleControls.forEachIndexed { index, action ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ControlActionButton(
                    action = action,
                    pinned = action.id == pinnedControlId,
                    enabled = enabled,
                    onClick = { action.perform(bleManager) },
                    onLongClick = { onTogglePin(action.id) }
                )
            }
        }
    }
}
