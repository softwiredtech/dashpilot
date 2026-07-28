import SwiftUI

/// Full-screen onboarding pager shown when a DashKit is found nearby but has
/// not been set up on this phone yet (port of the Android `OnboardingScreen`).
/// Two pages — pairing and tips — behind a 3-step indicator where the pairing
/// page counts as two steps (searching → paired). Skip is only offered on the
/// pairing page.
struct OnboardingView: View {

    let onFinish: () -> Void
    let onSkip: () -> Void

    private static let pairingPage = 0
    private static let tipsPage = 1
    private static let totalSteps = 3

    @State private var page = OnboardingView.pairingPage
    @State private var pairingState: PairingState = .idle

    private var onPairingPage: Bool { page == Self.pairingPage }
    private var canAdvance: Bool { !onPairingPage || pairingState == .paired }
    private var currentStep: Int {
        if onPairingPage {
            return pairingState == .paired ? 2 : 1
        }
        return Self.totalSteps
    }

    var body: some View {
        ZStack {
            OnboardingStyle.background.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                Spacer().frame(height: 12)
                stepIndicator
                Spacer().frame(height: 24)

                TabView(selection: $page) {
                    FadeUpOnEnter {
                        OnboardingPairingPage(state: $pairingState) {
                            withAnimation { page = Self.tipsPage }
                        }
                    }
                    .tag(Self.pairingPage)

                    FadeUpOnEnter {
                        OnboardingTipsPage(onAdvance: onFinish)
                    }
                    .tag(Self.tipsPage)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .scrollDisabled(!canAdvance)
            }
            .padding(.horizontal, 16)
        }
    }

    private var topBar: some View {
        HStack {
            Text("Setup")
                .foregroundColor(OnboardingStyle.textPrimary)
                .font(.system(size: OnboardingStyle.topTitleSize, weight: .medium))
                .kerning(-0.5)
            Spacer()
            if onPairingPage {
                Button("Skip", action: onSkip)
                    .foregroundColor(OnboardingStyle.textMuted)
                    .font(.system(size: OnboardingStyle.bodySize))
            }
        }
        .frame(height: 44)
    }

    private var stepIndicator: some View {
        HStack(spacing: 6) {
            ForEach(0..<Self.totalSteps, id: \.self) { i in
                RoundedRectangle(cornerRadius: 2)
                    .fill(i < currentStep ? OnboardingStyle.accent : OnboardingStyle.stepInactive)
                    .frame(width: 16, height: 3)
            }
            Spacer().frame(width: 4)
            Text("\(currentStep) of \(Self.totalSteps)")
                .foregroundColor(OnboardingStyle.textMuted)
                .font(.system(size: OnboardingStyle.captionSize))
            Spacer()
        }
        .animation(.easeInOut(duration: 0.25), value: currentStep)
    }
}

/// Slide-up + fade entrance for a page's content (port of the Android
/// `FadeUpOnEnter`).
private struct FadeUpOnEnter<Content: View>: View {
    @ViewBuilder let content: Content
    @State private var visible = false

    var body: some View {
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : -8)
            .onAppear {
                withAnimation(.easeOut(duration: 0.35)) { visible = true }
            }
    }
}
