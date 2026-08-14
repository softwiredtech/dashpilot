import SwiftUI

/// First onboarding page: pairs the nearby DashKit (port of the Android
/// `PairingPage`). The CTA drives the real connection through
/// `ConnectionViewModel`; the page watches `connectionStatus` to flip the
/// pairing state. Unlike Android it also has a failure state, because DashKit
/// rejects new phones while its pairing window is closed.
struct OnboardingPairingPage: View {

    @Environment(ConnectionViewModel.self) private var connectionVM

    @Binding var state: PairingState
    let onAdvance: () -> Void

    var body: some View {
        OnboardingPageScaffold(
            title: title,
            subtitle: subtitle
        ) {
            DevicePuck(state: state)
                .frame(width: 220, height: 220)
        } extra: {
            EmptyView()
        } cta: {
            OnboardingPrimaryButton(
                label: ctaLabel,
                systemImage: ctaIcon,
                showSpinner: state == .searching,
                enabled: state != .searching
            ) {
                switch state {
                case .idle, .failed:
                    state = .searching
                    connectionVM.connectDashKit()
                case .searching:
                    break
                case .paired:
                    onAdvance()
                }
            }
        }
        .onAppear {
            if connectionVM.connectionStatus == .connected { state = .paired }
        }
        .onChange(of: connectionVM.connectionStatus) { _, status in
            switch status {
            case .connected:
                if state != .paired { state = .paired }
            case .error:
                if state == .searching { state = .failed }
            case .disconnected:
                if state == .searching { state = .idle }
            case .connecting:
                break
            }
        }
    }

    private var title: String {
        switch state {
        case .idle: return "Pair your DashKit"
        case .searching: return "Looking for DashKit…"
        case .paired: return "You're all set!"
        case .failed: return "Couldn't pair"
        }
    }

    private var subtitle: String {
        switch state {
        case .idle:
            return "Plug the DashKit into your car's debug port, then tap below to pair over Bluetooth."
        case .searching:
            return "Hold the device close to your phone. This usually takes a few seconds."
        case .paired:
            return "DashKit is connected and ready to stream data to your dashboard."
        case .failed:
            return "If this DashKit is already paired with another phone, open DashPilot "
                + "on that phone and tap Settings → DashKit → Pair a new device, then try again."
        }
    }

    private var ctaLabel: String {
        switch state {
        case .idle: return "Pair device"
        case .searching: return "Pairing…"
        case .paired: return "Continue"
        case .failed: return "Retry"
        }
    }

    private var ctaIcon: String? {
        switch state {
        case .idle: return "antenna.radiowaves.left.and.right"
        case .failed: return "arrow.clockwise"
        case .searching, .paired: return nil
        }
    }
}
