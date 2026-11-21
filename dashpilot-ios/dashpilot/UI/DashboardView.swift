import SwiftUI

struct DashboardView: View {

    let dashboardType: String
    let dashboardUrl: String
    var isPreview = false

    @Environment(ConnectionViewModel.self) var connectionVM

    /// This view's own subscription, recreated on every dashboard switch. The
    /// web view's coordinator consumes it and cancels it when it is torn down.
    @State private var dashStream: AsyncStream<DashState>?

    /// Set after the first swipe; nil means "show what the route passed in".
    @State private var swipedDashboard: DashboardConfig?
    @State private var switchedToName: String?
    @State private var dashAppEditing = false
    @State private var nameOverlayDismiss: Task<Void, Never>?

    private static let swipeableDashboards = availableDashboards.filter { $0.type == .web || $0.type == .rive }

    private var currentType: String { swipedDashboard?.type.rawValue ?? dashboardType }
    private var currentUrl: String { swipedDashboard?.url ?? dashboardUrl }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Group {
                if let dashStream {
                    switch currentType {
                    case DashboardType.web.rawValue:
                        WebDashView(url: currentUrl, incomingMessages: dashStream,
                                    onEditingChange: { dashAppEditing = $0 })
                            .id(currentUrl)
                    case DashboardType.rive.rawValue:
                        RiveDashView(source: Bundle.main.url(forResource: currentUrl, withExtension: "riv"),
                                     gpuCanvas: riveDashboardRequiresGpuCanvas(url: currentUrl),
                                     incomingMessages: dashStream)
                            .id(currentUrl)
                    case DashboardType.devRive.rawValue:
                        // A developer-loaded file has no manifest, so it always gets GPU Canvas.
                        RiveDashView(source: DevRiveFile.url, gpuCanvas: true, incomingMessages: dashStream)
                    default:
                        Text("Unsupported dashboard type: \(currentType)")
                            .foregroundColor(.white)
                    }
                } else {
                    Color.black
                }
            }

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
                    // A drag from the left edge is the system back swipe.
                    guard value.startLocation.x > 40 else { return }
                    guard abs(dx) > 60, abs(dx) > abs(value.translation.height) else { return }
                    switchDashboard(step: dx < 0 ? 1 : -1)
                },
            // Suspended while a dash-app's own editor is open, so dragging a
            // widget does not swipe to the next dashboard.
            including: dashAppEditing ? .subviews : .all
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

    private func switchDashboard(step: Int) {
        guard !isPreview else { return }
        let dashboards = Self.swipeableDashboards
        guard dashboards.count > 1, currentType != DashboardType.devRive.rawValue else { return }
        guard let currentIndex = dashboards.firstIndex(where: { $0.url == currentUrl }) else { return }

        let next = dashboards[(currentIndex + step + dashboards.count) % dashboards.count]
        swipedDashboard = next
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
