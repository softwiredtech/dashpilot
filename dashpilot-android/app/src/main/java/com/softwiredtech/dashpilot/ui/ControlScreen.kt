package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.ble.VehicleControl
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

/**
 * Vehicle control screen. Exposes the available DashKit commands as single
 * toggle/action buttons. Disabled when there is no active DashKit connection.
 */
@Composable
fun ControlScreen(
    bleManager: DashKitBleManager?,
    onBack: () -> Unit
) {
    val enabled = bleManager != null

    // Local UI state for toggles that the firmware exposes as separate
    // on/off (or open/close) opcodes — there is no readback from the car.
    var preheatOn by remember { mutableStateOf(false) }
    var chargePortOpen by remember { mutableStateOf(false) }
    var mirrorsFolded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            ControlButton(
                label = if (preheatOn) "Battery Preheat: On" else "Battery Preheat: Off",
                icon = Icons.Rounded.Thermostat,
                active = preheatOn,
                enabled = enabled
            ) {
                bleManager?.let {
                    val next = !preheatOn
                    if (VehicleControl.send(it, VehicleControl.CMD_BATTERY_PREHEAT, if (next) 1 else 0)) {
                        preheatOn = next
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ControlButton(
                label = "Frunk",
                icon = Icons.Rounded.DirectionsCar,
                active = false,
                enabled = enabled
            ) {
                bleManager?.let {
                    VehicleControl.send(it, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_FRONT_TRUNK)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ControlButton(
                label = "Trunk",
                icon = Icons.Rounded.Inventory2,
                active = false,
                enabled = enabled
            ) {
                bleManager?.let {
                    VehicleControl.send(it, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_REAR_TRUNK)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ControlButton(
                label = if (chargePortOpen) "Charge Port: Open" else "Charge Port: Closed",
                icon = Icons.Rounded.EvStation,
                active = chargePortOpen,
                enabled = enabled
            ) {
                bleManager?.let {
                    val opcode = if (chargePortOpen) VehicleControl.CMD_CHARGE_PORT_CLOSE
                    else VehicleControl.CMD_CHARGE_PORT_OPEN
                    if (VehicleControl.send(it, opcode, 1)) {
                        chargePortOpen = !chargePortOpen
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TODO: no firmware opcode for mirror fold yet — UI stub only.
            ControlButton(
                label = if (mirrorsFolded) "Mirrors: Folded" else "Mirrors: Unfolded",
                icon = Icons.Rounded.Flip,
                active = mirrorsFolded,
                enabled = enabled
            ) {
                mirrorsFolded = !mirrorsFolded
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Glovebox is an electronic latch release: open only (closed by hand).
            ControlButton(
                label = "Open Glovebox",
                icon = Icons.Rounded.Inbox,
                active = false,
                enabled = enabled
            ) {
                bleManager?.let {
                    VehicleControl.send(it, VehicleControl.CMD_GLOVEBOX, 1)
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) AccentColor else DarkColors.Surface,
            contentColor = Color.White,
            disabledContainerColor = DarkColors.Disabled,
            disabledContentColor = DarkColors.ContentDisabled
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}
