import SwiftUI

struct BatteryView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(ConnectionViewModel.self) private var connectionVM

    @State private var dash: DashState?

    private var car: CarState? { dash?.carState }

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScreenHeader(title: "Battery") { dismiss() }

                    Spacer().frame(height: 24)

                    socCard

                    Spacer().frame(height: 28)

                    section("Energy", rows: [
                        ("Remaining", energyText(car?.nominalEnergyRemaining)),
                        ("Usable", usableEnergyText),
                        ("Full Pack", energyText(car?.fullPackEnergy)),
                        ("Buffer", bufferText)
                    ])

                    Spacer().frame(height: 28)

                    section("Power", rows: [
                        ("Power", powerText),
                        ("Max Discharge", kilowattText(car?.maxDischargePower)),
                        ("Max Regen", kilowattText(car?.maxRegenPower))
                    ])

                    Spacer().frame(height: 28)

                    section("Pack", rows: [
                        ("Voltage", voltageText),
                        ("Current", currentText),
                        ("Temp Min", tempText(car?.packTMin)),
                        ("Temp Max", tempText(car?.packTMax))
                    ])

                    Spacer().frame(height: 32)
                }
                .padding(DashMetrics.screenPadding)
            }
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
    }

    // MARK: - SOC card

    private var socCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                Text(socText)
                    .foregroundColor(.white)
                    .font(.system(size: 44, weight: .bold))
                Spacer()
                IconChip(
                    systemName: "battery.100.bolt",
                    tint: .dashAccent,
                    background: Color.dashAccent.opacity(0.14)
                )
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.dashSurfaceSelected)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.dashAccent)
                        .frame(width: geo.size.width * CGFloat(socFraction))
                }
            }
            .frame(height: 8)
            Text("State of charge")
                .foregroundColor(.dashTextMuted)
                .font(.system(size: 13))
        }
        .padding(16)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
    }

    // MARK: - Sections

    private func section(_ title: String, rows: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .foregroundColor(.dashTextMuted)
                .font(.system(size: 13, weight: .semibold))
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

    private var socFraction: Float {
        guard let car, car.fullPackEnergy > 0, car.nominalEnergyRemaining > 0,
              car.fullPackEnergy - car.energyBuffer > 0 else { return 0 }
        let soc = (car.nominalEnergyRemaining - car.energyBuffer) / (car.fullPackEnergy - car.energyBuffer)
        return min(max(soc, 0), 1)
    }

    private var socText: String {
        guard let car, car.fullPackEnergy > 0, car.nominalEnergyRemaining > 0,
              car.fullPackEnergy - car.energyBuffer > 0 else { return "—" }
        return "\(Int((socFraction * 100).rounded()))%"
    }

    private func energyText(_ value: Float?) -> String {
        guard let value, value > 0 else { return "—" }
        return String(format: "%.1f kWh", value)
    }

    private var usableEnergyText: String {
        guard let car, car.nominalEnergyRemaining > 0 else { return "—" }
        return String(format: "%.1f kWh", max(car.nominalEnergyRemaining - car.energyBuffer, 0))
    }

    private var bufferText: String {
        guard let car, car.fullPackEnergy > 0 else { return "—" }
        return String(format: "%.1f kWh", car.energyBuffer)
    }

    private func kilowattText(_ value: Float?) -> String {
        guard let value, value > 0 else { return "—" }
        return String(format: "%.0f kW", value)
    }

    private var powerText: String {
        guard let car, car.packVoltage > 0 else { return "—" }
        return String(format: "%.1f kW", car.packVoltage * car.packCurrent / 1000)
    }

    private var voltageText: String {
        guard let car, car.packVoltage > 0 else { return "—" }
        return String(format: "%.1f V", car.packVoltage)
    }

    private var currentText: String {
        guard let car, car.packVoltage > 0 else { return "—" }
        return String(format: "%.1f A", car.packCurrent)
    }

    private func tempText(_ value: Float?) -> String {
        guard let value, value != 0 else { return "—" }
        return "\(Int(value.rounded()))°"
    }
}
