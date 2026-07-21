import SwiftUI
import Observation

/// Session-scoped toggle state for controls the firmware exposes as separate
/// on/off (or open/close) opcodes — there is no readback from the car, so this
/// is shared in-memory between the Controls and Home screens and reset per run.
///
/// Ported from Android `ControlsState` (VehicleControls.kt). No persistence.
@Observable
final class ControlsState {
    static let shared = ControlsState()

    var preheatOn = false
    var chargePortOpen = false
    var mirrorsFolded = false
    /// Cosmetic only: whether our rear-fan override is active.
    var rearFanOn = false

    private init() {}
}

/// A single vehicle control definition. `label` and `active` are read lazily so
/// they reflect the latest `ControlsState` on each render.
///
/// `gestureValue` is the action code sent to the firmware when this control is
/// bound to a multi-finger infotainment gesture (must match
/// `multi_finger_action_t` in the firmware's multi_finger.h).
struct ControlAction: Identifiable {
    let id: String
    /// SF Symbol name.
    let icon: String
    let label: () -> String
    let active: () -> Bool
    let gestureValue: Int
    /// Bluetooth-less stub: flips in-memory `ControlsState` and logs only.
    let perform: () -> Void
}

/// Shared registry of vehicle controls used by both Controls and Home screens.
/// Ids and gestureValues match the Android app exactly.
let vehicleControls: [ControlAction] = [
    ControlAction(
        id: "battery_preheat",
        icon: "thermometer",
        label: { ControlsState.shared.preheatOn ? "Battery Preheat: On" : "Battery Preheat: Off" },
        active: { ControlsState.shared.preheatOn },
        gestureValue: 2,
        perform: {
            ControlsState.shared.preheatOn.toggle()
            print("[control] battery preheat -> \(ControlsState.shared.preheatOn ? "on" : "off")")
        }
    ),
    ControlAction(
        id: "frunk",
        icon: "car.fill",
        label: { "Frunk" },
        active: { false },
        gestureValue: 4,
        perform: {
            print("[control] frunk pressed")
        }
    ),
    ControlAction(
        id: "trunk",
        icon: "shippingbox.fill",
        label: { "Trunk" },
        active: { false },
        gestureValue: 5,
        perform: {
            print("[control] trunk pressed")
        }
    ),
    ControlAction(
        id: "charge_port",
        icon: "bolt.car.fill",
        label: { ControlsState.shared.chargePortOpen ? "Charge Port: Open" : "Charge Port: Closed" },
        active: { ControlsState.shared.chargePortOpen },
        gestureValue: 6,
        perform: {
            ControlsState.shared.chargePortOpen.toggle()
            print("[control] charge port -> \(ControlsState.shared.chargePortOpen ? "open" : "closed")")
        }
    ),
    ControlAction(
        id: "mirror_fold",
        icon: "arrow.left.arrow.right",
        label: { ControlsState.shared.mirrorsFolded ? "Mirrors: Folded" : "Mirrors: Unfolded" },
        active: { ControlsState.shared.mirrorsFolded },
        gestureValue: 3,
        perform: {
            ControlsState.shared.mirrorsFolded.toggle()
            print("[control] mirrors -> \(ControlsState.shared.mirrorsFolded ? "folded" : "unfolded")")
        }
    ),
    ControlAction(
        id: "rear_fan",
        icon: "wind",
        label: { ControlsState.shared.rearFanOn ? "Rear Fan: On" : "Rear Fan: Off" },
        active: { ControlsState.shared.rearFanOn },
        gestureValue: 7,
        perform: {
            ControlsState.shared.rearFanOn.toggle()
            print("[control] rear fan -> \(ControlsState.shared.rearFanOn ? "on" : "off")")
        }
    ),
    // Glovebox is an electronic latch release: open only (closed by hand).
    ControlAction(
        id: "glovebox",
        icon: "tray.fill",
        label: { "Open Glovebox" },
        active: { false },
        gestureValue: 1,
        perform: {
            print("[control] glovebox pressed")
        }
    )
]

/// Looks up a control by id, or nil if unknown.
func controlById(_ id: String) -> ControlAction? {
    vehicleControls.first { $0.id == id }
}

/// Reusable control button shared by the Controls and Home screens. Tap fires
/// `onTap`; long-press fires `onLongPress` (pin toggle). Shows a pin badge
/// when `pinned` and dims when not `enabled`.
struct ControlActionButton: View {
    let action: ControlAction
    let pinned: Bool
    let enabled: Bool
    let onTap: () -> Void
    let onLongPress: () -> Void

    init(
        action: ControlAction,
        pinned: Bool = false,
        enabled: Bool = true,
        onTap: @escaping () -> Void,
        onLongPress: @escaping () -> Void
    ) {
        self.action = action
        self.pinned = pinned
        self.enabled = enabled
        self.onTap = onTap
        self.onLongPress = onLongPress
    }

    var body: some View {
        ZStack {
            HStack(spacing: 12) {
                Image(systemName: action.icon)
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 20)
                Text(action.label())
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)

            if pinned {
                VStack {
                    HStack {
                        Spacer()
                        Image(systemName: "pin.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                            .padding(.top, 6)
                            .padding(.trailing, 8)
                    }
                    Spacer()
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: DashMetrics.buttonHeight)
        .background(action.active() ? Color.dashAccent : Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
        .opacity(enabled ? 1.0 : 0.5)
        .contentShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
        .onTapGesture {
            if enabled { onTap() }
        }
        // Long-press (pin toggle) stays available even when disabled so
        // pinning can be tested without a connection.
        .onLongPressGesture {
            onLongPress()
        }
    }
}
