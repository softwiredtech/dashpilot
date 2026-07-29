import SwiftUI

struct DashboardView: View {

    let dashboardType: String
    let dashboardUrl: String

    @Environment(ConnectionViewModel.self) var connectionVM
    @Environment(\.dismiss) private var dismiss

    /// This view's own subscription, created once per appearance. The web
    /// view's coordinator consumes it and cancels it when it is torn down.
    @State private var dashStream: AsyncStream<DashState>?

    var body: some View {
        ZStack(alignment: .topLeading) {
            Group {
                if let dashStream {
                    switch dashboardType {
                    case DashboardType.web.rawValue:
                        WebDashView(url: dashboardUrl, incomingMessages: dashStream)
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
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
            if dashStream == nil {
                dashStream = connectionVM.dashStateStream()
            }
        }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }
}
