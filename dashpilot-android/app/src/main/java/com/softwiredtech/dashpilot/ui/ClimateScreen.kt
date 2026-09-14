package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

@Composable
fun ClimateScreen(
    dashState: Flow<DashState>?,
    climateKeepEnabled: Boolean,
    onClimateKeepChange: (Boolean) -> Unit,
    climateKeepMinutes: Int,
    onClimateKeepMinutesChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val fallback = DashState()
    val state by (dashState ?: flowOf(fallback)).collectAsState(initial = fallback)
    val car = state.carState
    val imperial = state.displaySettings.useImperial
    // A real setpoint is never 0, so 0 means no UI_hvacRequest seen yet.
    val live = car.acTemp != 0f

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
            ScreenHeader(title = "Climate", onBack = onBack)

            Spacer(modifier = Modifier.height(24.dp))

            SetpointCard(car, imperial)

            Spacer(modifier = Modifier.height(28.dp))

            SectionTitle("Automation")
            Spacer(modifier = Modifier.height(8.dp))
            AutomationRow(
                icon = Icons.Rounded.AcUnit,
                title = "Keep climate on",
                subtitle = "Keep the climate on when you leave the car. The automation stops after the set time, or when you return to the car.",
                checked = climateKeepEnabled,
                onToggle = { onClimateKeepChange(!climateKeepEnabled) },
                extraContent = if (climateKeepEnabled) {
                    {
                        NumberPickerFooter(
                            label = "Stop after",
                            value = climateKeepMinutes,
                            unit = "min",
                            range = CLIMATE_KEEP_MINUTE_RANGE,
                            onValueChange = onClimateKeepMinutesChange
                        )
                    }
                } else null
            )

            Spacer(modifier = Modifier.height(28.dp))

            InfoSection(
                "Setpoints",
                listOf(
                    "Driver" to setpointText(car.acTemp, imperial),
                    "Passenger" to setpointText(car.acTempRight, imperial)
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            InfoSection(
                "Settings",
                listOf(
                    "Power" to enumText(live, car.hvacPowerState, POWER_LABELS),
                    "Fan" to fanText(live, car.hvacFanLevel),
                    "A/C" to enumText(live, car.hvacAcMode, AC_LABELS),
                    "Recirculation" to enumText(live, car.hvacRecirc, RECIRC_LABELS),
                    "Keep climate" to enumText(live, car.hvacKeepClimateOn, KEEP_LABELS)
                )
            )


            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SetpointCard(car: CarState, imperial: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = setpointText(car.acTemp, imperial),
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            IconChip(
                icon = Icons.Rounded.AcUnit,
                tint = AccentColor,
                background = AccentColor.copy(alpha = 0.14f)
            )
        }
        Text(
            text = "Driver setpoint",
            color = DarkColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = DarkColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun InfoSection(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkColors.Surface, RoundedCornerShape(16.dp))
        ) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, color = DarkColors.TextMuted, fontSize = 15.sp)
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = DarkColors.Border,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}

private val POWER_LABELS = listOf("Off", "On", "Preconditioning", "Overheat protection (fan)", "Overheat protection")
private val AC_LABELS = listOf("Auto", "Off", "On")
private val RECIRC_LABELS = listOf("Auto", "Recirculate", "Fresh air")
private val KEEP_LABELS = listOf("Off", "Keep", "Dog mode", "Camp mode")

private fun setpointText(value: Float, imperial: Boolean): String {
    if (value == 0f) return "—"
    val lo = if (imperial) 59f else 15f
    val hi = if (imperial) 82.4f else 28f
    return when {
        value <= lo -> "LO"
        value >= hi -> "HI"
        else -> "${value.roundToInt()}°"
    }
}

private fun enumText(live: Boolean, value: Float, labels: List<String>): String =
    if (live) labels.getOrNull(value.roundToInt()) ?: "—" else "—"

private fun fanText(live: Boolean, value: Float): String {
    if (!live) return "—"
    return when (val level = value.roundToInt()) {
        0 -> "Off"
        11 -> "Auto"
        in 1..10 -> "$level"
        else -> "—"
    }
}
