import SwiftUI

/// Back navigation for detail screens, without a page title.
struct ScreenHeader: View {
    let onBack: () -> Void

    var body: some View {
        ZStack {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(Text("Back"))
                // Pull the button left by its touch-target inset so the arrow
                // glyph — not the 44pt target — lines up with the content edge.
                .offset(x: -12)
                Spacer()
            }
        }
    }
}

/// 36pt rounded-square icon chip used on stat cards and list rows (port of the
/// Android 36dp Box + 20dp Icon pattern).
struct IconChip: View {
    let systemName: String
    var tint: Color = .white
    var background: Color = Color.white.opacity(0.08)

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 18, weight: .medium))
            .foregroundColor(tint)
            .frame(width: 36, height: 36)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
