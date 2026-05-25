//
//  dashpilotApp.swift
//  dashpilot
//
//  Created by Pál Gábor on 2026. 03. 24..
//

import SwiftUI

@main
struct dashpilotApp: App {

    @State private var connectionVM = ConnectionViewModel()
    @State private var navigationPath = NavigationPath()

    init() {
        let webIds = availableDashboards.filter { $0.type == .web }.map { $0.id }
        setLoadedManifests(ManifestLoader.loadFromBundle(dashboardIds: webIds))
    }

    var body: some Scene {
        WindowGroup {
            NavigationStack(path: $navigationPath) {
                SetupView(navigationPath: $navigationPath)
                    .navigationDestination(for: AppRoute.self) { route in
                        switch route {
                        case .dashboardSelection:
                            DashboardSelectionView()
                        case .dashboard(let type, let url):
                            DashboardView(dashboardType: type, dashboardUrl: url)
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .environment(connectionVM)
        }
    }
}
