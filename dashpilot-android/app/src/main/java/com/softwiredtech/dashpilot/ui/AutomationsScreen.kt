package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.ui.controls.controlById
import com.softwiredtech.dashpilot.ui.controls.vehicleControls
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

// Finger counts that can be bound to an infotainment gesture (matches the
// firmware's MULTI_FINGER_MIN/MAX_FINGERS).
private val FINGER_COUNTS = 3..5

/**
 * Automations screen. Lets the user enable the wiper-off automation and bind
 * 3-, 4-, and 5-finger infotainment taps each to a vehicle control. Bindings are
 * pushed to the DashKit firmware over BLE (VC_CMD_MULTI_FINGER_ACTION).
 *
 * @param fingerActions current bindings: finger count -> control id.
 */
@Composable
fun AutomationsScreen(
    wiperOffEnabled: Boolean,
    onWiperOffChange: (Boolean) -> Unit,
    fingerActions: Map<Int, String>,
    onSetFingerAction: (fingers: Int, id: String?) -> Unit,
    onChangeFingerCount: (from: Int, to: Int) -> Unit,
    onRemoveFingerAction: (fingers: Int) -> Unit,
    onBack: () -> Unit
) {
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
            ScreenHeader(title = "Automations", onBack = onBack)

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel("Wipers")
            Spacer(modifier = Modifier.height(8.dp))
            AutomationRow(
                icon = Icons.Rounded.WaterDrop,
                title = "Wiper Off",
                subtitle = "Keep wipers disabled automatically",
                checked = wiperOffEnabled,
                onToggle = { onWiperOffChange(!wiperOffEnabled) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            SectionLabel("Multi-touch infotainment trigger")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bind 3-, 4-, or 5-finger infotainment taps to a control",
                color = DarkColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            val usedCounts = fingerActions.keys
            fingerActions.toSortedMap().forEach { (fingers, controlId) ->
                // Allow this row to keep its own count plus any not used elsewhere.
                val fingerOptions = FINGER_COUNTS.filter { it == fingers || it !in usedCounts }
                FingerActionRow(
                    fingers = fingers,
                    controlId = controlId,
                    fingerOptions = fingerOptions,
                    onFingerCountChange = { onChangeFingerCount(fingers, it) },
                    onActionChange = { onSetFingerAction(fingers, it) },
                    onRemove = { onRemoveFingerAction(fingers) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val nextFree = FINGER_COUNTS.firstOrNull { it !in usedCounts }
            if (nextFree != null) {
                AddTriggerButton(
                    onClick = { onSetFingerAction(nextFree, vehicleControls.first().id) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = DarkColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun FingerActionRow(
    fingers: Int,
    controlId: String,
    fingerOptions: List<Int>,
    onFingerCountChange: (Int) -> Unit,
    onActionChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DropdownPicker(
            selectedLabel = fingers.toString(),
            options = fingerOptions,
            optionLabel = { it.toString() },
            onSelect = onFingerCountChange
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = "fingers", color = DarkColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = "action:", color = DarkColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.size(6.dp))
        DropdownPicker(
            selectedLabel = controlById(controlId)?.label?.invoke() ?: controlId,
            options = vehicleControls,
            optionLabel = { it.label() },
            onSelect = { onActionChange(it.id) },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove",
                tint = DarkColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** A compact dropdown styled to sit inside a [DarkColors.Surface] row. */
@Composable
private fun <T> DropdownPicker(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DarkColors.Background)
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedLabel,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = DarkColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AddTriggerButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add trigger",
            tint = AccentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Add trigger",
            color = AccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AutomationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (checked) AccentColor.copy(alpha = 0.16f)
                    else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) AccentColor else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = DarkColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = DarkColors.Disabled
            )
        )
    }
}
