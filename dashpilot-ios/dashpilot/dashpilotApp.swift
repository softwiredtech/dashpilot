//
//  dashpilotApp.swift
//  dashpilot
//
//  Created by Pál Gábor on 2026. 03. 24..
//

import SwiftUI

@main
struct dashpilotApp: App {

    @Environment(\.scenePhase) private var scenePhase

    @State private var connectionVM = ConnectionViewModel()
    @State private var navigationPath = NavigationPath()
    @State private var selectedTab = 0
    @State private var dashboardVisible = false
    @State private var dashboardPreviewPresented = false
    @State private var autoStartGate = AutoStartDriveGate()
    @AppStorage(DisplaySettings.keyAutoStartDrive) private var autoStartDrive = false

    @AppStorage(ConnectionViewModel.onboardingCompletedKey)
    private var onboardingCompleted = false

    /// The first `.active` scene phase is the cold launch, which is handled by
    /// `startupAutoConnect()`; only later activations are "foregrounded".
    @State private var sawFirstActivation = false

    init() {
        let webIds = availableDashboards.filter { $0.type == .web }.map { $0.id }
        let riveIds = availableDashboards.filter { $0.type == .rive }.map { $0.id }
        setLoadedManifests(
            ManifestLoader.loadFromBundle(dashboardIds: webIds, folderPrefix: "web")
                .merging(ManifestLoader.loadFromBundle(dashboardIds: riveIds, folderPrefix: "rive", optional: true)) { web, _ in web }
        )
    }

    var body: some Scene {
        WindowGroup {
            GeometryReader { geometry in
                let landscape = geometry.size.width > geometry.size.height
                HStack(spacing: 0) {
                    mainTabs(landscape: landscape)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    if landscape && !dashboardVisible {
                        AppNavigationRail(selection: $selectedTab)
                    }
                }
            }
            .tint(.dashAccent)
            .environment(connectionVM)
            .environment(\.dashboardPreviewPresented, $dashboardPreviewPresented)
            .task { connectionVM.startupAutoConnect() }
            .task(id: connectionVM.connectionStatus == .connected) {
                var previousGear: Float?
                for await state in connectionVM.dashStateStream() {
                    let gear = state.carState.gear
                    guard gear != previousGear else { continue }
                    previousGear = gear
                    startDriveIfNeeded(gear: gear)
                }
            }
            .onChange(of: autoStartDrive) { _, _ in
                startDriveIfNeeded(gear: connectionVM.latestDashState?.carState.gear)
            }
            .onChange(of: dashboardPreviewPresented) { _, _ in
                startDriveIfNeeded(gear: connectionVM.latestDashState?.carState.gear)
            }
            .onChange(of: connectionVM.connectionStatus) { _, _ in
                startDriveIfNeeded(gear: connectionVM.latestDashState?.carState.gear)
            }
            .onChange(of: connectionVM.onboardingRequested) { _, _ in
                startDriveIfNeeded(gear: connectionVM.latestDashState?.carState.gear)
            }
            .fullScreenCover(isPresented: Binding(
                get: { connectionVM.onboardingRequested },
                set: { connectionVM.onboardingRequested = $0 }
            )) {
                OnboardingView(
                    onFinish: { completeOnboarding() },
                    onSkip: { completeOnboarding() }
                )
                .environment(connectionVM)
            }
            .onChange(of: scenePhase) { _, phase in
                guard phase == .active else { return }
                startDriveIfNeeded(gear: connectionVM.latestDashState?.carState.gear)
                if !sawFirstActivation {
                    sawFirstActivation = true
                    return
                }
                connectionVM.onAppForegrounded()
            }
        }
    }

