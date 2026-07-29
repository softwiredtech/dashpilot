import SwiftUI

/// Landing screen ported from the Android app's `ConnectedHomeContent`
/// (HomeScreen.kt): vehicle info widget grid + pinned control + primary
/// actions. No Bluetooth yet — widgets show "—" until live data arrives.
struct HomeView: View {

    @Binding var navigationPath: NavigationPath
    @Environment(ConnectionViewModel.self) var connectionVM

    @AppStorage("pinned_control_id") var pinnedControlId: String = ""
    @State private var dash: DashState?

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            VStack(spacing: 0) {
                header
                Spacer().frame(height: 8)
                widgetGrid
                Spacer().frame(height: 32)
                pinnedSection
                actionButtons
                Spacer().frame(height: 12)
                dataSourceMenu
                Spacer()
            }
            .padding(DashMetrics.screenPadding)
        }
        .navigationBarHidden(true)
        .task {
            // Each appearance opens its own subscription (with the latest
            // state replayed), so navigating away cancels only this view's
            // stream — the pipeline keeps feeding the other views.
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

    // MARK: - Header

    private var header: some View {
        ZStack {
            HStack {
                Button {
                    navigationPath.append(AppRoute.settings)
                } label: {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 22))
                        .foregroundColor(.dashTextMuted)
                        .frame(width: 44, height: 44)
                }
                .offset(x: -12)
                Spacer()
            }
            Text("DashPilot")
                .foregroundColor(.white)
                .font(.system(size: 20, weight: .bold))
        }
    }

    // MARK: - Data source menu

    /// Centered "Data source" menu below the actions (port of the Android
    /// `DebugDataSourceMenu`): pick a source while idle, disconnect while a
    /// session is up. Selecting DashKit retries the BLE link; comma leads to
    /// the WiFi connect/setup flow.
    private var dataSourceMenu: some View {
        VStack(spacing: 4) {
            Menu {
                if isIdle {
                    Button("comma") { navigationPath.append(AppRoute.setup) }
                    Button("DashKit") { connectionVM.connectDashKit() }
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
        .frame(maxWidth: .infinity)
    }

    private var isIdle: Bool {
        switch connectionVM.connectionStatus {
        case .disconnected, .error: return true
        case .connecting, .connected: return false
        }
    }

    private var dataSourceLabel: String {
        switch connectionVM.connectionStatus {
        case .connected: return "Data source: connected"
        case .connecting: return "Data source: connecting…"
        case .disconnected, .error: return "Data source"
        }
    }

    // MARK: - Widget grid

    private var widgetGrid: some View {
        VStack(spacing: DashMetrics.gridGap) {
            HStack(spacing: DashMetrics.gridGap) {
                InfoWidget(
                    icon: "battery.100.bolt",
                    label: "Battery",
                    value: socText
                )
                InfoWidget(
                    icon: "thermometer.medium",
                    label: "Battery Temp",
                    value: batteryTempText
                )
            }
            HStack(spacing: DashMetrics.gridGap) {
                InfoWidget(
                    icon: "snowflake",
                    label: "AC Temp",
                    value: acTempText
                )
                InfoWidget(
                    icon: "gauge.with.dots.needle.bottom.50percent",
                    label: "Odometer",
                    value: odometerText
                )
            }
        }
    }

    // MARK: - Pinned control

    @ViewBuilder
    private var pinnedSection: some View {
        if !pinnedControlId.isEmpty, let control = controlById(pinnedControlId) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Pinned")
                    .foregroundColor(.dashTextMuted)
                    .font(.system(size: 13, weight: .semibold))
                ControlActionButton(
                    action: control,
                    pinned: true,
                    enabled: connectionVM.bleManager != nil,
                    onTap: { control.perform(connectionVM.bleManager) },
                    onLongPress: {}
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer().frame(height: 24)
        }
    }

    // MARK: - Actions

    private var actionButtons: some View {
        VStack(spacing: 12) {
            ActionButton(label: "Automations", icon: "sparkles") {
                navigationPath.append(AppRoute.automations)
            }
            ActionButton(label: "Controls", icon: "slider.horizontal.3") {
                navigationPath.append(AppRoute.controls)
            }
            ActionButton(label: "Drive", icon: "car.fill", accent: true) {
                let dashboard = selectedDashboard()
                navigationPath.append(
                    AppRoute.dashboard(type: dashboard.type.rawValue, url: dashboard.url)
                )
            }
        }
    }

    // MARK: - Formatters (ported from HomeScreen.kt)

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

// MARK: - Info widget

/// Square-ish stat card: accent icon at top, big value + muted label at bottom.
private struct InfoWidget: View {

    let icon: String
    let label: String
    let value: String

    var body: some View {
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
        .aspectRatio(1.4, contentMode: .fit)
    }
}

// MARK: - Action button

/// Full-width primary action button (icon + label). `accent` renders the
/// green accent background; otherwise the standard dark surface.
private struct ActionButton: View {

    let label: String
    let icon: String
    var accent: Bool = false
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
    }
}
