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
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.res.stringResource
import com.softwiredtech.dashpilot.R

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
 * A toggle whose command is queued, not sent, when it is tapped. It shows the
 * new state at once; when the outcome of every queued command is known it
 * shows what the car actually took, so lost commands (a dying link, a refused
 * write) do not leave the button out of step. Used from the main thread.
 *
 * [flips]: the command toggles the car's state (so a delivered one inverts
 * it) instead of setting it to the requested value.
 */
private class OptimisticToggle(
    private val flips: Boolean,
    private val get: () -> Boolean,
    private val set: (Boolean) -> Unit
) {
    private var confirmed = false   // the car's state, as far as acknowledged commands tell
    private var pending = 0         // commands queued whose outcome is not known yet

    /** [send] queues the command for [target] and returns whether it was queued. */
    fun request(target: Boolean, send: (onResult: (Boolean) -> Unit) -> Boolean) {
        if (pending == 0) confirmed = get()
        val queued = send { delivered ->
            if (delivered) confirmed = if (flips) !confirmed else target
            pending--
            if (pending == 0) set(confirmed)
        }
        if (queued) {
            pending++
            set(target)
        }
    }
}

private val preheatToggle = OptimisticToggle(false, { ControlsState.preheatOn }, { ControlsState.preheatOn = it })
private val chargePortToggle = OptimisticToggle(false, { ControlsState.chargePortOpen }, { ControlsState.chargePortOpen = it })
private val mirrorFoldToggle = OptimisticToggle(false, { ControlsState.mirrorsFolded }, { ControlsState.mirrorsFolded = it })
private val rearFanToggle = OptimisticToggle(true, { ControlsState.rearFanOn }, { ControlsState.rearFanOn = it })

/** Categories change presentation only; command IDs and gesture codes stay stable. */
enum class ControlCategory(val titleRes: Int, val icon: ImageVector) {
    DOORS(R.string.commands_category_doors, Icons.Rounded.MeetingRoom),
    STORAGE(R.string.commands_category_storage, Icons.Rounded.Inbox),
    MIRRORS(R.string.commands_category_mirrors, Icons.Rounded.Flip),
    CLIMATE(R.string.commands_category_climate, Icons.Rounded.Air),
    BATTERY_CHARGING(R.string.commands_category_battery_charging, Icons.Rounded.EvStation)
}

/**
 * A single vehicle control definition. [labelRes] and [active] are read lazily so
 * they reflect the latest [ControlsState] each recomposition.
 */
