import SwiftUI

struct ControlsView: View {
    var isRoot = false
    @Environment(\.dismiss) var dismiss
    @Environment(ConnectionViewModel.self) var connectionVM

    @AppStorage("pinned_control_id") var pinnedControlId: String = ""
    @State private var selectedTab = 0
    @State private var expandedCategories: Set<ControlCategory> = []

    private var isConnected: Bool {
        connectionVM.bleManager != nil && connectionVM.connectionStatus == .connected
    }

    private var controlsEnabled: Bool {
        #if DEBUG
        return true
        #else
        return isConnected
        #endif
    }

    var body: some View {
        ZStack {
            Color.dashBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if isRoot {
                        Text("Commands")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 44)
                    } else {
                        ScreenHeader(title: "Commands") { dismiss() }
                    }

                    Spacer().frame(height: 16)
                    SegmentedSelector(
                        options: ["Controls", "Multi-touch"],
                        selection: $selectedTab,
                        background: .dashSurface
                    )
                    Spacer().frame(height: 24)

                    if selectedTab == 0 {
                        controlsTab
                    } else {
                        MultiTouchSection()
                    }
                }
                .padding(DashMetrics.screenPadding)
            }
        }
        .navigationBarHidden(true)
    }

    private var controlsTab: some View {
        VStack(spacing: 0) {
            if !isConnected {
                Text("Connect to DashKit to send commands")
                    .foregroundColor(.dashTextMuted)
                    .font(.system(size: 13))
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 16)
            }

            VStack(spacing: 12) {
                ForEach(ControlCategory.allCases) { category in
                    categorySection(category)
                }
            }

            Spacer().frame(height: 20)
            Text("Tip: long-press a control to pin it to Drive")
                .foregroundColor(.dashTextSubtle)
                .font(.system(size: 12))
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
        }
    }

    private func togglePin(_ id: String) {
        pinnedControlId = (pinnedControlId == id) ? "" : id
    }

    private func categorySection(_ category: ControlCategory) -> some View {
        let controls = vehicleControls.filter { $0.category == category }
        return DisclosureGroup(isExpanded: Binding(
            get: { expandedCategories.contains(category) },
            set: { expanded in
                if expanded { expandedCategories.insert(category) }
                else { expandedCategories.remove(category) }
            }
        )) {
            VStack(spacing: 10) {
                ForEach(controls) { control in
                    ControlActionButton(
                        action: control,
                        pinned: control.id == pinnedControlId,
                        enabled: controlsEnabled,
                        onTap: { control.perform(connectionVM.bleManager) },
                        onLongPress: { togglePin(control.id) }
                    )
                }
            }
            .padding(.top, 12)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: category.icon)
                    .foregroundColor(.dashAccent)
                    .frame(width: 24)
                    .accessibilityHidden(true)
                Text(category.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                Spacer()
                Text(controls.count, format: .number)
                    .font(.system(size: 13))
                    .foregroundColor(.dashTextMuted)
            }
            .frame(minHeight: 32)
        }
        .tint(.dashAccent)
        .padding(16)
        .background(Color.dashSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .animation(.easeInOut(duration: 0.2), value: expandedCategories)
    }
}
