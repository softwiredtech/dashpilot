import SwiftUI
import UniformTypeIdentifiers

private let accent = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)
private let mutedText = Color(white: 0.53)
private let borderColor = Color(white: 0.2)
private let secondaryFill = Color(white: 0.17)
private let errorRed = Color(red: 1, green: 0.32, blue: 0.32)

/// DashKit tab of the Settings screen (port of the Android
/// `DashKitSettingsContent`): firmware info, OTA update, pairing window and
/// maintenance (reboot).
struct DashKitSettingsView: View {

    let bleManager: DashKitBleManager
    let updateManager: FirmwareUpdateManager

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
            FirmwareUpdateSection(updateManager: updateManager, connected: connected)
            pairingSection
            maintenanceSection
        }
        // On connect, read the installed version and check for updates.
        .task(id: connected) {
            guard connected else { return }
            updateManager.start()
            await updateManager.checkForUpdate()
        }
        .alert("Pair a new device?", isPresented: $showPairDialog) {
            Button("Open pairing") {
                let ok = VehicleControl.sendEnterPairing(bleManager)
                pairStatus = ok
                    ? String(localized: "DashKit is open for pairing. Pair the new phone within 2 minutes.")
                    : String(localized: "Connect to DashKit first to pair a new device")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("DashKit will allow one new device to pair for the next 2 minutes. Open DashPilot on the other phone and pair it now.")
        }
        .alert("Reboot DashKit?", isPresented: $showRebootDialog) {
            Button("Reboot") {
                let ok = VehicleControl.sendReboot(bleManager)
                rebootStatus = ok
                    ? String(localized: "Reboot command sent. DashKit is restarting.")
                    : String(localized: "Could not send reboot command.")
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
            InfoRow(label: "Status", value: connected ? String(localized: "Connected") : String(localized: "Disconnected"))
            InfoRow(label: "Firmware version", value: installedVersionText)
        }
    }

    private var installedVersionText: String {
        guard let version = updateManager.installedVersion else { return String(localized: "Unknown") }
        return updateManager.localFirmware ? String(localized: "\(version) (from file)") : version
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

private struct PickedFirmware {
    let name: String
    let bytes: Data
}

private struct FirmwareUpdateSection: View {

    let updateManager: FirmwareUpdateManager
    let connected: Bool

    @State private var showFileImporter = false
    @State private var picked: PickedFirmware?
    @State private var fileError: String?

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
                if updateManager.localFirmware {
                    Text("DashKit is running a firmware installed from a file. Install the published version to go back to official releases.")
                        .foregroundColor(mutedText)
                        .font(.system(size: 13))
                }
                WideButton(label: "Install firmware from file", enabled: connected && updateManager.check != .checking, style: .secondary) {
                    fileError = nil
                    showFileImporter = true
                }
                if let fileError {
                    Text(fileError)
                        .foregroundColor(errorRed)
                        .font(.system(size: 13))
                }
            }
        }
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.data]) { result in
            switch result {
            case .success(let url):
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                if let bytes = try? Data(contentsOf: url) {
                    picked = PickedFirmware(name: url.lastPathComponent, bytes: bytes)
                } else {
                    fileError = String(localized: "Could not read the selected file")
                }
            case .failure:
                fileError = String(localized: "Could not read the selected file")
            }
        }
        .alert("Install firmware from file?", isPresented: Binding(get: { picked != nil }, set: { if !$0 { picked = nil } }), presenting: picked) { firmware in
            Button("Install") {
                if !updateManager.installLocal(firmware.bytes) {
                    fileError = String(localized: "The selected file is not a DashKit firmware image")
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: { firmware in
            Text("\(firmware.name) (\(ByteCountFormatter.string(fromByteCount: Int64(firmware.bytes.count), countStyle: .file))) will be uploaded to DashKit. Only install firmware built for DashKit.")
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
            Text(updateManager.localFirmware
                 ? "Published version \(manifest.version) is available"
                 : "Version \(manifest.version) is available")
                .foregroundColor(.white)
                .font(.system(size: 14))
            if let notes = manifest.notes, !notes.isEmpty {
                Text(notes)
                    .foregroundColor(mutedText)
                    .font(.system(size: 13))
            }
            WideButton(label: updateManager.localFirmware ? "Install published version" : "Install update", enabled: true) {
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
            Text(String(format: String(localized: "Uploading firmware… %d%%"), Int(progress * 100)))
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
                updateManager.cancel()
                Task { await updateManager.checkForUpdate() }
            }
        }
    }
}

// MARK: - Shared pieces

private struct SectionHeader: View {
    let title: LocalizedStringKey

    init(_ title: LocalizedStringKey) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .foregroundColor(mutedText)
            .font(.system(size: 16, weight: .medium))
    }
}

private struct InfoRow: View {
    let label: LocalizedStringKey
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
    enum Style { case primary, secondary }

    let label: LocalizedStringKey
    let enabled: Bool
    var style: Style = .primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(enabled ? .white : mutedText)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
        }
        .background(enabled ? (style == .primary ? accent : secondaryFill) : borderColor)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .disabled(!enabled)
    }
}
