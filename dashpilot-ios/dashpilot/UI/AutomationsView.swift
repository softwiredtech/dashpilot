import SwiftUI

/// Minutes the keep-climate-on window can run (matches the firmware clamp).
let climateKeepMinuteRange = 1...60

// Matches the firmware clamp.
private let sportKickdownPercentOptions = Array(stride(from: 10, through: 95, by: 5))

/// Automations screen, ported from Android `AutomationsScreen`: wiper, climate
/// and driving automations. Multi-finger infotainment triggers live in the
/// Controls screen's Multi-touch tab (`MultiTouchSection`).
///
/// Edits persist to UserDefaults and are pushed to the DashKit firmware over
/// BLE when a link is up; the ConnectionViewModel re-syncs everything on each
/// connect, so edits made while disconnected are not lost.
struct AutomationsView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(ConnectionViewModel.self) private var connectionVM

    @AppStorage("wiper_off_automation") private var wiperOff: Bool = false
    @AppStorage("climate_keep_automation") private var climateKeep: Bool = false
    @AppStorage("climate_keep_minutes") private var climateKeepMinutes: Int = 5
    @AppStorage("sport_kickdown_automation") private var sportKickdown: Bool = false
    @AppStorage("sport_kickdown_percent") private var sportKickdownPercent: Int = 80
    @State private var minutesPushTask: Task<Void, Never>?
    @State private var minutesWheelExpanded = false
    @State private var percentPushTask: Task<Void, Never>?
    @State private var percentWheelExpanded = false

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScreenHeader(title: "Automations") { dismiss() }

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

                    SectionLabel("Climate")
                    Spacer().frame(height: 8)
                    AutomationRow(
                        icon: "fanblades.fill",
                        title: "Keep climate on",
                        subtitle: "Keep the climate on when you leave the car. The automation stops after the set time, or when you return to the car.",
                        isOn: $climateKeep
                    ) {
                        if climateKeep {
                            ValuePickerFooter(
                                label: "Stop after",
                                unit: String(localized: "min"),
                                options: Array(climateKeepMinuteRange),
                                value: $climateKeepMinutes,
                                expanded: $minutesWheelExpanded
                            )
                        }
                    }
                    Spacer().frame(height: 8)
                    AcSwingRow()

                    Spacer().frame(height: 28)

                    SectionLabel("Driving")
                    Spacer().frame(height: 8)
                    AutomationRow(
                        icon: "gauge.with.needle",
                        title: "Kick-down",
                        subtitle: "Switch from Chill to Standard while the accelerator is pressed past the threshold. Reverts when you ease off.",
                        isOn: $sportKickdown
                    ) {
                        if sportKickdown {
                            ValuePickerFooter(
                                label: "Pedal threshold",
                                unit: "%",
                                options: sportKickdownPercentOptions,
                                value: $sportKickdownPercent,
                                expanded: $percentWheelExpanded
                            )
                        }
                    }
                }
                .padding(DashMetrics.screenPadding)
            }
        }
        // Child controls consume their own taps, so this only sees taps on
        // empty space: tapping outside the minutes wheel collapses it.
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation {
                minutesWheelExpanded = false
                percentWheelExpanded = false
            }
        }
        .navigationBarHidden(true)
        .onChange(of: wiperOff) { _, newValue in
            // Persisted by @AppStorage; arm/disarm the firmware immediately.
            if let manager = connectionVM.bleManager {
                VehicleControl.sendWiperOff(manager, enabled: newValue)
            }
        }
        .onChange(of: climateKeep) { _, newValue in
            if !newValue {
                minutesWheelExpanded = false
            }
            if let manager = connectionVM.bleManager {
                VehicleControl.sendClimateKeep(manager, enabled: newValue)
            }
        }
        .onChange(of: climateKeepMinutes) { _, newValue in
            // Debounced: the wheel fires once per detent while spinning.
            minutesPushTask?.cancel()
            minutesPushTask = Task {
                try? await Task.sleep(for: .milliseconds(400))
                guard !Task.isCancelled else { return }
                if let manager = connectionVM.bleManager {
                    VehicleControl.sendClimateKeepDuration(manager, minutes: newValue)
                }
            }
        }
        .onChange(of: sportKickdown) { _, newValue in
            if !newValue {
                percentWheelExpanded = false
            }
            if let manager = connectionVM.bleManager {
                VehicleControl.sendSportKickdown(manager, enabled: newValue)
            }
        }
        .onChange(of: sportKickdownPercent) { _, newValue in
            percentPushTask?.cancel()
            percentPushTask = Task {
                try? await Task.sleep(for: .milliseconds(400))
                guard !Task.isCancelled else { return }
                if let manager = connectionVM.bleManager {
                    VehicleControl.sendSportKickdownThreshold(manager, percent: newValue)
                }
            }
        }
    }

}

// MARK: - Section label

