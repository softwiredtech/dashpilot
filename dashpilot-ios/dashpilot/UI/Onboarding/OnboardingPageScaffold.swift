import SwiftUI

/// Shared visual tokens for the onboarding flow (port of the Android
/// `OnboardingColors` / `OnboardingTokens`).
enum OnboardingStyle {
    static let accent = Color(red: 0x22 / 255.0, green: 0xC5 / 255.0, blue: 0x5E / 255.0)
    static let background = Color.black
    static let surface = Color.white.opacity(0.08)
    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.55)
    static let textMuted = Color.white.opacity(0.45)
    static let stepInactive = Color.white.opacity(0.18)
    static let ledDim = Color(red: 0x1F / 255.0, green: 0x3A / 255.0, blue: 0x1F / 255.0)

    static let radiusButton: CGFloat = 12
    static let radiusSmall: CGFloat = 8

    static let topTitleSize: CGFloat = 22
    static let pageTitleSize: CGFloat = 18
    static let bodySize: CGFloat = 13
    static let captionSize: CGFloat = 11
}

/// Shared vertical layout for onboarding pages (port of the Android
/// `OnboardingPageScaffold`): hero takes the upper flex region, title/subtitle
/// anchor mid-screen, optional extra content sits between subtitle and CTA,
/// and the primary CTA pins to the bottom.
struct OnboardingPageScaffold<Hero: View, Extra: View, Cta: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder let hero: Hero
    @ViewBuilder let extra: Extra
    @ViewBuilder let cta: Cta

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                hero
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text(title)
                .foregroundColor(OnboardingStyle.textPrimary)
                .font(.system(size: OnboardingStyle.pageTitleSize, weight: .medium))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)

            Spacer().frame(height: 8)

            Text(subtitle)
                .foregroundColor(OnboardingStyle.textSecondary)
                .font(.system(size: OnboardingStyle.bodySize))
                .multilineTextAlignment(.center)
                .lineSpacing(OnboardingStyle.bodySize * 0.5)
                .padding(.horizontal, 16)

            if !(extra is EmptyView) {
                Spacer().frame(height: 20)
                extra
            }

            Spacer().frame(height: 24)
            cta
            Spacer().frame(height: 16)
        }
    }
}

/// Full-width accent CTA button (port of the Android `PrimaryCta`).
struct OnboardingPrimaryButton: View {
    let label: String
    var systemImage: String? = nil
    var showSpinner = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if showSpinner {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.black)
                } else if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 15, weight: .medium))
                }
                Text(label)
                    .font(.system(size: 14, weight: .medium))
            }
            .foregroundColor(.black)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(OnboardingStyle.accent)
            .clipShape(RoundedRectangle(cornerRadius: OnboardingStyle.radiusButton))
        }
        .disabled(!enabled)
    }
}
