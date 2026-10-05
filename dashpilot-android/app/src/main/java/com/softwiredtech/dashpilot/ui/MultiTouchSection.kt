package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ui.controls.controlById
import com.softwiredtech.dashpilot.ui.controls.vehicleControls
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

// Firmware MULTI_FINGER_MIN/MAX_FINGERS.
private val FINGER_COUNTS = 3..5

@Composable
internal fun MultiTouchSection(
    fingerActions: Map<Int, String>,
    onSetFingerAction: (fingers: Int, id: String?) -> Unit,
    onChangeFingerCount: (from: Int, to: Int) -> Unit,
    onRemoveFingerAction: (fingers: Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.automations_multitouch_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.automations_multitouch_subtitle),
            color = DarkColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        val usedCounts = fingerActions.keys
        fingerActions.toSortedMap().forEach { (fingers, controlId) ->
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
        Text(text = stringResource(R.string.automations_fingers), color = DarkColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = stringResource(R.string.automations_action), color = DarkColors.TextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.size(6.dp))
        DropdownPicker(
            selectedLabel = controlById(controlId)?.let { stringResource(it.labelRes()) } ?: controlId,
            options = vehicleControls,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { onActionChange(it.id) },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.automations_remove),
                tint = DarkColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun <T> DropdownPicker(
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
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
            contentDescription = stringResource(R.string.automations_add_trigger),
            tint = AccentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.automations_add_trigger),
            color = AccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
