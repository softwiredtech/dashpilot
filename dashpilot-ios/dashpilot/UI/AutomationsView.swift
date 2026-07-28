import SwiftUI

/// Finger counts that can be bound to an infotainment gesture (matches the
/// firmware's MULTI_FINGER_MIN/MAX_FINGERS and Android `FINGER_COUNTS`).
private let fingerCounts = 3...5

/// UserDefaults key holding the serialized finger-action bindings.
private let fingerActionsKey = "finger_actions"

/// A single multi-finger tap binding: `fingerCount` fingers -> vehicle
/// control `controlId`.
struct FingerAction: Identifiable, Equatable {
    let id = UUID()
    var fingerCount: Int
    var controlId: String
}

/// Automations screen, ported from Android `AutomationsScreen`. Lets the user
/// enable the wiper-off automation and bind 3-, 4-, and 5-finger infotainment
/// taps each to a vehicle control.
///
/// Edits persist to UserDefaults and are pushed to the DashKit firmware over
/// BLE when a link is up; the ConnectionViewModel re-syncs everything on each
/// connect, so edits made while disconnected are not lost.
struct AutomationsView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(ConnectionViewModel.self) private var connectionVM

    @AppStorage("wiper_off_automation") private var wiperOff: Bool = false
    @State private var fingerActions: [FingerAction] = []

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    headerRow

                    Spacer().frame(height: 24)

                    SectionLabel("Wipers")
                    Spacer().frame(height: 8)
                    AutomationRow(
                        icon: "drop.fill",
                        title: "Wiper Off",
                        subtitle: "Keep wipers disabled automatically",
                        isOn: $wiperOff
                    )

                    Spacer().frame(height: 28)

                    SectionLabel("Multi-touch infotainment trigger")
                    Spacer().frame(height: 4)
                    Text("Bind 3-, 4-, or 5-finger infotainment taps to a control")
                        .foregroundColor(.dashTextMuted)
                        .font(.system(size: 13))
                        .padding(.leading, 4)
                    Spacer().frame(height: 12)

                    ForEach($fingerActions) { $action in
                        FingerActionRow(
                            action: $action,
                            fingerOptions: fingerOptions(for: action),
                            onRemove: { removeAction(action) }
                        )
                        Spacer().frame(height: 12)
                    }

                    if let nextFree = firstFreeFingerCount() {
                        AddTriggerButton {
                            addAction(fingerCount: nextFree)
                        }
                    }
                }
                .padding(DashMetrics.screenPadding)
            }
        }
        .navigationBarHidden(true)
        .onAppear(perform: loadFingerActions)
        .onChange(of: wiperOff) { _, newValue in
            // Persisted by @AppStorage; arm/disarm the firmware immediately.
            if let manager = connectionVM.bleManager {
                VehicleControl.sendWiperOff(manager, enabled: newValue)
            }
        }
        .onChange(of: fingerActions) { oldValue, newValue in
            saveFingerActions()
            pushChangedBindings(from: oldValue, to: newValue)
        }
    }

    /// Pushes every finger slot whose binding changed, clearing freed slots
    /// with GESTURE_ACTION_NONE (covers add, remove, count and control edits —
    /// the same slots Android's setFingerAction/changeFingerCount push).
    private func pushChangedBindings(from old: [FingerAction], to new: [FingerAction]) {
        guard let manager = connectionVM.bleManager else { return }
        let oldByCount = Dictionary(uniqueKeysWithValues: old.map { ($0.fingerCount, $0.controlId) })
        let newByCount = Dictionary(uniqueKeysWithValues: new.map { ($0.fingerCount, $0.controlId) })
        for fingers in fingerCounts {
            let oldId = oldByCount[fingers]
            let newId = newByCount[fingers]
            guard oldId != newId else { continue }
            let actionValue = newId.flatMap { controlById($0)?.gestureValue }
                ?? VehicleControl.gestureActionNone
            VehicleControl.sendFingerAction(manager, fingers: fingers, actionValue: actionValue)
        }
    }

    // MARK: - Header

    private var headerRow: some View {
        HStack(spacing: 8) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
            }
            Text("Automations")
                .foregroundColor(.white)
                .font(.system(size: 24, weight: .bold))
        }
    }

    // MARK: - Finger action state

    /// Counts this row may pick: its own current value plus any count not used
    /// by another row.
    private func fingerOptions(for action: FingerAction) -> [Int] {
        let used = Set(fingerActions.map(\.fingerCount))
        return fingerCounts.filter { $0 == action.fingerCount || !used.contains($0) }
    }

    private func firstFreeFingerCount() -> Int? {
        let used = Set(fingerActions.map(\.fingerCount))
        return fingerCounts.first { !used.contains($0) }
    }

    private func addAction(fingerCount: Int) {
        guard let firstControl = vehicleControls.first else { return }
        fingerActions.append(FingerAction(fingerCount: fingerCount, controlId: firstControl.id))
    }

    private func removeAction(_ action: FingerAction) {
        fingerActions.removeAll { $0.id == action.id }
    }

    // MARK: - Persistence (UserDefaults stub)

    /// Loads bindings from UserDefaults ("3=glovebox;4=frunk" format).
    private func loadFingerActions() {
        let raw = UserDefaults.standard.string(forKey: fingerActionsKey) ?? ""
        fingerActions = Self.parseFingerActions(raw)
    }

    /// Persists bindings. Called from `.onChange(of: fingerActions)` so every
    /// add/remove/edit saves; the BLE push happens in `pushChangedBindings`.
    private func saveFingerActions() {
        let raw = Self.serializeFingerActions(fingerActions)
        UserDefaults.standard.set(raw, forKey: fingerActionsKey)
    }

    /// Parses "3=glovebox;4=frunk" into bindings, dropping malformed entries,
    /// counts outside 3...5, unknown control ids, and duplicate counts.
    static func parseFingerActions(_ raw: String) -> [FingerAction] {
        var seen = Set<Int>()
        var result: [FingerAction] = []
        for entry in raw.split(separator: ";") {
            let parts = entry.split(separator: "=", maxSplits: 1)
            guard parts.count == 2,
                  let fingers = Int(parts[0]),
                  fingerCounts.contains(fingers),
                  !seen.contains(fingers),
                  controlById(String(parts[1])) != nil
            else { continue }
            seen.insert(fingers)
            result.append(FingerAction(fingerCount: fingers, controlId: String(parts[1])))
        }
        return result.sorted { $0.fingerCount < $1.fingerCount }
    }

    /// Serializes bindings as "3=glovebox;4=frunk" (sorted by finger count).
    static func serializeFingerActions(_ actions: [FingerAction]) -> String {
        actions
            .sorted { $0.fingerCount < $1.fingerCount }
            .map { "\($0.fingerCount)=\($0.controlId)" }
            .joined(separator: ";")
    }
}

