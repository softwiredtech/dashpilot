import SwiftUI

struct DashboardSelectionView: View {

    private let columns = [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)]

    var body: some View {
        ZStack {
            Color(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                VStack(spacing: 4) {
                    Text("dashpilot")
                        .foregroundColor(.white)
                        .font(.system(size: 28, weight: .bold))
                        .tracking(1)
                    Text("Choose a dashboard")
                        .foregroundColor(Color(white: 0.53))
                        .font(.system(size: 14))
                }

                Spacer().frame(height: 32)

                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(availableDashboards) { dashboard in
                        if dashboard.type == .devRive {
                            DashboardCard(dashboard: dashboard)
                                .onTapGesture {}
                        } else {
                            NavigationLink(value: AppRoute.dashboard(
                                type: dashboard.type.rawValue,
                                url: dashboard.url
                            )) {
                                DashboardCard(dashboard: dashboard)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 4)

                Spacer()
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 48)
        }
        .navigationBarHidden(true)
    }
}

private struct DashboardCard: View {

    let dashboard: DashboardConfig

    var body: some View {
        VStack(spacing: 0) {
            if dashboard.type == .devRive {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(white: 0.16))
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
            } else if let name = dashboard.screenshotName, let _ = UIImage(named: name) {
                Image(name)
                    .resizable()
                    .scaledToFill()
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(white: 0.16))
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
            }

            Spacer().frame(height: 10)

            Text(dashboard.name)
                .foregroundColor(.white)
                .font(.system(size: 14, weight: .medium))
        }
        .padding(12)
        .background(Color(white: 0.1))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
