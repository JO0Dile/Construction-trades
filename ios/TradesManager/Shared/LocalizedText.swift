import Foundation

/// Text carried in every language it has been written in, keyed by language code.
///
/// The Kotlin side stores exactly this shape (see `LocalizedText.kt`), and both
/// read the same JSON, so a change to the resolution rule has to be made in two
/// places on purpose rather than in one by accident. The rules are:
/// exact match, then the base language of a regional tag, then the source
/// language, then any translation present.
public typealias LocalizedText = [String: String]

public enum Localised {

    /// The language the catalogues are authored in.
    public static let sourceLanguage = "en"

    public static func resolve(_ text: LocalizedText, languageCode: String) -> String {
        if let exact = text[languageCode], !exact.isEmpty { return exact }

        let base = String(languageCode.prefix(while: { $0 != "-" }))
        if let baseMatch = text[base], !baseMatch.isEmpty { return baseMatch }

        if let source = text[sourceLanguage], !source.isEmpty { return source }

        return text.values.first(where: { !$0.isEmpty }) ?? ""
    }

    /// Every translation joined and lowercased — the search index, so a search
    /// typed in Hebrew still finds an item the user remembers in English.
    public static func searchable(_ text: LocalizedText) -> String {
        text.values.map { $0.lowercased() }.joined(separator: " ")
    }
}

public extension Dictionary where Key == String, Value == String {
    /// `item.names.resolved(in: "he")`
    func resolved(in languageCode: String) -> String {
        Localised.resolve(self, languageCode: languageCode)
    }
}