    private func mainTabs(landscape: Bool) -> some View {
        TabView(selection: $selectedTab) {
            NavigationStack(path: $navigationPath) {
                HomeView(navigationPath: $navigationPath)
                    .navigationDestination(for: AppRoute.self) { destination($0) }
            }
            .toolbar(landscape || dashboardVisible ? .hidden : .visible, for: .tabBar)
            .toolbarBackground(Color.dashSurface, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
            .toolbarColorScheme(.dark, for: .tabBar)
            .tabItem { Label("Drive", systemImage: "car.fill") }
            .tag(0)

            NavigationStack {
                ControlsView(isRoot: true)
                    .navigationDestination(for: AppRoute.self) { destination($0) }
            }
            .toolbar(landscape || dashboardVisible ? .hidden : .visible, for: .tabBar)
            .toolbarBackground(Color.dashSurface, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
            .toolbarColorScheme(.dark, for: .tabBar)
            .tabItem { Label("Commands", systemImage: "slider.horizontal.3") }
            .tag(1)

            NavigationStack {
                AutomationsView(isRoot: true)
                    .navigationDestination(for: AppRoute.self) { destination($0) }
            }
            .toolbar(landscape || dashboardVisible ? .hidden : .visible, for: .tabBar)
            .toolbarBackground(Color.dashSurface, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
            .toolbarColorScheme(.dark, for: .tabBar)
            .tabItem { Label("Automations", systemImage: "sparkles") }
            .tag(2)

            NavigationStack {
                SettingsView(isRoot: true)
                    .navigationDestination(for: AppRoute.self) { destination($0) }
            }
            .toolbar(landscape || dashboardVisible ? .hidden : .visible, for: .tabBar)
            .toolbarBackground(Color.dashSurface, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
            .toolbarColorScheme(.dark, for: .tabBar)
            .tabItem { Label("Settings", systemImage: "gearshape.fill") }
            .tag(3)
        }
    }

    @ViewBuilder
    private func destination(_ route: AppRoute) -> some View {
        switch route {
        case .dashboardSelection:
            DashboardSelectionView()
        case .dashboard(let type, let url):
            DashboardView(dashboardType: type, dashboardUrl: url)
                .toolbar(.hidden, for: .tabBar)
                .onAppear { dashboardVisible = true }
                .onDisappear { dashboardVisible = false }
        case .settings:
            SettingsView()
        case .themePicker:
            ThemePickerView()
        case .battery:
            BatteryView()
        case .climate:
            ClimateView()
        case .automations:
            AutomationsView()
        case .controls:
            ControlsView()
        }
    }

    private func startDriveIfNeeded(gear: Float?) {
        guard autoStartGate.shouldStart(
            enabled: autoStartDrive,
            connected: connectionVM.connectionStatus == .connected,
            gear: gear,
            canNavigate: scenePhase == .active && !connectionVM.onboardingRequested,
            dashboardVisible: dashboardVisible,
            previewVisible: dashboardPreviewPresented
        ) else { return }
        let dashboard = selectedDashboard()
        navigationPath = NavigationPath([
            AppRoute.dashboard(type: dashboard.type.rawValue, url: dashboard.url)
        ])
        selectedTab = 0
    }

    /// Both Finish and Skip mark onboarding done so it never auto-triggers again.
    private func completeOnboarding() {
        onboardingCompleted = true
        connectionVM.onboardingRequested = false
    }
}

/// Uses the same tab selection in both orientations, preserving each navigation stack.
private struct AppNavigationRail: View {
    @Binding var selection: Int
    private let labels: [LocalizedStringKey] = ["Drive", "Commands", "Automations", "Settings"]
    private let icons = ["car.fill", "slider.horizontal.3", "sparkles", "gearshape.fill"]

    var body: some View {
        VStack(spacing: 8) {
            Spacer(minLength: 0)
            ForEach(labels.indices, id: \.self) { index in
                Button { selection = index } label: {
                    VStack(spacing: 4) {
                        Image(systemName: icons[index])
                            .font(.system(size: 20))
                            .frame(width: 56, height: 32)
                            .background {
                                if selection == index {
                                    Capsule().fill(Color.dashAccent.opacity(0.14))
                                }
                            }
                        Text(labels[index])
                            .font(.system(size: 11, weight: .medium))
                            .lineLimit(2)
                            .minimumScaleFactor(0.85)
                            .multilineTextAlignment(.center)
                    }
                    .foregroundColor(selection == index ? .dashAccent : .dashTextMuted)
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == index ? .isSelected : [])
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .frame(width: 108)
        .frame(maxHeight: .infinity)
        .background(Color.dashSurface.ignoresSafeArea(edges: [.top, .bottom, .trailing]))
    }
}
