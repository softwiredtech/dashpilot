import SwiftUI

private let dashGreen = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)
private let bgColor = Color(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0)
private let mutedText = Color(white: 0.53)
private let borderColor = Color(white: 0.2)

struct ThemePickerView: View {

    @Environment(\.dismiss) private var dismiss

    @AppStorage(DisplaySettings.keySelectedDashboardId) private var selectedDashboardId = ""

    private let columns = [GridItem(.adaptive(minimum: 160), spacing: 12)]

    // Falls back to the default dashboard so the checkmark matches what
    // actually loads when nothing has been picked yet.
    private var effectiveSelectedId: String {
        dashboardById(selectedDashboardId)?.id ?? availableDashboards[0].id
    }

    var body: some View {
        ZStack {
            bgColor.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                Divider().background(borderColor)
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Some settings depend on the selected theme")
                            .foregroundColor(mutedText)
                            .font(.system(size: 13))

                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(availableDashboards.filter { $0.type != .devRive }) { dashboard in
                                themeCard(for: dashboard)
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                }
            }
        }
        .navigationBarHidden(true)
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "arrow.backward")
                    .foregroundColor(.white)
                    .font(.system(size: 18, weight: .medium))
                    .frame(width: 44, height: 44)
            }
            Text("Dashboard Theme")
                .foregroundColor(.white)
                .font(.system(size: 18, weight: .semibold))
            Spacer()
        }
        .padding(.horizontal, 8)
        .frame(height: 56)
        .background(bgColor)
    }

    // MARK: - Theme Card

    private func themeCard(for dashboard: DashboardConfig) -> some View {
        let isSelected = dashboard.id == effectiveSelectedId
        return VStack(spacing: 0) {
            ZStack(alignment: .topTrailing) {
                thumbnail(for: dashboard)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                if isSelected {
                    Image(systemName: "checkmark")
                        .foregroundColor(.white)
                        .font(.system(size: 12, weight: .bold))
                        .frame(width: 24, height: 24)
                        .background(dashGreen)
                        .clipShape(Circle())
                        .padding(8)
                }
            }
            Text(dashboard.name)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(isSelected ? dashGreen : .white)
                .padding(.vertical, 8)
        }
        .background(Color(white: 0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isSelected ? dashGreen : Color.clear, lineWidth: 2)
        )
        .onTapGesture {
            selectedDashboardId = dashboard.id
            dismiss()
        }
    }

    private func thumbnail(for dashboard: DashboardConfig) -> some View {
        Color(white: 0.16)
            .aspectRatio(16.0 / 9.0, contentMode: .fit)
            .overlay {
                if let name = dashboard.screenshotName, UIImage(named: name) != nil {
                    Image(name)
                        .resizable()
                        .scaledToFill()
                }
            }
            .clipped()
    }
}
