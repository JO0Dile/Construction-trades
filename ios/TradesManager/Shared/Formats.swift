import Foundation

/// The Israeli conventions the product fixes: metric units, the shekel,
/// DD/MM/YYYY dates and a 24-hour clock — the same rules as the Kotlin
/// `Formats` object, so a report exported from an iPhone and one exported from
/// an Android phone read identically.
public enum Formats {

    public static let currencyCode = "ILS"

    private static let datePattern = "dd/MM/yyyy"
    private static let timePattern = "HH:mm"

    public static func date(_ date: Date, locale: Locale) -> String {
        formatter(pattern: datePattern, locale: locale).string(from: date)
    }

    public static func time(_ date: Date, locale: Locale) -> String {
        formatter(pattern: timePattern, locale: locale).string(from: date)
    }

    public static func money(_ amount: Double, locale: Locale) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .currency
        formatter.currencyCode = currencyCode
        formatter.maximumFractionDigits = 2
        return formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
    }

    /// Whole numbers print without a decimal tail: "12 sockets" reads better on
    /// a phone in the sun than "12.00 sockets".
    public static func quantity(_ value: Double, locale: Locale) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = value.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 2
        return formatter.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    public static func percent(_ fraction: Double, locale: Locale) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .percent
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: min(max(fraction, 0), 1))) ?? ""
    }

    /// A fixed pattern must not pick up the calendar or numbering system of the
    /// device: `dd/MM/yyyy` in a Hebrew locale still means the Gregorian date.
    private static func formatter(pattern: String, locale: Locale) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = pattern
        return formatter
    }
}
