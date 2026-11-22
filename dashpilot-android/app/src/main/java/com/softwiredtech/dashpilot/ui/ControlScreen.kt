package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import com.softwiredtech.dashpilot.ui.controls.ControlCategory
import com.softwiredtech.dashpilot.ui.theme.AccentColor
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
    onBack: () -> Unit,
    isRoot: Boolean = false
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var expandedCategories by rememberSaveable { mutableStateOf(emptyList<String>()) }

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
            if (!isRoot) {
                ScreenHeader(onBack = onBack)
                Spacer(modifier = Modifier.height(16.dp))
            }
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
                    onTogglePin = onTogglePin,
                    expandedCategories = expandedCategories,
                    onToggleCategory = { id ->
                        expandedCategories = if (id in expandedCategories) expandedCategories - id
                        else expandedCategories + id
                    }
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
    onTogglePin: (String) -> Unit,
    expandedCategories: List<String>,
    onToggleCategory: (String) -> Unit
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

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlCategory.entries.forEach { category ->
                val actions = vehicleControls.filter { it.category == category }
                val expanded = category.name in expandedCategories
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "categoryChevron")
                val expansionState = stringResource(if (expanded) R.string.commands_category_expanded else R.string.commands_category_collapsed)
                val toggleLabel = stringResource(if (expanded) R.string.commands_category_collapse else R.string.commands_category_expand)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(16.dp)).background(DarkColors.Surface)
                            .semantics(mergeDescendants = true) { stateDescription = expansionState }
                            .clickable(role = Role.Button, onClickLabel = toggleLabel) { onToggleCategory(category.name) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(category.icon, contentDescription = null, tint = AccentColor, modifier = Modifier.size(24.dp))
                        Text(stringResource(category.titleRes), color = Color.White, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(actions.size.toString(), color = DarkColors.TextMuted, fontSize = 13.sp)
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = DarkColors.TextMuted,
                            modifier = Modifier.size(24.dp).rotate(rotation))
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            actions.forEach { action ->
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
            }
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
