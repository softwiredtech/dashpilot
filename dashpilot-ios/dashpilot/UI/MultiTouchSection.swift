import SwiftUI

// Firmware MULTI_FINGER_MIN/MAX_FINGERS.
private let fingerCounts = 3...5

private let fingerActionsKey = "finger_actions"

struct FingerAction: Identifiable, Equatable {
    let id = UUID()
    var fingerCount: Int
    var controlId: String
}

struct MultiTouchSection: View {
    @Environment(ConnectionViewModel.self) private var connectionVM

    @State private var fingerActions: [FingerAction] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
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
        .onAppear(perform: loadFingerActions)
        .onChange(of: fingerActions) { oldValue, newValue in
            saveFingerActions()
            pushChangedBindings(from: oldValue, to: newValue)
        }
    }

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

    private func loadFingerActions() {
        let raw = UserDefaults.standard.string(forKey: fingerActionsKey) ?? ""
        fingerActions = Self.parseFingerActions(raw)
    }

    private func saveFingerActions() {
        let raw = Self.serializeFingerActions(fingerActions)
        UserDefaults.standard.set(raw, forKey: fingerActionsKey)
    }

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

    static func serializeFingerActions(_ actions: [FingerAction]) -> String {
        actions
            .sorted { $0.fingerCount < $1.fingerCount }
            .map { "\($0.fingerCount)=\($0.controlId)" }
            .joined(separator: ";")
    }
}

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
                    .font(.system(size: 16, weight: .semibold))
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

private struct AddTriggerButton: View {
    let onTap: () -> Void

    init(onTap: @escaping () -> Void) {
        self.onTap = onTap
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
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
