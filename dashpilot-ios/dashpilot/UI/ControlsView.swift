import SwiftUI

/// Vehicle control screen with two tabs. Controls exposes the available
/// DashKit commands as single toggle/action buttons; long-pressing a control
/// pins it to the home screen. Multi-touch binds multi-finger infotainment
/// taps to those controls (`MultiTouchSection`).
///
/// Ported from Android `ControlScreen` (ControlScreen.kt). Commands are sent
/// over the live DashKit BLE link; without one the buttons are dimmed (in
/// debug builds they stay enabled and simulate, like Android).
struct ControlsView: View {

    @Environment(\.dismiss) var dismiss
    @Environment(ConnectionViewModel.self) var connectionVM

    @AppStorage("pinned_control_id") var pinnedControlId: String = ""
    @State private var selectedTab = 0

    private var isConnected: Bool {
        connectionVM.bleManager != nil && connectionVM.connectionStatus == .connected
    }

    private var controlsEnabled: Bool {
        #if DEBUG
        return true
        #else
        return isConnected
        #endif
    }

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScreenHeader(title: "Controls") { dismiss() }

                    Spacer().frame(height: 16)
                    SegmentedSelector(
                        options: ["Controls", "Multi-touch"],
                        selection: $selectedTab,
                        background: .dashSurface
                    )
                    Spacer().frame(height: 24)

                    if selectedTab == 0 {
                        controlsTab
                    } else {
                        MultiTouchSection()
                    }
                }
                .padding(DashMetrics.screenPadding)
            }
        }
        .navigationBarHidden(true)
    }

    private var controlsTab: some View {
        VStack(spacing: 0) {
            if !isConnected {
                Text("Connect to DashKit to send commands")
                    .foregroundColor(.dashTextMuted)
                    .font(.system(size: 13))
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 16)
            }

            VStack(spacing: 12) {
                ForEach(vehicleControls) { control in
                    ControlActionButton(
                        action: control,
                        pinned: control.id == pinnedControlId,
                        enabled: controlsEnabled,
                        onTap: { control.perform(connectionVM.bleManager) },
                        onLongPress: { togglePin(control.id) }
                    )
                }
            }

            Spacer().frame(height: 20)
            Text("Tip: long-press a control to pin it to the home screen")
                .foregroundColor(.dashTextSubtle)
                .font(.system(size: 12))
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Pinning

    /// Toggles the home-screen pin: clears it when the control is already
    /// pinned, otherwise pins this control (replacing any previous pin).
    private func togglePin(_ id: String) {
        pinnedControlId = (pinnedControlId == id) ? "" : id
    }
}
