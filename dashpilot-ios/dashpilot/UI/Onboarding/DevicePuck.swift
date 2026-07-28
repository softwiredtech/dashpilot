import SwiftUI

/// Pairing progression on the onboarding pairing page (port of the Android
/// `PairingState`, plus a `failed` state that Android does not have: iOS
/// surfaces the "pairing window closed" firmware rejection here).
enum PairingState: Equatable {
    case idle
    case searching
    case paired
    case failed
}

/// Animated hero graphic of the DashKit device: a dark puck with a status LED,
/// pulsing radar rings while searching and a check badge once paired (pure
/// SwiftUI port of the Android `DevicePuck`).
struct DevicePuck: View {

    let state: PairingState

    @State private var ringsAnimating = false
    @State private var ledPulsing = false

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            // Matches the Android vector: puck radius 68 in a 220 viewport.
            let puckDiameter = side * 0.618
            let ledOffset = CGSize(width: 32, height: -22)

            ZStack {
                // Radar rings while searching.
                if state == .searching {
                    ForEach(0..<3, id: \.self) { i in
                        Circle()
                            .stroke(OnboardingStyle.accent, lineWidth: 1)
                            .frame(width: puckDiameter, height: puckDiameter)
                            .scaleEffect(ringsAnimating ? 1.6 : 1.0)
                            .opacity(ringsAnimating ? 0.0 : 0.6)
                            .animation(
                                .linear(duration: 1.8)
                                    .repeatForever(autoreverses: false)
                                    .delay(Double(i) * 0.6),
                                value: ringsAnimating
                            )
                    }
                }

                // Device body.
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(white: 0.18), Color(white: 0.08)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
                    .frame(width: puckDiameter, height: puckDiameter)

                // Inner face detail.
                Circle()
                    .strokeBorder(Color.white.opacity(0.06), lineWidth: 1)
                    .frame(width: puckDiameter * 0.62, height: puckDiameter * 0.62)

                // LED glow when paired.
                if state == .paired {
                    Circle()
                        .fill(OnboardingStyle.accent.opacity(0.15))
                        .frame(width: 24, height: 24)
                        .offset(ledOffset)
                    Circle()
                        .fill(OnboardingStyle.accent.opacity(0.35))
                        .frame(width: 14, height: 14)
                        .offset(ledOffset)
                }

                // Status LED.
                Circle()
                    .fill(ledColor)
                    .frame(width: 8, height: 8)
                    .scaleEffect(state == .paired && ledPulsing ? 1.0 : 0.85)
                    .animation(
                        state == .paired
                            ? .linear(duration: 1.2).repeatForever(autoreverses: true)
                            : .default,
                        value: ledPulsing
                    )
                    .offset(ledOffset)

                // Check badge once paired.
                if state == .paired {
                    CheckBadge()
                        .offset(x: 48, y: 56)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .animation(.spring(response: 0.4, dampingFraction: 0.5), value: state == .paired)
        }
        .onAppear { syncAnimations() }
        .onChange(of: state) { _, _ in syncAnimations() }
    }

    private var ledColor: Color {
        switch state {
        case .paired: return OnboardingStyle.accent
        case .failed: return Color(red: 1, green: 0.32, blue: 0.32)
        case .idle, .searching: return OnboardingStyle.ledDim
        }
    }

    private func syncAnimations() {
        ringsAnimating = state == .searching
        ledPulsing = state == .paired
    }
}

private struct CheckBadge: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(OnboardingStyle.accent)
            Circle()
                .stroke(Color.black, lineWidth: 3)
            Image(systemName: "checkmark")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.black)
        }
        .frame(width: 36, height: 36)
    }
}
