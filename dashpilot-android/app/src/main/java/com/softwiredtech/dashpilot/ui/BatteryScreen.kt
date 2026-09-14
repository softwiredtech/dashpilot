package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.controls.ControlActionButton
import com.softwiredtech.dashpilot.ui.controls.controlById
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

@Composable
fun BatteryScreen(
    dashState: Flow<DashState>?,
    bleManager: DashKitBleManager?,
    onBack: () -> Unit
) {
    val fallback = DashState()
    val state by (dashState ?: flowOf(fallback)).collectAsState(initial = fallback)
    val car = state.carState

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
            ScreenHeader(title = "Battery", onBack = onBack)

            Spacer(modifier = Modifier.height(24.dp))

            SocCard(car)
            controlById("battery_preheat")?.let { action ->
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Preheat",
                    color = DarkColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                ControlActionButton(
                    action = action,
                    pinned = false,
                    enabled = bleManager != null,
                    onClick = { action.perform(bleManager) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            InfoSection(
                "Energy",
                listOf(
                    "Remaining" to energyText(car.nominalEnergyRemaining),
                    "Usable" to usableEnergyText(car),
                    "Full Pack" to energyText(car.fullPackEnergy),
                    "Buffer" to bufferText(car)
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            InfoSection(
                "Power",
                listOf(
                    "Power" to powerText(car),
                    "Max Discharge" to kilowattText(car.maxDischargePower),
                    "Max Regen" to kilowattText(car.maxRegenPower)
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            InfoSection(
                "Pack",
                listOf(
                    "Voltage" to voltageText(car),
                    "Current" to currentText(car),
                    "Temp Min" to tempText(car.packTMin),
                    "Temp Max" to tempText(car.packTMax)
                )
            )


            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SocCard(car: CarState) {
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
                text = socText(car),
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            IconChip(
                icon = Icons.Rounded.BatteryChargingFull,
                tint = AccentColor,
                background = AccentColor.copy(alpha = 0.14f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkColors.SurfaceSelected)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(socFraction(car))
                    .background(AccentColor)
            )
        }
        Text(
            text = "State of charge",
            color = DarkColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun InfoSection(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = DarkColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
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

private fun hasSoc(car: CarState): Boolean =
    car.fullPackEnergy > 0f && car.nominalEnergyRemaining > 0f && car.fullPackEnergy - car.energyBuffer > 0f

private fun socFraction(car: CarState): Float {
    if (!hasSoc(car)) return 0f
    val soc = (car.nominalEnergyRemaining - car.energyBuffer) / (car.fullPackEnergy - car.energyBuffer)
    return soc.coerceIn(0f, 1f)
}

private fun socText(car: CarState): String =
    if (hasSoc(car)) "${(socFraction(car) * 100f).roundToInt()}%" else "—"

private fun energyText(value: Float): String =
    if (value > 0f) "%.1f kWh".format(value) else "—"

private fun usableEnergyText(car: CarState): String =
    if (car.nominalEnergyRemaining > 0f) {
        "%.1f kWh".format((car.nominalEnergyRemaining - car.energyBuffer).coerceAtLeast(0f))
    } else "—"

private fun bufferText(car: CarState): String =
    if (car.fullPackEnergy > 0f) "%.1f kWh".format(car.energyBuffer) else "—"

private fun kilowattText(value: Float): String =
    if (value > 0f) "%.0f kW".format(value) else "—"

private fun powerText(car: CarState): String =
    if (car.packVoltage > 0f) "%.1f kW".format(car.packVoltage * car.packCurrent / 1000f) else "—"

private fun voltageText(car: CarState): String =
    if (car.packVoltage > 0f) "%.1f V".format(car.packVoltage) else "—"

private fun currentText(car: CarState): String =
    if (car.packVoltage > 0f) "%.1f A".format(car.packCurrent) else "—"

private fun tempText(value: Float): String =
    if (value != 0f) "${value.roundToInt()}°" else "—"
