package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.controls.ControlActionButton
import com.softwiredtech.dashpilot.ui.controls.vehicleControls
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import androidx.compose.ui.res.stringResource
import com.softwiredtech.dashpilot.R

@Composable
fun ControlScreen(
    bleManager: DashKitBleManager?,
    pinnedControlId: String?,
    onTogglePin: (String) -> Unit,
    fingerActions: Map<Int, String>,
    onSetFingerAction: (fingers: Int, id: String?) -> Unit,
    onChangeFingerCount: (from: Int, to: Int) -> Unit,
    onRemoveFingerAction: (fingers: Int) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Debug builds simulate commands without a DashKit.
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
            ScreenHeader(title = stringResource(R.string.controls_title), onBack = onBack)

            Spacer(modifier = Modifier.height(16.dp))
            SegmentedSelector(
                options = listOf(
                    stringResource(R.string.controls_tab_controls),
                    stringResource(R.string.controls_tab_multitouch)
                ),
                selected = selectedTab,
                onSelect = { selectedTab = it },
                containerColor = DarkColors.Surface
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                ControlsTab(
                    bleManager = bleManager,
                    enabled = enabled,
                    pinnedControlId = pinnedControlId,
                    onTogglePin = onTogglePin
                )
            } else {
                MultiTouchSection(
                    fingerActions = fingerActions,
                    onSetFingerAction = onSetFingerAction,
                    onChangeFingerCount = onChangeFingerCount,
                    onRemoveFingerAction = onRemoveFingerAction
                )
            }
        }
    }
}

@Composable
private fun ControlsTab(
    bleManager: DashKitBleManager?,
    enabled: Boolean,
    pinnedControlId: String?,
    onTogglePin: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!enabled) {
            Text(
                text = stringResource(R.string.controls_connect_hint),
                color = DarkColors.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

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

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.controls_pin_tip),
            color = DarkColors.TextSubtle,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
