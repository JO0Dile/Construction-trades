import SwiftData
import SwiftUI

struct RootView: View {

    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context

    var body: some View {
        Group {
            if settings.onboardingComplete {
                MainTabs()
            } else {
                OnboardingView()
            }
        }
        .task { await loadCatalogueIfNeeded() }
    }

    /// Reference data is refreshed when the shipped catalogue version moves
    /// ahead of what this device has loaded. It only ever touches the read-only
    /// catalogue tables, so it is safe to run at launch while the user is
    /// already on a screen.
    private func loadCatalogueIfNeeded() async {
        let store = DataStore(context: context, settings: settings)
        guard let shipped = try? CatalogSource().manifest().catalogVersion,
              settings.seededCatalogVersion < shipped
        else { return }
        if let loaded = try? store.seedReferenceData() {
            settings.seededCatalogVersion = loaded
        }
    }
}

private struct MainTabs: View {

    @Environment(Localization.self) private var loc

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label(loc["nav_home"], systemImage: "square.grid.2x2") }
            InventoryListView()
                .tabItem { Label(loc["nav_inventory"], systemImage: "shippingbox") }
            ProjectsView()
                .tabItem { Label(loc["nav_projects"], systemImage: "hammer") }
            ScheduleView()
                .tabItem { Label(loc["nav_schedule"], systemImage: "calendar") }
            SafetyView()
                .tabItem { Label(loc["nav_safety"], systemImage: "shield.lefthalf.filled") }
        }
    }
}
