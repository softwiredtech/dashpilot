import SwiftUI

struct ThemePickerView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage(DisplaySettings.keySelectedDashboardId) private var selectedDashboardId = ""

    var body: some View {
        ZStack {
            Color.dashBackground.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 24) {
                    ScreenHeader(title: "Dashboard gallery") { dismiss() }
                        .padding(.horizontal, 24)
                    DashboardGallery(selectedDashboardId: $selectedDashboardId)
                }
                .padding(.vertical, 24)
            }
        }
        .navigationBarHidden(true)
    }
}

struct DashboardGallery: View {
    @Binding var selectedDashboardId: String
    @State private var focusedId = ""
    @State private var previewDashboard: DashboardConfig?

    private var effectiveSelectedId: String {
        dashboardById(selectedDashboardId)?.id ?? availableDashboards[0].id
    }

    private var focused: DashboardConfig {
        availableDashboards.first(where: { $0.id == focusedId }) ?? availableDashboards[0]
    }

    private var focusedIndex: Int {
        availableDashboards.firstIndex(where: { $0.id == focusedId }) ?? 0
    }

    private var visibleIndices: [Int] {
        let count = availableDashboards.count
        return [(focusedIndex + count - 1) % count, focusedIndex, (focusedIndex + 1) % count]
    }

    private func moveFocus(_ step: Int) {
        let count = availableDashboards.count
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            focusedId = availableDashboards[(focusedIndex + step + count) % count].id
        }
    }

    var body: some View {
        VStack(spacing: 12) {
            GeometryReader { geometry in
                let cardWidth = min(geometry.size.width * 0.72, 480)
                ZStack {
                    ForEach(visibleIndices, id: \.self) { index in
                        let dashboard = availableDashboards[index]
                        let isFocused = index == focusedIndex
                        let direction: CGFloat = isFocused ? 0 : (index == visibleIndices[0] ? -1 : 1)
                        Button {
                            if isFocused {
                                previewDashboard = dashboard
                            } else {
                                moveFocus(Int(direction))
                            }
                        } label: {
                            ZStack {
                                Color.dashSurface
                                if let name = dashboard.screenshotName, UIImage(named: name) != nil {
                                    Image(name).resizable().scaledToFit()
                                } else {
                                    Text(dashboard.name).foregroundColor(.white)
                                }
                            }
                            .frame(width: cardWidth, height: cardWidth * 9 / 16)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(.white.opacity(isFocused ? 1 : 0.35), lineWidth: 2)
                            }
                            .shadow(color: .black.opacity(0.5), radius: 12, y: 8)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(dashboard.name)
                        .accessibilityHint(Text(isFocused
                            ? LocalizedStringKey("Preview this dashboard")
                            : LocalizedStringKey("Show this dashboard in the center")))
                        .scaleEffect(isFocused ? 1 : 0.82)
                        .opacity(isFocused ? 1 : 0.65)
                        .offset(x: direction * cardWidth * 0.43, y: isFocused ? 0 : 12)
                        .zIndex(isFocused ? 2 : 1)
                        .transition(.opacity)
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
                .contentShape(Rectangle())
                .simultaneousGesture(
                    DragGesture(minimumDistance: 20).onEnded { value in
                        guard abs(value.translation.width) > 36,
                              abs(value.translation.width) > abs(value.translation.height) else { return }
                        moveFocus(value.translation.width < 0 ? 1 : -1)
                    }
                )
                .animation(.spring(response: 0.35, dampingFraction: 0.85), value: focusedId)
                .clipped()
            }
            .frame(height: 200)
            .accessibilityLabel("Dashboard previews")
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: moveFocus(1)
                case .decrement: moveFocus(-1)
                @unknown default: break
                }
            }

            HStack {
                Text(focused.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                Spacer()
                Text("\(focusedIndex + 1) of \(availableDashboards.count)")
                    .font(.system(size: 13))
                    .foregroundColor(.dashTextMuted)
            }
            .padding(.horizontal, 24)

            HStack(spacing: 12) {
                Button { previewDashboard = focused } label: {
                    Text("Preview").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                Button {
                    selectedDashboardId = focused.id
                } label: {
                    Text(focused.id == effectiveSelectedId
                        ? LocalizedStringKey("Selected")
                        : LocalizedStringKey("Use dashboard"))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(focused.id == effectiveSelectedId)
            }
            .tint(.dashAccent)
            .padding(.horizontal, 24)

            Text("Swipe left or right to browse. Preview uses simulated driving data.")
                .font(.system(size: 13))
                .foregroundColor(.dashTextMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
        }
        .onAppear {
            if focusedId.isEmpty {
                focusedId = availableDashboards.first(where: { $0.id == effectiveSelectedId })?.id
                    ?? availableDashboards[0].id
            }
        }
        .fullScreenCover(item: $previewDashboard) { dashboard in
            DashboardPreviewView(dashboard: dashboard)
        }
    }
}

private struct DashboardPreviewView: View {
    let dashboard: DashboardConfig
    @Environment(\.dismiss) private var dismiss
    @State private var previewConnection = ConnectionViewModel()

    var body: some View {
        DashboardView(dashboardType: dashboard.type.rawValue, dashboardUrl: dashboard.url, isPreview: true)
            .environment(previewConnection)
            .overlay(alignment: .topTrailing) {
                Button("Close preview") { dismiss() }
                    .buttonStyle(.borderedProminent)
                    .tint(.black.opacity(0.75))
                    .padding(12)
            }
            .onAppear { previewConnection.connectDemo() }
            .onDisappear { previewConnection.disconnect() }
    }
}
