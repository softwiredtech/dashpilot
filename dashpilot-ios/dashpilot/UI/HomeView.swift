import SwiftUI

struct HomeView: View {
    @Binding var navigationPath: NavigationPath
    @Environment(ConnectionViewModel.self) var connectionVM

    @AppStorage("pinned_control_id") var pinnedControlId: String = ""
    // Read the session's cached snapshot immediately when returning to Drive.
    // A new per-view stream starts asynchronously and can flash empty values.
    private var dash: DashState? {
        connectionVM.connectionStatus == .disconnected ? nil : connectionVM.latestDashState
    }

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            GeometryReader { geo in
                let landscape = geo.size.width > geo.size.height
                ScrollView {
                    VStack(spacing: 0) {
                        if landscape {
                            header(showDataSource: true)
                            Spacer().frame(height: 8)
                        }
                        if landscape {
                            landscapeContent(
                                minHeight: geo.size.height - 2 * DashMetrics.screenPadding - 52
                            )
                        } else {
                            portraitContent
                        }
                    }
                    .padding(DashMetrics.screenPadding)
                }
            }
        }
        .navigationBarHidden(true)
    }

    private var portraitContent: some View {
        VStack(spacing: 0) {
            widgetGrid(fillHeight: false)
            Spacer().frame(height: 32)
            if hasPinnedControl {
                pinnedSection
                Spacer().frame(height: 24)
            }
            actionButtons
            Spacer().frame(height: 12)
            DataSourceMenu(navigationPath: $navigationPath)
        }
    }

    private func landscapeContent(minHeight: CGFloat) -> some View {
        HStack(alignment: .top, spacing: DashMetrics.screenPadding) {
            widgetGrid(fillHeight: true)
            VStack(spacing: DashMetrics.gridGap) {
                VStack(spacing: 12) {
                    Spacer(minLength: 0)
                    if let control = pinnedControl {
                        pinnedButton(control)
                    }
                    driveButton
                }
                .frame(maxHeight: .infinity)
            }
        }
        .frame(minHeight: minHeight)
    }

    private func header(showDataSource: Bool) -> some View {
        ZStack {
            HStack {
                Spacer()
                if showDataSource {
                    DataSourceMenu(navigationPath: $navigationPath, alignment: .trailing)
                        .offset(x: 12)
                }
            }
        }
        .frame(height: 44)
    }

    private func widgetGrid(fillHeight: Bool) -> some View {
        VStack(spacing: DashMetrics.gridGap) {
            HStack(spacing: DashMetrics.gridGap) {
                Button {
                    navigationPath.append(AppRoute.battery)
                } label: {
                    InfoWidget(
                        icon: "battery.100.bolt",
                        label: "home_widget_battery",
                        value: socText,
                        fillHeight: fillHeight
                    )
                }
                .buttonStyle(.plain)
                Button {
                    navigationPath.append(AppRoute.battery)
                } label: {
                    InfoWidget(
                        icon: "thermometer.medium",
                        label: "Battery Temp",
                        value: batteryTempText,
                        fillHeight: fillHeight
                    )
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: DashMetrics.gridGap) {
                Button {
                    navigationPath.append(AppRoute.climate)
                } label: {
                    InfoWidget(
                        icon: "snowflake",
                        label: "AC Setpoint",
                        value: acTempText,
                        fillHeight: fillHeight
                    )
                }
                .buttonStyle(.plain)
                InfoWidget(
                    icon: "gauge.with.dots.needle.bottom.50percent",
                    label: "Odometer",
                    value: odometerText,
                    fillHeight: fillHeight
                )
            }
        }
    }

    private var pinnedControl: ControlAction? {
        pinnedControlId.isEmpty ? nil : controlById(pinnedControlId)
    }

    private var hasPinnedControl: Bool { pinnedControl != nil }

    @ViewBuilder
    private var pinnedSection: some View {
        if let control = pinnedControl {
            VStack(alignment: .leading, spacing: 8) {
                Text("Pinned")
                    .foregroundColor(.dashTextMuted)
                    .font(.system(size: 13, weight: .semibold))
                pinnedButton(control)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func pinnedButton(_ control: ControlAction) -> some View {
        ControlActionButton(
            action: control,
            pinned: true,
            enabled: connectionVM.bleManager != nil,
            onTap: { control.perform(connectionVM.bleManager) },
            onLongPress: {}
        )
    }

    private var actionButtons: some View {
        VStack(spacing: 12) {
            driveButton
        }
    }

    private var driveButton: some View {
        ActionButton(
            label: "Start driving",
            icon: "car.fill",
            accent: true,
            enabled: connectionVM.connectionStatus == .connected
        ) {
            let dashboard = selectedDashboard()
            navigationPath.append(
                AppRoute.dashboard(type: dashboard.type.rawValue, url: dashboard.url)
            )
        }
    }

    private var socText: String {
        guard let car = dash?.carState else { return "—" }
        let fullPack = car.fullPackEnergy
        let remaining = car.nominalEnergyRemaining
        let buffer = car.energyBuffer
        guard fullPack > 0, remaining > 0, fullPack - buffer > 0 else { return "—" }
        let soc = Int(((remaining - buffer) / (fullPack - buffer) * 100).rounded())
        return "\(soc)%"
    }

    private var acTempText: String {
        guard let car = dash?.carState, car.acTemp != 0 else { return "—" }
        return "\(Int(car.acTemp.rounded()))°"
    }

    private var batteryTempText: String {
        guard let car = dash?.carState else { return "—" }
        let tMin = car.packTMin
        let tMax = car.packTMax
        guard tMin != 0 || tMax != 0 else { return "—" }
        let avg = (tMin + tMax) / 2
        return "\(Int(avg.rounded()))°"
    }

    private var odometerText: String {
        guard let state = dash, state.carState.odometer > 0 else { return "—" }
        let unit = state.displaySettings.useImperial ? "mi" : "km"
        return "\(Int(state.carState.odometer.rounded())) \(unit)"
    }
}

// Separate view so 25 Hz dash updates don't rebuild the open Menu (taps get dropped).
private struct DataSourceMenu: View {
    @Binding var navigationPath: NavigationPath
    var alignment: Alignment = .center
    @Environment(ConnectionViewModel.self) var connectionVM

    var body: some View {
        VStack(spacing: 4) {
            Menu {
                if isIdle {
                    Button("comma") { navigationPath.append(AppRoute.setup) }
                    Button("DashKit") { connectionVM.connectDashKit() }
                    Button("Demo") { connectionVM.connectDemo() }
                } else {
                    Button("Disconnect") { connectionVM.disconnect() }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(dataSourceLabel)
                        .foregroundColor(.dashTextMuted)
                        .font(.system(size: 13))
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 9))
                        .foregroundColor(.dashTextMuted)
                }
                .padding(.horizontal, 12)
                .frame(height: 36)
            }
            if case .error(let message) = connectionVM.connectionStatus {
                Text(message)
                    .foregroundColor(.dashError)
                    .font(.system(size: 12))
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, alignment: alignment)
    }

    private var isIdle: Bool {
        switch connectionVM.connectionStatus {
        case .disconnected, .error: return true
        case .connecting, .connected: return false
        }
    }

    private var dataSourceLabel: LocalizedStringKey {
        let name = connectionVM.activeSourceType?.displayName
            ?? String(localized: "Data source")
        switch connectionVM.connectionStatus {
        case .connected: return "\(name) connected"
        case .connecting: return "Connecting to \(name)…"
        case .disconnected, .error: return "Data source"
        }
    }
}

private struct InfoWidget: View {
    let icon: String
    let label: LocalizedStringKey
    let value: String
    var fillHeight: Bool = false

    var body: some View {
        if fillHeight {
            card
        } else {
            card.aspectRatio(1.4, contentMode: .fit)
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            IconChip(
                systemName: icon,
                tint: .dashAccent,
                background: Color.dashAccent.opacity(0.14)
            )
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .foregroundColor(.white)
                    .font(.system(size: 26, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(label)
                    .foregroundColor(.dashTextMuted)
                    .font(.system(size: 13))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
    }
}

private struct ActionButton: View {
    let label: LocalizedStringKey
    let icon: String
    var accent: Bool = false
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                Text(label)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: DashMetrics.buttonHeight)
        }
        .background(accent ? Color.dashAccent : Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: DashMetrics.corner))
        .disabled(!enabled)
        .opacity(enabled ? 1.0 : 0.5)
    }
}
