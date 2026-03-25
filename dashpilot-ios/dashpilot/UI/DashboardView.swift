import SwiftUI

struct DashboardView: View {

    let dashboardType: String
    let dashboardUrl: String

    @Environment(ConnectionViewModel.self) var connectionVM

    var body: some View {
        Group {
            if let ds = connectionVM.dataSource {
                switch dashboardType {
                case DashboardType.web.rawValue:
                    WebDashView(url: dashboardUrl, incomingMessages: ds.incomingMessages)
                default:
                    Text("Unsupported dashboard type: \(dashboardType)")
                        .foregroundColor(.white)
                }
            } else {
                Color.black
            }
        }
        .ignoresSafeArea()
        .navigationBarHidden(true)
        .statusBar(hidden: true)
        .persistentSystemOverlays(.hidden)
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }
}
