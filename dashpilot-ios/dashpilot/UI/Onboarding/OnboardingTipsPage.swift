import SwiftUI

/// Second onboarding page: static tips + "Get started" CTA (port of the
/// Android `TipsPage`).
struct OnboardingTipsPage: View {

    let onAdvance: () -> Void

    var body: some View {
        OnboardingPageScaffold(
            title: "One more thing",
            subtitle: "You can pair additional devices and install firmware updates anytime from Settings."
        ) {
            EmptyView()
        } extra: {
            VStack(spacing: 8) {
                TipRow(systemImage: "ipad.and.iphone", text: "Pair more DashKit devices")
                TipRow(systemImage: "arrow.down.circle", text: "Check for firmware updates")
            }
        } cta: {
            OnboardingPrimaryButton(
                label: "Get started",
                systemImage: "checkmark.circle",
                action: onAdvance
            )
        }
    }
}

private struct TipRow: View {
    let systemImage: String
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: OnboardingStyle.radiusSmall)
                    .fill(OnboardingStyle.accent.opacity(0.15))
                Image(systemName: systemImage)
                    .font(.system(size: 12))
                    .foregroundColor(OnboardingStyle.accent)
            }
            .frame(width: 24, height: 24)

            Text(text)
                .foregroundColor(OnboardingStyle.textPrimary)
                .font(.system(size: OnboardingStyle.bodySize))

            Spacer()
        }
        .padding(12)
        .background(OnboardingStyle.surface)
        .clipShape(RoundedRectangle(cornerRadius: OnboardingStyle.radiusSmall))
    }
}
