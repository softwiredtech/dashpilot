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
            TabView(selection: $selectedTab) {
                NavigationStack(path: $navigationPath) {
                    HomeView(navigationPath: $navigationPath)
                        .navigationDestination(for: AppRoute.self) { destination($0) }
                }
                .toolbarBackground(Color.dashSurface, for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)
                .toolbarColorScheme(.dark, for: .tabBar)
                .tabItem { Label("Drive", systemImage: "car.fill") }
                .tag(0)

                NavigationStack {
                    ControlsView(isRoot: true)
                        .navigationDestination(for: AppRoute.self) { destination($0) }
                }
                .toolbarBackground(Color.dashSurface, for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)
                .toolbarColorScheme(.dark, for: .tabBar)
                .tabItem { Label("Commands", systemImage: "slider.horizontal.3") }
                .tag(1)

                NavigationStack {
                    AutomationsView(isRoot: true)
                        .navigationDestination(for: AppRoute.self) { destination($0) }
                }
                .toolbarBackground(Color.dashSurface, for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)
                .toolbarColorScheme(.dark, for: .tabBar)
                .tabItem { Label("Automations", systemImage: "sparkles") }
                .tag(2)

                NavigationStack {
                    SettingsView(isRoot: true)
                        .navigationDestination(for: AppRoute.self) { destination($0) }
                }
                .toolbarBackground(Color.dashSurface, for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)
                .toolbarColorScheme(.dark, for: .tabBar)
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(3)
            }
            .tint(.dashAccent)
            .environment(connectionVM)
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
        case .setup:
            SetupView(navigationPath: $navigationPath)
        }
    }

    private func startDriveIfNeeded(gear: Float?) {
        guard autoStartGate.shouldStart(
            enabled: autoStartDrive,
            connected: connectionVM.connectionStatus == .connected,
            gear: gear,
            canNavigate: scenePhase == .active && !connectionVM.onboardingRequested,
            dashboardVisible: dashboardVisible
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
