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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.ble.VehicleControl
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

/**
 * Vehicle control screen. Currently exposes battery preconditioning (preheat),
 * which the firmware translates into a UI_vehicleControl2 CAN frame. Disabled
 * when there is no active DashKit connection.
 */
@Composable
fun ControlScreen(bleManager: DashKitBleManager?) {
    val enabled = bleManager != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Battery",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (!enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect to DashKit to send commands",
                    color = DarkColors.TextMuted,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ControlCard(title = "Preconditioning") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        label = "Preheat On",
                        icon = true,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        bleManager?.let {
                            VehicleControl.send(it, VehicleControl.CMD_BATTERY_PREHEAT, 1)
                        }
                    }
                    ActionButton(
                        label = "Off",
                        icon = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        bleManager?.let {
                            VehicleControl.send(it, VehicleControl.CMD_BATTERY_PREHEAT, 0)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Closures",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Frunk and rear trunk use a single "move" request that toggles the
            // closure open/closed, so each is one button rather than open/close.
            ControlCard(title = "Frunk / Trunk") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        label = "Frunk",
                        icon = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        bleManager?.let {
                            VehicleControl.send(
                                it,
                                VehicleControl.CMD_CLOSURE,
                                VehicleControl.CLOSURE_FRONT_TRUNK
                            )
                        }
                    }
                    ActionButton(
                        label = "Trunk",
                        icon = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        bleManager?.let {
                            VehicleControl.send(
                                it,
                                VehicleControl.CMD_CLOSURE,
                                VehicleControl.CLOSURE_REAR_TRUNK
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ControlCard(title = "Charge Port") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        label = "Open",
                        icon = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        bleManager?.let {
                            VehicleControl.send(it, VehicleControl.CMD_CHARGE_PORT_OPEN, 1)
                        }
                    }
                    ActionButton(
                        label = "Close",
                        icon = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        bleManager?.let {
                            VehicleControl.send(it, VehicleControl.CMD_CHARGE_PORT_CLOSE, 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Glovebox is an electronic latch release: open only (closed by hand).
            ControlCard(title = "Glovebox") {
                ActionButton(
                    label = "Open Glovebox",
                    icon = false,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    bleManager?.let {
                        VehicleControl.send(it, VehicleControl.CMD_GLOVEBOX, 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = DarkColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (icon) AccentColor else DarkColors.SurfaceSelected,
            contentColor = Color.White,
            disabledContainerColor = DarkColors.Disabled,
            disabledContentColor = DarkColors.ContentDisabled
        ),
        modifier = modifier.height(52.dp)
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(text = label, fontSize = 15.sp)
    }
}