/// Small muted section heading (Android `SectionLabel`).
struct SectionLabel: View {
    let text: LocalizedStringKey

    init(_ text: LocalizedStringKey) {
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

/// A `.dashSurface` card with an icon, title/subtitle, a trailing toggle, and
/// an optional footer rendered inside the same card (Android `AutomationRow`).
struct AutomationRow<Footer: View>: View {
    let icon: String
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey?
    let isOn: Binding<Bool>?
    let footer: Footer

    init(
        icon: String,
        title: LocalizedStringKey,
        subtitle: LocalizedStringKey?,
        isOn: Binding<Bool>?,
        @ViewBuilder footer: () -> Footer
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.isOn = isOn
        self.footer = footer()
    }

    private var checked: Bool { isOn?.wrappedValue ?? false }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                IconChip(
                    systemName: icon,
                    tint: checked ? .dashAccent : .white,
                    background: checked ? Color.dashAccent.opacity(0.16) : Color.white.opacity(0.08)
                )

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

                if let isOn {
                    Toggle("", isOn: isOn)
                        .labelsHidden()
                        .tint(.dashAccent)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture {
                isOn?.wrappedValue.toggle()
            }

            footer
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
    }
}

extension AutomationRow where Footer == EmptyView {
    init(icon: String, title: LocalizedStringKey, subtitle: LocalizedStringKey?, isOn: Binding<Bool>) {
        self.init(icon: icon, title: title, subtitle: subtitle, isOn: isOn) { EmptyView() }
    }
}

// MARK: - AC swing

struct AcSwingRow: View {
    @Environment(ConnectionViewModel.self) private var connectionVM

    @AppStorage("ac_swing_side") private var side: Int = 0
    @AppStorage("ac_swing_intensity") private var intensity: Int = 2

    var body: some View {
        AutomationRow(
            icon: "wind",
            title: "AC swing mode",
            subtitle: "Start it from Controls or a multi-finger tap. Stops when you move a vent, leave the car, turn climate off, or after 30 minutes.",
            isOn: nil
        ) {
            ChoiceFooter(label: "Side", options: ["Driver", "Passenger", "Both"], selection: $side)
            ChoiceFooter(label: "Intensity", options: ["Low", "Medium", "Full"], selection: $intensity)
        }
        .onChange(of: side) { _, newValue in
            if let manager = connectionVM.bleManager {
                VehicleControl.sendAcSwingSide(manager, side: newValue)
            }
        }
        .onChange(of: intensity) { _, newValue in
            if let manager = connectionVM.bleManager {
                VehicleControl.sendAcSwingIntensity(manager, intensity: newValue)
            }
        }
    }
}

struct ChoiceFooter: View {
    let label: LocalizedStringKey
    let options: [LocalizedStringKey]
    @Binding var selection: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .foregroundColor(.dashTextMuted)
                .font(.system(size: 14))

            SegmentedSelector(options: options, selection: $selection)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 12)
    }
}

/// Pill-style segmented control (Android `SegmentedSelector`).
struct SegmentedSelector: View {
    let options: [LocalizedStringKey]
    @Binding var selection: Int
    var background: Color = .dashBackground

    var body: some View {
        HStack(spacing: 3) {
            ForEach(options.indices, id: \.self) { index in
                let isSelected = index == selection
                Button {
                    selection = index
                } label: {
                    Text(options[index])
                        .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                        .foregroundColor(isSelected ? .white : .dashTextMuted)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(isSelected ? Color.dashAccent : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.smallCorner))
    }
}

// MARK: - Value picker footer

/// "Label  N unit" line inside an automation card; tapping the value expands a
/// wheel over `options` (Android `NumberPickerFooter`).
struct ValuePickerFooter: View {
    let label: LocalizedStringKey
    let unit: String
    let options: [Int]
    @Binding var value: Int
    @Binding var expanded: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(label)
                    .foregroundColor(.dashTextMuted)
                    .font(.system(size: 14))
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button {
                    withAnimation { expanded.toggle() }
                } label: {
                    HStack(spacing: 2) {
                        Text("\(value) \(unit)")
                            .foregroundColor(.white)
                            .font(.system(size: 15))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.dashTextMuted)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                    }
                    .padding(.leading, 12)
                    .padding(.trailing, 8)
                    .padding(.vertical, 8)
                    .background(Color.dashBackground)
                    .clipShape(RoundedRectangle(cornerRadius: DashMetrics.smallCorner))
                }
            }
            .padding(.top, 10)

            if expanded {
                Picker("", selection: $value) {
                    ForEach(options, id: \.self) { option in
                        Text("\(option) \(unit)").tag(option)
                    }
                }
                .pickerStyle(.wheel)
                .frame(height: 120)
                .frame(maxWidth: .infinity)
                .environment(\.colorScheme, .dark)
            }
        }
    }
}

#Preview {
    NavigationStack {
        AutomationsView()
    }
    .environment(ConnectionViewModel())
}
