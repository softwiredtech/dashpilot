import SwiftUI

struct DashboardView: View {

    let dashboardType: String
    let dashboardUrl: String

    @Environment(ConnectionViewModel.self) var connectionVM
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topLeading) {
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
        }
        .ignoresSafeArea()
        .navigationBarHidden(true)
        .statusBar(hidden: true)
        .persistentSystemOverlays(.hidden)
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }
}
