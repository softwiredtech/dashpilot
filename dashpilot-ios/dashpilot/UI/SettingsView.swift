import SwiftUI

private let dashGreen = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)
private let bgColor = Color(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0)
private let mutedText = Color(white: 0.53)
private let borderColor = Color(white: 0.2)

struct SettingsView: View {

    @Environment(\.dismiss) private var dismiss

    @AppStorage(DisplaySettings.keyExtraVehicleBus)           private var extraVehicleBus = false
    @AppStorage(DisplaySettings.keyShowCarBattery)            private var showCarBattery = true
    @AppStorage(DisplaySettings.keyShowOdometer)              private var showOdometer = true
    @AppStorage(DisplaySettings.keyUseImperial)               private var useImperial = false
    @AppStorage(DisplaySettings.keyShowPhoneBattery)          private var showPhoneBattery = true
    @AppStorage(DisplaySettings.keyAlwaysOnBlindSpotMonitor)  private var alwaysOnBlindSpotMonitor = true
    @AppStorage(DisplaySettings.keyDarkMode)                  private var darkMode = false
    @AppStorage(DisplaySettings.keyRenderQuality)             private var renderQuality = 3
    @AppStorage(DisplaySettings.keySelectedDashboardId)       private var selectedDashboardId = ""

    private var showVanillaSettings: Bool {
        selectedDashboardId == "vanilla"
    }

    var body: some View {
        ZStack {
            bgColor.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                Divider().background(borderColor)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        dashboardSection
                        configurationSection
                        displaySection
                        if showVanillaSettings {
                            visualizerSection
                        }
                        versionRow
                        Spacer().frame(height: 32)
                    }
                    .padding(.top, 16)
                }
            }
        }
        .navigationBarHidden(true)
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "arrow.backward")
                    .foregroundColor(.white)
                    .font(.system(size: 18, weight: .medium))
                    .frame(width: 44, height: 44)
            }
            Text("Settings")
                .foregroundColor(.white)
                .font(.system(size: 18, weight: .semibold))
            Spacer()
        }
        .padding(.horizontal, 8)
        .frame(height: 56)
        .background(bgColor)
    }

    // MARK: - Dashboard Section

    private var dashboardSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle("Dashboard")
                .padding(.horizontal, 24)
            Text("Choose a dashboard")
                .foregroundColor(mutedText)
                .font(.system(size: 13))
                .padding(.horizontal, 24)
            Spacer().frame(height: 4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(availableDashboards.filter { $0.type != .devRive }) { dashboard in
                        let isSelected = dashboard.id == selectedDashboardId
                        VStack(spacing: 0) {
                            dashboardThumbnail(for: dashboard)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            Text(dashboard.name)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(isSelected ? dashGreen : mutedText)
                                .padding(.vertical, 8)
                        }
                        .frame(width: 148)
                        .background(Color(white: 0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(isSelected ? dashGreen : Color.clear, lineWidth: 2)
                        )
                        .onTapGesture {
                            selectedDashboardId = dashboard.id
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 4)
            }
            Spacer().frame(height: 8)
        }
    }

    @ViewBuilder
    private func dashboardThumbnail(for dashboard: DashboardConfig) -> some View {
        if let name = dashboard.screenshotName, UIImage(named: name) != nil {
            Image(name)
                .resizable()
                .scaledToFill()
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                .clipped()
        } else {
            Rectangle()
                .fill(Color(white: 0.16))
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
        }
    }

    // MARK: - Configuration Section

    private var configurationSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle("Configuration")
            SettingsToggleRow(label: "Extra Vehicle Bus", isOn: $extraVehicleBus)
            Spacer().frame(height: 8)
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Display Section

    private var displaySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle("Display")
            if extraVehicleBus {
                SettingsToggleRow(label: "Show Car Battery", isOn: $showCarBattery)
                SettingsToggleRow(label: "Show Odometer", isOn: $showOdometer)
            }
            SettingsToggleRow(label: "Use Imperial Units", isOn: $useImperial)
            SettingsToggleRow(label: "Show Phone Battery", isOn: $showPhoneBattery)
            Spacer().frame(height: 8)
        }
        .padding(.horizontal, 24)
    }

    // MARK: - 3D Visualizer Section

    private var visualizerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle("3D Visualizer")
            SettingsToggleRow(label: "Always On Blind-Spot Monitor", isOn: $alwaysOnBlindSpotMonitor)
            SettingsToggleRow(label: "Dark Mode", isOn: $darkMode)
            renderQualityRow
            Spacer().frame(height: 8)
        }
        .padding(.horizontal, 24)
    }

    private var renderQualityRow: some View {
        HStack {
            Text("Render Quality")
                .foregroundColor(.white)
                .font(.system(size: 16))
            Spacer()
            RenderQualityPicker(selected: $renderQuality)
        }
        .padding(.vertical, 4)
    }

    // MARK: - Version

    private var versionRow: some View {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "–"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "–"
        return HStack {
            Text("Version")
                .foregroundColor(.white)
                .font(.system(size: 16))
            Spacer()
            Text("\(version) (\(build))")
                .foregroundColor(mutedText)
                .font(.system(size: 16))
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
    }

    // MARK: - Helpers

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .foregroundColor(mutedText)
            .font(.system(size: 16, weight: .medium))
            .padding(.top, 8)
            .padding(.bottom, 4)
    }
}

// MARK: - Toggle Row

private struct SettingsToggleRow: View {
    let label: String
    @Binding var isOn: Bool

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.white)
                .font(.system(size: 16))
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0))
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Render Quality Picker

private struct RenderQualityPicker: View {
    @Binding var selected: Int

    private let options: [(Int, String)] = [(1, "Low"), (2, "Med"), (3, "High")]
    private let accent = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.0) { value, label in
                Button {
                    selected = value
                } label: {
                    Text(label)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(selected == value ? .white : Color(white: 0.53))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(selected == value ? accent : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .background(Color(white: 0.18))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
