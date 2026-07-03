package com.softwiredtech.dashpilot.ui.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
 * Session-scoped toggle state for controls the firmware exposes as separate
 * on/off (or open/close) opcodes — there is no readback from the car, so this
 * is shared in-memory between the Controls and Home screens and reset per run.
 */
object ControlsState {
    var preheatOn by mutableStateOf(false)
    var chargePortOpen by mutableStateOf(false)
    var mirrorsFolded by mutableStateOf(false)
    // Cosmetic only: whether our rear-fan override is active (firmware injecting).
    // The firmware picks the injected value (HIGH/OFF) from the live bus when it
    // starts, so there's no readback and this is just on/off for the override.
    var rearFanOn by mutableStateOf(false)
}

/**
 * A single vehicle control definition. [label] and [active] are read lazily so
 * they reflect the latest [ControlsState] each recomposition.
 */
data class ControlAction(
    val id: String,
    val icon: ImageVector,
    val label: () -> String,
    val active: () -> Boolean,
    // Action code sent to the firmware when this control is bound to a
    // multi-finger infotainment gesture (must match multi_finger_action_t in the
    // firmware's multi_finger.h). This is the single source of truth for the
    // gesture codes.
    val gestureValue: Int,
    // Nullable manager: when null (e.g. debug testing without a DashKit) the
    // command is simulated — toggle state still flips, momentary actions no-op.
    val perform: (DashKitBleManager?) -> Unit
)

/** Shared registry of vehicle controls used by both Controls and Home screens. */
val vehicleControls: List<ControlAction> = listOf(
    ControlAction(
        id = "battery_preheat",
        icon = Icons.Rounded.Thermostat,
        label = { if (ControlsState.preheatOn) "Battery Preheat: On" else "Battery Preheat: Off" },
        active = { ControlsState.preheatOn },
        gestureValue = 2,
        perform = { manager ->
            val next = !ControlsState.preheatOn
            if (manager == null ||
                VehicleControl.send(manager, VehicleControl.CMD_BATTERY_PREHEAT, if (next) 1 else 0)
            ) {
                ControlsState.preheatOn = next
            }
        }
    ),
    ControlAction(
        id = "frunk",
        icon = Icons.Rounded.DirectionsCar,
        label = { "Frunk" },
        active = { false },
        gestureValue = 4,
        perform = { manager ->
            manager?.let { VehicleControl.send(it, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_FRONT_TRUNK) }
        }
    ),
    ControlAction(
        id = "trunk",
        icon = Icons.Rounded.Inventory2,
        label = { "Trunk" },
        active = { false },
        gestureValue = 5,
        perform = { manager ->
            manager?.let { VehicleControl.send(it, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_REAR_TRUNK) }
        }
    ),
    ControlAction(
        id = "charge_port",
        icon = Icons.Rounded.EvStation,
        label = { if (ControlsState.chargePortOpen) "Charge Port: Open" else "Charge Port: Closed" },
        active = { ControlsState.chargePortOpen },
        gestureValue = 6,
        perform = { manager ->
            val opcode = if (ControlsState.chargePortOpen) VehicleControl.CMD_CHARGE_PORT_CLOSE
            else VehicleControl.CMD_CHARGE_PORT_OPEN
            if (manager == null || VehicleControl.send(manager, opcode, 1)) {
                ControlsState.chargePortOpen = !ControlsState.chargePortOpen
            }
        }
    ),
    ControlAction(
        id = "mirror_fold",
        icon = Icons.Rounded.Flip,
        label = { if (ControlsState.mirrorsFolded) "Mirrors: Folded" else "Mirrors: Unfolded" },
        active = { ControlsState.mirrorsFolded },
        gestureValue = 3,
        perform = { manager ->
            val value = if (ControlsState.mirrorsFolded) VehicleControl.MIRROR_UNFOLD
            else VehicleControl.MIRROR_FOLD
            if (manager == null || VehicleControl.send(manager, VehicleControl.CMD_MIRROR_FOLD, value)) {
                ControlsState.mirrorsFolded = !ControlsState.mirrorsFolded
            }
        }
    ),
    ControlAction(
        id = "rear_fan",
        icon = Icons.Rounded.Air,
        label = { if (ControlsState.rearFanOn) "Rear Fan: On" else "Rear Fan: Off" },
        active = { ControlsState.rearFanOn },
        gestureValue = 7,
        perform = { manager ->
            if (manager == null || VehicleControl.sendRearFanToggle(manager)) {
                ControlsState.rearFanOn = !ControlsState.rearFanOn
            }
        }
    ),
    // Glovebox is an electronic latch release: open only (closed by hand).
    ControlAction(
        id = "glovebox",
        icon = Icons.Rounded.Inbox,
        label = { "Open Glovebox" },
        active = { false },
        gestureValue = 1,
        perform = { manager ->
            manager?.let { VehicleControl.send(it, VehicleControl.CMD_GLOVEBOX, 1) }
        }
    )
)

/** Looks up a control by id, or null if [id] is null/unknown. */
fun controlById(id: String?): ControlAction? =
    id?.let { wanted -> vehicleControls.firstOrNull { it.id == wanted } }

/**
 * Reusable control button shared by the Controls and Home screens. Tap fires
 * [onClick]; an optional [onLongClick] toggles the pin. Shows a pin badge when
 * [pinned] and dims when not [enabled].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlActionButton(
    action: ControlAction,
    pinned: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (action.active()) AccentColor else DarkColors.Surface)
            // Long-press (pin toggle) stays available even when disconnected so
            // pinning can be tested without a DashKit; tap still only fires the
            // control when [enabled].
            .combinedClickable(
                enabled = enabled || onLongClick != null,
                onClick = { if (enabled) onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = action.label(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        if (pinned) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = "Pinned",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp)
                    .size(16.dp)
            )
        }
    }
}