data class ControlAction(
    val id: String,
    val category: ControlCategory,
    val icon: ImageVector,
    val labelRes: () -> Int,
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
        category = ControlCategory.BATTERY_CHARGING,
        icon = Icons.Rounded.Thermostat,
        labelRes = { if (ControlsState.preheatOn) R.string.control_battery_preheat_on else R.string.control_battery_preheat_off },
        active = { ControlsState.preheatOn },
        gestureValue = 2,
        perform = { manager ->
            val next = !ControlsState.preheatOn
            if (manager == null) {
                ControlsState.preheatOn = next
            } else {
                preheatToggle.request(next) { onResult ->
                    VehicleControl.send(manager, VehicleControl.CMD_BATTERY_PREHEAT, if (next) 1 else 0, onResult)
                }
            }
        }
    ),
    ControlAction(
        id = "frunk",
        category = ControlCategory.STORAGE,
        icon = VehicleIcons.Frunk,
        labelRes = { R.string.control_frunk },
        active = { false },
        gestureValue = 4,
        perform = { manager ->
            manager?.let { VehicleControl.send(it, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_FRONT_TRUNK) }
        }
    ),
    ControlAction(
        id = "trunk",
        category = ControlCategory.STORAGE,
        icon = VehicleIcons.Trunk,
        labelRes = { R.string.control_trunk },
        active = { false },
        gestureValue = 5,
        perform = { manager ->
            manager?.let { VehicleControl.send(it, VehicleControl.CMD_CLOSURE, VehicleControl.CLOSURE_REAR_TRUNK) }
        }
    ),
    ControlAction(
        id = "charge_port",
        category = ControlCategory.BATTERY_CHARGING,
        icon = Icons.Rounded.EvStation,
        labelRes = { if (ControlsState.chargePortOpen) R.string.control_charge_port_open else R.string.control_charge_port_closed },
        active = { ControlsState.chargePortOpen },
        gestureValue = 6,
        perform = { manager ->
            val opcode = if (ControlsState.chargePortOpen) VehicleControl.CMD_CHARGE_PORT_CLOSE
            else VehicleControl.CMD_CHARGE_PORT_OPEN
            val next = !ControlsState.chargePortOpen
            if (manager == null) {
                ControlsState.chargePortOpen = next
            } else {
                chargePortToggle.request(next) { onResult -> VehicleControl.send(manager, opcode, 1, onResult) }
            }
        }
    ),
    ControlAction(
        id = "mirror_fold",
        category = ControlCategory.MIRRORS,
        icon = Icons.Rounded.Flip,
        labelRes = { if (ControlsState.mirrorsFolded) R.string.control_mirrors_folded else R.string.control_mirrors_unfolded },
        active = { ControlsState.mirrorsFolded },
        gestureValue = 3,
        perform = { manager ->
            val value = if (ControlsState.mirrorsFolded) VehicleControl.MIRROR_UNFOLD
            else VehicleControl.MIRROR_FOLD
            val next = !ControlsState.mirrorsFolded
            if (manager == null) {
                ControlsState.mirrorsFolded = next
            } else {
                mirrorFoldToggle.request(next) { onResult ->
                    VehicleControl.send(manager, VehicleControl.CMD_MIRROR_FOLD, value, onResult)
                }
            }
        }
    ),
    ControlAction(
        id = "rear_fan",
        category = ControlCategory.CLIMATE,
        icon = Icons.Rounded.Air,
        labelRes = { if (ControlsState.rearFanOn) R.string.control_rear_fan_on else R.string.control_rear_fan_off },
        active = { ControlsState.rearFanOn },
        gestureValue = 7,
        perform = { manager ->
            val next = !ControlsState.rearFanOn
            if (manager == null) {
                ControlsState.rearFanOn = next
            } else {
                rearFanToggle.request(next) { onResult -> VehicleControl.sendRearFanToggle(manager, onResult) }
            }
        }
    ),
    ControlAction(
        id = "ac_swing",
        category = ControlCategory.CLIMATE,
        icon = Icons.Rounded.SwapHoriz,
        labelRes = { R.string.control_ac_swing },
        active = { false },
        gestureValue = 9,
        perform = { manager ->
            manager?.let { VehicleControl.sendAcSwingToggle(it) }
        }
    ),
    // Momentary: the firmware only acts in reverse and drops the override when
    // the car leaves reverse, so there is no stable on/off state to show.
    ControlAction(
        id = "mirror_dip",
        category = ControlCategory.MIRRORS,
        icon = Icons.Rounded.SouthEast,
        labelRes = { R.string.control_mirror_dip },
        active = { false },
        gestureValue = 8,
        perform = { manager ->
            manager?.let { VehicleControl.sendMirrorDipToggle(it) }
        }
    ),
    // Door unlatch: a momentary latch release per door, like the Tesla app's
    // "Unlatch Door". The firmware refuses it in R/N/D or while moving.
    ControlAction(
        id = "unlatch_front_left",
        category = ControlCategory.DOORS,
        icon = Icons.Rounded.MeetingRoom,
        labelRes = { R.string.control_unlatch_front_left },
        active = { false },
        gestureValue = 10,
        perform = { manager ->
            manager?.let { VehicleControl.sendDoorUnlatch(it, VehicleControl.DOOR_FRONT_LEFT) }
        }
    ),
    ControlAction(
        id = "unlatch_front_right",
        category = ControlCategory.DOORS,
        icon = Icons.Rounded.MeetingRoom,
        labelRes = { R.string.control_unlatch_front_right },
        active = { false },
        gestureValue = 11,
        perform = { manager ->
            manager?.let { VehicleControl.sendDoorUnlatch(it, VehicleControl.DOOR_FRONT_RIGHT) }
        }
    ),
    ControlAction(
        id = "unlatch_rear_left",
        category = ControlCategory.DOORS,
        icon = Icons.Rounded.MeetingRoom,
        labelRes = { R.string.control_unlatch_rear_left },
        active = { false },
        gestureValue = 12,
        perform = { manager ->
            manager?.let { VehicleControl.sendDoorUnlatch(it, VehicleControl.DOOR_REAR_LEFT) }
        }
    ),
    ControlAction(
        id = "unlatch_rear_right",
        category = ControlCategory.DOORS,
        icon = Icons.Rounded.MeetingRoom,
        labelRes = { R.string.control_unlatch_rear_right },
        active = { false },
        gestureValue = 13,
        perform = { manager ->
            manager?.let { VehicleControl.sendDoorUnlatch(it, VehicleControl.DOOR_REAR_RIGHT) }
        }
    ),
    // Glovebox is an electronic latch release: open only (closed by hand).
    ControlAction(
        id = "glovebox",
        category = ControlCategory.STORAGE,
        icon = Icons.Rounded.Inbox,
        labelRes = { R.string.control_glovebox },
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Color.White.copy(alpha = if (action.active()) 0.18f else 0.08f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(action.labelRes()),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        if (pinned) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = stringResource(R.string.control_pinned_cd),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp)
                    .size(16.dp)
            )
        }
    }
}
