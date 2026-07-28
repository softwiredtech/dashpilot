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

    @AppStorage(ConnectionViewModel.onboardingCompletedKey)
    private var onboardingCompleted = false

    /// The first `.active` scene phase is the cold launch, which is handled by
    /// `startupAutoConnect()`; only later activations are "foregrounded".
    @State private var sawFirstActivation = false

    init() {
        let webIds = availableDashboards.filter { $0.type == .web }.map { $0.id }
        setLoadedManifests(ManifestLoader.loadFromBundle(dashboardIds: webIds))
    }

    var body: some Scene {
        WindowGroup {
            NavigationStack(path: $navigationPath) {
                HomeView(navigationPath: $navigationPath)
                    .navigationDestination(for: AppRoute.self) { route in
                        switch route {
                        case .dashboardSelection:
                            DashboardSelectionView()
                        case .dashboard(let type, let url):
                            DashboardView(dashboardType: type, dashboardUrl: url)
                        case .settings:
                            SettingsView()
                        case .themePicker:
                            ThemePickerView()
                        case .automations:
                            AutomationsView()
                        case .controls:
                            ControlsView()
                        case .setup:
                            SetupView(navigationPath: $navigationPath)
                        }
                    }
            }
            .environment(connectionVM)
            .task { connectionVM.startupAutoConnect() }
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
                if !sawFirstActivation {
                    sawFirstActivation = true
                    return
                }
                connectionVM.onAppForegrounded()
            }
        }
    }

    /// Both Finish and Skip mark onboarding done so it never auto-triggers again.
    private func completeOnboarding() {
        onboardingCompleted = true
        connectionVM.onboardingRequested = false
    }
}
