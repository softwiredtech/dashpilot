package com.softwiredtech.dashpilot.ui

import android.widget.NumberPicker
import androidx.compose.foundation.background
import com.softwiredtech.dashpilot.datamodel.dash.AcSwingSettings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import androidx.compose.ui.res.stringResource
import com.softwiredtech.dashpilot.R

// Minutes the keep-climate-on window can run (matches the firmware clamp).
internal val CLIMATE_KEEP_MINUTE_RANGE = 1..60

// Matches the firmware clamp.
private val SPORT_KICKDOWN_PERCENT_RANGE = 10..95
private const val SPORT_KICKDOWN_PERCENT_STEP = 5

/**
 * Automations screen: wiper, climate and driving automations. Multi-finger
 * infotainment triggers live in the Controls screen's Multi-touch tab.
 */
@Composable
fun AutomationsScreen(
    wiperOffEnabled: Boolean,
    onWiperOffChange: (Boolean) -> Unit,
    climateKeepEnabled: Boolean,
    onClimateKeepChange: (Boolean) -> Unit,
    climateKeepMinutes: Int,
    onClimateKeepMinutesChange: (Int) -> Unit,
    acSwing: AcSwingSettings,
    onAcSwingChange: (AcSwingSettings) -> Unit,
    sportKickdownEnabled: Boolean,
    onSportKickdownChange: (Boolean) -> Unit,
    sportKickdownPercent: Int,
    onSportKickdownPercentChange: (Int) -> Unit,
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
            ScreenHeader(title = stringResource(R.string.automations_title), onBack = onBack)

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.automations_section_wipers))
            Spacer(modifier = Modifier.height(8.dp))
            AutomationRow(
                icon = Icons.Rounded.WaterDrop,
                title = stringResource(R.string.automations_wiper_off_title),
                subtitle = stringResource(R.string.automations_wiper_off_subtitle),
                checked = wiperOffEnabled,
                onToggle = { onWiperOffChange(!wiperOffEnabled) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            SectionLabel(stringResource(R.string.automations_section_climate))
            Spacer(modifier = Modifier.height(8.dp))
            AutomationRow(
                icon = Icons.Rounded.AcUnit,
                title = stringResource(R.string.automations_keep_climate_title),
                subtitle = stringResource(R.string.automations_keep_climate_subtitle),
                checked = climateKeepEnabled,
                onToggle = { onClimateKeepChange(!climateKeepEnabled) },
                extraContent = if (climateKeepEnabled) {
                    {
                        NumberPickerFooter(
                            label = stringResource(R.string.automations_stop_after),
                            value = climateKeepMinutes,
                            unit = stringResource(R.string.unit_min),
                            range = CLIMATE_KEEP_MINUTE_RANGE,
                            onValueChange = onClimateKeepMinutesChange
                        )
                    }
                } else null
            )
            Spacer(modifier = Modifier.height(8.dp))
            AcSwingRow(acSwing, onAcSwingChange)

            Spacer(modifier = Modifier.height(28.dp))

            SectionLabel(stringResource(R.string.automations_section_driving))
            Spacer(modifier = Modifier.height(8.dp))
            AutomationRow(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.automations_kickdown_title),
                subtitle = stringResource(R.string.automations_kickdown_subtitle),
                checked = sportKickdownEnabled,
                onToggle = { onSportKickdownChange(!sportKickdownEnabled) },
                extraContent = if (sportKickdownEnabled) {
                    {
                        NumberPickerFooter(
                            label = stringResource(R.string.automations_pedal_threshold),
                            value = sportKickdownPercent,
                            unit = "%",
                            range = SPORT_KICKDOWN_PERCENT_RANGE,
                            step = SPORT_KICKDOWN_PERCENT_STEP,
                            onValueChange = onSportKickdownPercentChange
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = DarkColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
internal fun NumberPickerFooter(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    step: Int = 1
) {
    var showPicker by remember { mutableStateOf(false) }
    val choices = remember(range, step) { (range step step).toList() }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = DarkColors.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DarkColors.Background)
                .clickable { showPicker = true }
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$value $unit", color = Color.White, fontSize = 15.sp)
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = DarkColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showPicker) {
        var pending by remember { mutableIntStateOf(value) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = 0
                            maxValue = choices.lastIndex
                            displayedValues = choices.map { "$it $unit" }.toTypedArray()
                            wrapSelectorWheel = false
                            this.value = choices.indexOf(value).coerceAtLeast(0)
                            setOnValueChangedListener { _, _, idx -> pending = choices[idx] }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    onValueChange(pending)
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
internal fun AcSwingRow(value: AcSwingSettings, onChange: (AcSwingSettings) -> Unit) {
    AutomationRow(
        icon = Icons.Rounded.Air,
        title = stringResource(R.string.automations_ac_swing_title),
        subtitle = stringResource(R.string.automations_ac_swing_subtitle),
        checked = false,
        onToggle = null,
        extraContent = {
            ChoiceFooter(
                label = stringResource(R.string.automations_ac_swing_side),
                options = listOf(
                    stringResource(R.string.climate_driver),
                    stringResource(R.string.climate_passenger),
                    stringResource(R.string.automations_ac_swing_both)
                ),
                selected = value.side,
                onSelect = { onChange(value.copy(side = it)) }
            )
            ChoiceFooter(
                label = stringResource(R.string.automations_ac_swing_intensity),
                options = listOf(
                    stringResource(R.string.automations_ac_swing_low),
                    stringResource(R.string.automations_ac_swing_medium),
                    stringResource(R.string.automations_ac_swing_full)
                ),
                selected = value.intensity,
                onSelect = { onChange(value.copy(intensity = it)) }
            )
        }
    )
}

@Composable
private fun ChoiceFooter(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = label, color = DarkColors.TextMuted, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(6.dp))
    SegmentedSelector(options = options, selected = selected, onSelect = onSelect)
}

@Composable
internal fun SegmentedSelector(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = DarkColors.Background
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) AccentColor else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = if (isSelected) Color.White else DarkColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun AutomationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: (() -> Unit)?,
    extraContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onToggle != null) { onToggle?.invoke() },
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
            if (onToggle != null) {
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
        extraContent?.invoke()
    }
}
