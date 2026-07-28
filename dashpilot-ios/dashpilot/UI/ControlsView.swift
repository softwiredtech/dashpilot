import SwiftUI

/// Vehicle control screen. Exposes the available DashKit commands as single
/// toggle/action buttons. Long-pressing a control pins it to the home screen.
///
/// Ported from Android `ControlScreen` (ControlScreen.kt). Commands are sent
/// over the live DashKit BLE link; without one the buttons are dimmed (in
/// debug builds they stay enabled and simulate, like Android).
struct ControlsView: View {

    @Environment(\.dismiss) var dismiss
    @Environment(ConnectionViewModel.self) var connectionVM

    @AppStorage("pinned_control_id") var pinnedControlId: String = ""

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

                    if !isConnected {
                        Spacer().frame(height: 4)
                        Text("Connect to DashKit to send commands")
                            .foregroundColor(.dashTextMuted)
                            .font(.system(size: 13))
                            .frame(maxWidth: .infinity)
                            .multilineTextAlignment(.center)
                    }

                    Spacer().frame(height: 24)

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
                .padding(DashMetrics.screenPadding)
            }
        }
        .navigationBarHidden(true)
    }

    // MARK: - Pinning

    /// Toggles the home-screen pin: clears it when the control is already
    /// pinned, otherwise pins this control (replacing any previous pin).
    private func togglePin(_ id: String) {
        pinnedControlId = (pinnedControlId == id) ? "" : id
    }
}