// MARK: - Section label

/// Small muted section heading (Android `SectionLabel`).
private struct SectionLabel: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text)
            .foregroundColor(.dashTextMuted)
            .font(.system(size: 13, weight: .semibold))
            .padding(.leading, 4)
    }
}

// MARK: - Automation row

/// A `.dashSurface` card with an icon, title/subtitle, and a trailing toggle
/// (Android `AutomationRow`).
private struct AutomationRow: View {
    let icon: String
    let title: String
    let subtitle: String?
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(isOn ? .dashAccent : .white)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundColor(.white)
                    .font(.system(size: 16, weight: .semibold))
                if let subtitle {
                    Text(subtitle)
                        .foregroundColor(.dashTextMuted)
                        .font(.system(size: 13))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(.dashAccent)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
        .contentShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
        .onTapGesture {
            isOn.toggle()
        }
    }
}

// MARK: - Finger action row

/// One multi-finger binding row: finger-count picker, control picker, and a
/// remove button (Android `FingerActionRow`).
private struct FingerActionRow: View {
    @Binding var action: FingerAction
    let fingerOptions: [Int]
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            DropdownPicker(
                selectedLabel: "\(action.fingerCount)",
                options: fingerOptions,
                optionLabel: { "\($0)" },
                onSelect: { action.fingerCount = $0 }
            )

            Text("fingers")
                .foregroundColor(.dashTextMuted)
                .font(.system(size: 14))
                .padding(.trailing, 4)

            Text("action:")
                .foregroundColor(.dashTextMuted)
                .font(.system(size: 14))

            DropdownPicker(
                selectedLabel: controlById(action.controlId)?.label() ?? action.controlId,
                options: vehicleControls,
                optionLabel: { $0.label() },
                onSelect: { action.controlId = $0.id }
            )
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.dashTextMuted)
                    .frame(width: 32, height: 32)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
    }
}

// MARK: - Dropdown picker

/// A compact `Menu`-based dropdown styled as a `.dashBackground` pill, meant to
/// sit inside a `.dashSurface` row (Android `DropdownPicker`).
private struct DropdownPicker<T>: View {
    let selectedLabel: String
    let options: [T]
    let optionLabel: (T) -> String
    let onSelect: (T) -> Void

    var body: some View {
        Menu {
            ForEach(options.indices, id: \.self) { index in
                Button(optionLabel(options[index])) {
                    onSelect(options[index])
                }
            }
        } label: {
            HStack(spacing: 2) {
                Text(selectedLabel)
                    .foregroundColor(.white)
                    .font(.system(size: 15))
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.dashTextMuted)
            }
            .padding(.leading, 12)
            .padding(.trailing, 8)
            .padding(.vertical, 10)
            .background(Color.dashBackground)
            .clipShape(RoundedRectangle(cornerRadius: DashMetrics.smallCorner))
        }
    }
}

// MARK: - Add trigger button

/// Accent-colored "+ Add trigger" button (Android `AddTriggerButton`).
private struct AddTriggerButton: View {
    let onTap: () -> Void

    init(onTap: @escaping () -> Void) {
        self.onTap = onTap
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: "plus")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.dashAccent)
                Text("Add trigger")
                    .foregroundColor(.dashAccent)
                    .font(.system(size: 15, weight: .semibold))
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 8)
        }
    }
}

#Preview {
    NavigationStack {
        AutomationsView()
    }
    .environment(ConnectionViewModel())
}
