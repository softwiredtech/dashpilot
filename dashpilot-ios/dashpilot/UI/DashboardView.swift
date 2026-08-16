import SwiftUI

struct DashboardView: View {

    let dashboardType: String
    let dashboardUrl: String

    @Environment(ConnectionViewModel.self) var connectionVM
    @Environment(\.dismiss) private var dismiss

    /// This view's own subscription, recreated on every dashboard switch. The
    /// web view's coordinator consumes it and cancels it when it is torn down.
    @State private var dashStream: AsyncStream<DashState>?

    /// Set after the first swipe; nil means "show what the route passed in".
    @State private var swipedDashboard: DashboardConfig?
    @State private var switchedToName: String?
    @State private var nameOverlayDismiss: Task<Void, Never>?

    /// Only web dashboards participate in the swipe carousel.
    private static let swipeableDashboards = availableDashboards.filter { $0.type == .web }

    private var currentType: String { swipedDashboard?.type.rawValue ?? dashboardType }
    private var currentUrl: String { swipedDashboard?.url ?? dashboardUrl }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Group {
                if let dashStream {
                    switch currentType {
                    case DashboardType.web.rawValue:
                        WebDashView(url: currentUrl, incomingMessages: dashStream)
                            .id(currentUrl)
                    default:
                        Text("Unsupported dashboard type: \(currentType)")
                            .foregroundColor(.white)
                    }
                } else {
                    Color.black
                }
            }

            Button {
                dismiss()
            } label: {
                Image(systemName: "arrow.backward")
                    .foregroundColor(.white)
                    .font(.system(size: 18, weight: .medium))
                    .frame(width: 44, height: 44)
                    .background(Color.black.opacity(0.4))
                    .clipShape(Circle())
            }
            .padding(.top, 12)
            .padding(.leading, 12)

            if let switchedToName {
                Text(switchedToName)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.4))
                    .clipShape(Capsule())
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 16)
                    .transition(.opacity)
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 30)
                .onEnded { value in
                    let dx = value.translation.width
                    guard abs(dx) > 60, abs(dx) > abs(value.translation.height) else { return }
                    switchDashboard(step: dx < 0 ? 1 : -1)
                }
        )
        .ignoresSafeArea()
        .navigationBarHidden(true)
        .statusBar(hidden: true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
            if dashStream == nil {
                dashStream = connectionVM.dashStateStream()
            }
        }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    /// Moves `step` (+1 next, -1 previous) through the web dashboards,
    /// wrapping at both ends. Non-web screens (e.g. the Rive dev view)
    /// are not part of the cycle and cannot be swiped away from.
    private func switchDashboard(step: Int) {
        let dashboards = Self.swipeableDashboards
        guard dashboards.count > 1, currentType == DashboardType.web.rawValue else { return }
        guard let currentIndex = dashboards.firstIndex(where: { $0.url == currentUrl }) else { return }

        let next = dashboards[(currentIndex + step + dashboards.count) % dashboards.count]
        swipedDashboard = next
        // Fresh subscription for the new web view; the old coordinator cancels
        // its own on teardown.
        dashStream = connectionVM.dashStateStream()
        UserDefaults.standard.set(next.id, forKey: DisplaySettings.keySelectedDashboardId)

        nameOverlayDismiss?.cancel()
        withAnimation(.easeIn(duration: 0.15)) { switchedToName = next.name }
        nameOverlayDismiss = Task {
            try? await Task.sleep(for: .seconds(1.5))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.4)) { switchedToName = nil }
        }
    }
}
