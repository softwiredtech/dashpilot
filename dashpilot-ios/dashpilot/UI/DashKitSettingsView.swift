import SwiftUI

private let accent = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)
private let mutedText = Color(white: 0.53)
private let borderColor = Color(white: 0.2)
private let errorRed = Color(red: 1, green: 0.32, blue: 0.32)

/// DashKit tab of the Settings screen (port of the Android
/// `DashKitSettingsContent`): firmware info, OTA update, pairing window and
/// maintenance (reboot).
struct DashKitSettingsView: View {

    let bleManager: DashKitBleManager

    @State private var updateManager: FirmwareUpdateManager?
    @State private var showPairDialog = false
    @State private var showRebootDialog = false
    @State private var pairStatus: String?
    @State private var rebootStatus: String?

    private var connected: Bool {
        bleManager.connectionState == .connected
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            firmwareInfoSection
            if let updateManager {
                FirmwareUpdateSection(updateManager: updateManager)
            }
            pairingSection
            maintenanceSection
        }
        .onAppear {
            if updateManager == nil {
                updateManager = FirmwareUpdateManager(manager: bleManager)
            }
        }
        .onDisappear {
            updateManager?.dispose()
            updateManager = nil
        }
        // On connect, read the installed version and check for updates.
        .task(id: connected) {
            guard connected, let updateManager else { return }
            updateManager.start()
            await updateManager.checkForUpdate()
        }
        .alert("Pair a new device?", isPresented: $showPairDialog) {
            Button("Open pairing") {
                let ok = VehicleControl.sendEnterPairing(bleManager)
                pairStatus = ok
                    ? "DashKit is open for pairing. Pair the new phone within 2 minutes."
                    : "Connect to DashKit first to pair a new device"
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("DashKit will allow one new device to pair for the next 2 minutes. Open DashPilot on the other phone and pair it now.")
        }
        .alert("Reboot DashKit?", isPresented: $showRebootDialog) {
            Button("Reboot") {
                let ok = VehicleControl.sendReboot(bleManager)
                rebootStatus = ok
                    ? "Reboot command sent. DashKit is restarting."
                    : "Could not send reboot command."
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("DashKit will restart and briefly disconnect. It should reconnect automatically once it is back up.")
        }
    }

    // MARK: - Firmware info

    private var firmwareInfoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader("Firmware info")
            InfoRow(label: "Status", value: connected ? "Connected" : "Disconnected")
            InfoRow(label: "Firmware version", value: updateManager?.installedVersion ?? "Unknown")
        }
    }

    // MARK: - Pairing

    private var pairingSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader("Pairing")
            WideButton(label: "Pair a new device", enabled: connected) {
                showPairDialog = true
            }
            if !connected {
                Text("Connect to DashKit first to pair a new device")
                    .foregroundColor(mutedText)
                    .font(.system(size: 13))
            } else if let pairStatus {
                Text(pairStatus)
                    .foregroundColor(mutedText)
                    .font(.system(size: 13))
            }
        }
    }

    // MARK: - Maintenance

    private var maintenanceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader("Maintenance")
            WideButton(label: "Reboot DashKit", enabled: connected) {
                showRebootDialog = true
            }
            if let rebootStatus, connected {
                Text(rebootStatus)
                    .foregroundColor(mutedText)
                    .font(.system(size: 13))
            }
        }
    }
}

// MARK: - Firmware update section

private struct FirmwareUpdateSection: View {

    let updateManager: FirmwareUpdateManager

    /// While an upload is in progress (or just finished), show only OTA status.
    private var uploadActive: Bool {
        switch updateManager.otaState {
        case .uploading, .connecting, .rebooting, .error: return true
        case .idle: return false
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader("Firmware")

            if updateManager.downloading {
                Text("Downloading firmware…")
                    .foregroundColor(.white)
                    .font(.system(size: 14))
                ProgressView()
                    .tint(accent)
            } else if uploadActive {
                otaStatus
            } else {
                checkStatus
            }
        }
    }

    @ViewBuilder
    private var checkStatus: some View {
        switch updateManager.check {
        case .idle:
            EmptyView()
        case .checking:
            Text("Checking for updates…")
                .foregroundColor(mutedText)
                .font(.system(size: 14))
        case .upToDate:
            Text("DashKit firmware is up to date")
                .foregroundColor(mutedText)
                .font(.system(size: 14))
        case .available(let manifest):
            Text("Version \(manifest.version) is available")
                .foregroundColor(.white)
                .font(.system(size: 14))
            if let notes = manifest.notes, !notes.isEmpty {
                Text(notes)
                    .foregroundColor(mutedText)
                    .font(.system(size: 13))
            }
            WideButton(label: "Install update", enabled: true) {
                Task { await updateManager.install(manifest) }
            }
        case .error(let message):
            Text("Update failed: \(message)")
                .foregroundColor(errorRed)
                .font(.system(size: 14))
        }
    }

    @ViewBuilder
    private var otaStatus: some View {
        switch updateManager.otaState {
        case .idle:
            EmptyView()
        case .connecting:
            Text("Connecting…")
                .foregroundColor(mutedText)
                .font(.system(size: 14))
        case .uploading(let progress):
            Text("Uploading firmware… \(Int(progress * 100))%")
                .foregroundColor(.white)
                .font(.system(size: 14))
            ProgressView(value: progress)
                .tint(accent)
        case .rebooting:
            Text("Update complete! Rebooting DashKit…")
                .foregroundColor(accent)
                .font(.system(size: 14))
        case .error(let message):
            Text("Update failed: \(message)")
                .foregroundColor(errorRed)
                .font(.system(size: 14))
            WideButton(label: "Update DashKit Firmware", enabled: true) {
                Task { await updateManager.checkForUpdate() }
            }
        }
    }
}

// MARK: - Shared pieces

private struct SectionHeader: View {
    let title: String

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .foregroundColor(mutedText)
            .font(.system(size: 16, weight: .medium))
    }
}

private struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.white)
                .font(.system(size: 16))
            Spacer()
            Text(value)
                .foregroundColor(mutedText)
                .font(.system(size: 16))
        }
        .padding(.vertical, 4)
    }
}

private struct WideButton: View {
    let label: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(enabled ? .white : mutedText)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
        }
        .background(enabled ? accent : borderColor)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .disabled(!enabled)
    }
}
