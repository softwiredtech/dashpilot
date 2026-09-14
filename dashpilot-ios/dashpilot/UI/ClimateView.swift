import SwiftUI

struct ClimateView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(ConnectionViewModel.self) private var connectionVM

    @AppStorage("climate_keep_automation") private var climateKeep: Bool = false
    @AppStorage("climate_keep_minutes") private var climateKeepMinutes: Int = 5
    @State private var minutesPushTask: Task<Void, Never>?
    @State private var minutesWheelExpanded = false

    @State private var dash: DashState?

    private var car: CarState? { dash?.carState }
    private var imperial: Bool { dash?.displaySettings.useImperial ?? false }
    // A real setpoint is never 0, so 0 means no UI_hvacRequest seen yet.
    private var live: Bool { (car?.acTemp ?? 0) != 0 }

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScreenHeader(title: "Climate") { dismiss() }

                    Spacer().frame(height: 24)

                    setpointCard

                    Spacer().frame(height: 28)

                    sectionTitle("Automation")
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
                                unit: "min",
                                options: Array(climateKeepMinuteRange),
                                value: $climateKeepMinutes,
                                expanded: $minutesWheelExpanded
                            )
                        }
                    }

                    Spacer().frame(height: 28)

                    section("Setpoints", rows: [
                        ("Driver", setpointText(car?.acTemp)),
                        ("Passenger", setpointText(car?.acTempRight))
                    ])

                    Spacer().frame(height: 28)

                    section("Settings", rows: [
                        ("Power", enumText(car?.hvacPowerState, Self.powerLabels)),
                        ("Fan", fanText(car?.hvacFanLevel)),
                        ("A/C", enumText(car?.hvacAcMode, Self.acLabels)),
                        ("Recirculation", enumText(car?.hvacRecirc, Self.recircLabels)),
                        ("Keep climate", enumText(car?.hvacKeepClimateOn, Self.keepLabels))
                    ])


                    Spacer().frame(height: 32)
                }
                .padding(DashMetrics.screenPadding)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation { minutesWheelExpanded = false }
        }
        .navigationBarHidden(true)
        .task {
            for await state in connectionVM.dashStateStream() {
                dash = state
            }
        }
        .onChange(of: connectionVM.connectionStatus) { _, status in
            if status == .disconnected {
                dash = nil
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
            minutesPushTask?.cancel()
            minutesPushTask = Task {
                try? await Task.sleep(for: .milliseconds(400))
                guard !Task.isCancelled else { return }
                if let manager = connectionVM.bleManager {
                    VehicleControl.sendClimateKeepDuration(manager, minutes: newValue)
                }
            }
        }
    }

    // MARK: - Setpoint card

    private var setpointCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                Text(setpointText(car?.acTemp))
                    .foregroundColor(.white)
                    .font(.system(size: 44, weight: .bold))
                Spacer()
                IconChip(
                    systemName: "snowflake",
                    tint: .dashAccent,
                    background: Color.dashAccent.opacity(0.14)
                )
            }
            Text("Driver setpoint")
                .foregroundColor(.dashTextMuted)
                .font(.system(size: 13))
        }
        .padding(16)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
    }

    // MARK: - Sections

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .foregroundColor(.dashTextMuted)
            .font(.system(size: 13, weight: .semibold))
    }

    private func section(_ title: String, rows: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle(title)
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    HStack {
                        Text(row.0)
                            .foregroundColor(.dashTextMuted)
                            .font(.system(size: 15))
                        Spacer()
                        Text(row.1)
                            .foregroundColor(.white)
                            .font(.system(size: 16, weight: .semibold))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    if index < rows.count - 1 {
                        Divider()
                            .background(Color.dashBorder)
                            .padding(.leading, 16)
                    }
                }
            }
            .background(Color.dashSurface)
            .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
        }
    }

    // MARK: - Formatters

    private static let powerLabels = ["Off", "On", "Preconditioning", "Overheat protection (fan)", "Overheat protection"]
    private static let acLabels = ["Auto", "Off", "On"]
    private static let recircLabels = ["Auto", "Recirculate", "Fresh air"]
    private static let keepLabels = ["Off", "Keep", "Dog mode", "Camp mode"]

    private func setpointText(_ value: Float?) -> String {
        guard let value, value != 0 else { return "—" }
        let lo: Float = imperial ? 59 : 15
        let hi: Float = imperial ? 82.4 : 28
        if value <= lo { return "LO" }
        if value >= hi { return "HI" }
        return "\(Int(value.rounded()))°"
    }

    private func enumText(_ value: Float?, _ labels: [String]) -> String {
        guard live, let value else { return "—" }
        let index = Int(value.rounded())
        return labels.indices.contains(index) ? labels[index] : "—"
    }

    private func fanText(_ value: Float?) -> String {
        guard live, let value else { return "—" }
        switch Int(value.rounded()) {
        case 0: return "Off"
        case 11: return "Auto"
        case 1...10: return "\(Int(value.rounded()))"
        default: return "—"
        }
    }
}
