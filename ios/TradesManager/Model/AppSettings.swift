import Foundation
import SwiftUI

/// Device-local preferences. Nothing here is personal data, so it lives in
/// UserDefaults rather than in the encrypted store.
@Observable
final class AppSettings {

    enum ThemeMode: String, CaseIterable {
        case system, light, dark

        var labelKey: String {
            switch self {
            case .system: return "set_theme_system"
            case .light: return "set_theme_light"
            case .dark: return "set_theme_dark"
            }
        }

        var colorScheme: ColorScheme? {
            switch self {
            case .system: return nil
            case .light: return .light
            case .dark: return .dark
            }
        }
    }

    var onboardingComplete: Bool { didSet { store(onboardingComplete, "onboarding_complete") } }
    var themeMode: ThemeMode { didSet { store(themeMode.rawValue, "theme_mode") } }
    var largeText: Bool { didSet { store(largeText, "large_text") } }
    var actorName: String { didSet { store(actorName, "actor_name") } }
    var seededCatalogVersion: Int { didSet { store(seededCatalogVersion, "seeded_catalog_version") } }

    init() {
        let defaults = UserDefaults.standard
        onboardingComplete = defaults.bool(forKey: "onboarding_complete")
        themeMode = ThemeMode(rawValue: defaults.string(forKey: "theme_mode") ?? "") ?? .system
        largeText = defaults.bool(forKey: "large_text")
        actorName = defaults.string(forKey: "actor_name") ?? ""
        seededCatalogVersion = defaults.integer(forKey: "seeded_catalog_version")
    }

    /// Account and data deletion, which both stores require to be offered
    /// in-app. The SwiftData side is cleared by `DataStore.deleteEverything`.
    func reset() {
        onboardingComplete = false
        themeMode = .system
        largeText = false
        actorName = ""
        seededCatalogVersion = 0
    }

    private func store(_ value: Any, _ key: String) {
        UserDefaults.standard.set(value, forKey: key)
    }
}
