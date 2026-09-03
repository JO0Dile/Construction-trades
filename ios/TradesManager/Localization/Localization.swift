import Foundation
import SwiftUI

/// A language the app ships translations for.
struct AppLanguage: Identifiable, Hashable {
    let code: String
    /// The language's name in itself — "עברית", not "Hebrew". A Hebrew speaker
    /// looking for Hebrew is not helped by the English word for it.
    let endonym: String
    let isRightToLeft: Bool

    var id: String { code }
}

/// In-app language switching, instantly, without a restart.
///
/// iOS has no equivalent of `AppCompatDelegate.setApplicationLocales`, and the
/// usual workaround — writing `AppleLanguages` and asking the user to relaunch
/// — is unacceptable on a building site. So string lookup goes through the
/// `.lproj` bundle for the chosen language instead of through the main bundle,
/// and the whole view tree is re-rendered by `@Observable` when it changes.
/// `locale` and `layoutDirection` are pushed into the environment alongside it
/// so dates, numbers and mirroring follow the same choice.
///
/// Nothing here names Hebrew, Arabic or English: the list comes from whichever
/// `.lproj` folders are in the bundle, exactly as the Android picker reads
/// `locales_config.xml`.
@Observable
final class Localization {

    private static let storageKey = "app_language_code"
    static let sourceLanguage = "en"

    private(set) var languageCode: String
    private var bundle: Bundle

    init() {
        let stored = UserDefaults.standard.string(forKey: Self.storageKey)
        let resolved = Self.resolveInitial(stored: stored)
        self.languageCode = resolved
        self.bundle = Self.bundle(for: resolved)
    }

    /// The languages this build ships, newest translation included, with no
    /// list to maintain in code.
    var supported: [AppLanguage] {
        Bundle.main.localizations
            .filter { $0 != "Base" }
            .sorted()
            .map { code in
                let locale = Locale(identifier: code)
                let name = locale.localizedString(forLanguageCode: code) ?? code
                return AppLanguage(
                    code: code,
                    endonym: name.prefix(1).uppercased() + name.dropFirst(),
                    isRightToLeft: Self.isRightToLeft(code)
                )
            }
    }

    var locale: Locale { Locale(identifier: languageCode) }

    var layoutDirection: LayoutDirection {
        Self.isRightToLeft(languageCode) ? .rightToLeft : .leftToRight
    }

    func select(_ code: String) {
        guard code != languageCode else { return }
        languageCode = code
        bundle = Self.bundle(for: code)
        UserDefaults.standard.set(code, forKey: Self.storageKey)
    }

    /// `loc["inv_title"]` — a plain String, so `Text` renders it verbatim
    /// rather than trying its own lookup in the main bundle.
    subscript(key: String) -> String {
        bundle.localizedString(forKey: key, value: key, table: nil)
    }

    /// Plural-aware lookup, driven by the generated `Localizable.stringsdict`,
    /// so Hebrew's dual and Arabic's six categories are handled by the platform
    /// rather than by an `if count == 2` somewhere in a view.
    func plural(_ key: String, _ count: Int) -> String {
        String(format: bundle.localizedString(forKey: key, value: key, table: nil), count)
    }

    /// Catalogue text is data, not resources: resolve it in the chosen language
    /// with the same fallback chain the Kotlin side uses.
    func resolve(_ text: LocalizedText) -> String {
        Localised.resolve(text, languageCode: languageCode)
    }

    private static func isRightToLeft(_ code: String) -> Bool {
        Locale.Language(identifier: code).characterDirection == .rightToLeft
    }

    private static func bundle(for code: String) -> Bundle {
        guard let path = Bundle.main.path(forResource: code, ofType: "lproj"),
              let bundle = Bundle(path: path)
        else {
            return .main
        }
        return bundle
    }

    /// A first launch follows the device's languages, picking the first the app
    /// actually ships. Only an explicit in-app choice is stored.
    private static func resolveInitial(stored: String?) -> String {
        let available = Set(Bundle.main.localizations)
        if let stored, available.contains(stored) { return stored }
        for preferred in Locale.preferredLanguages {
            let base = String(preferred.prefix(while: { $0 != "-" }))
            if available.contains(preferred) { return preferred }
            if available.contains(base) { return base }
        }
        return sourceLanguage
    }
}
