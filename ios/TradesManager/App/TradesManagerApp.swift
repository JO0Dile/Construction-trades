import SwiftData
import SwiftUI

@main
struct TradesManagerApp: App {

    @State private var localization = Localization()
    @State private var settings = AppSettings()

    /// One store for the whole app. The models mirror the Room schema in the
    /// Android target field for field, so the two databases stay readable side
    /// by side — which is what a shared sync format will need.
    private let container: ModelContainer = {
        let schema = Schema([
            Trade.self, CatalogEntry.self, StockItem.self, StockMovement.self,
            Project.self, ProjectMaterial.self, ProjectTask.self,
            TaskBlock.self, TimeEntry.self,
            SafetyTemplate.self, SafetyTemplateCheck.self,
            ChecklistRun.self, ChecklistAnswer.self, AuditEntry.self,
        ])
        // Site records include personal data under the Israeli Privacy
        // Protection Law, so the file is protected at rest by the system,
        // which is the iOS counterpart of SQLCipher on Android.
        let configuration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: false,
            allowsSave: true
        )
        do {
            return try ModelContainer(for: schema, configurations: configuration)
        } catch {
            fatalError("Could not open the local database: \(error)")
        }
    }()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(localization)
                .environment(settings)
                .environment(\.locale, localization.locale)
                // Hebrew and Arabic mirror the whole interface. Driving it from
                // the chosen language rather than the device's means the in-app
                // switcher flips the layout too, with no per-view direction code.
                .environment(\.layoutDirection, localization.layoutDirection)
                .tradesTypography(largeText: settings.largeText)
                .preferredColorScheme(settings.themeMode.colorScheme)
        }
        .modelContainer(container)
    }
}
