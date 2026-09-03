import Foundation

/// Parsing and formatting of a time of day held as minutes past midnight —
/// the same rules as `core/time/TimeOfDay.kt`, whose behaviour is pinned by
/// unit tests on the Android side.
enum TimeOfDay {

    static let minutesPerDay = 24 * 60

    /// Reads "HH:mm" as typed on site. Accepts a dot separator and a bare hour.
    /// Anything out of range falls back rather than refusing the entry, because
    /// losing the task note matters more than the time.
    static func parse(_ text: String, fallback: Int) -> Int {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return fallback }

        // split drops empty subsequences, so ":" and "::" yield no parts at
        // all — indexing [0] there would crash rather than fall back.
        let parts = trimmed.split(whereSeparator: { $0 == ":" || $0 == "." }).map(String.init)
        guard let first = parts.first,
              let hour = Int(first.trimmingCharacters(in: .whitespaces))
        else { return fallback }

        var minute = 0
        if parts.count > 1 {
            guard let parsed = Int(parts[1].trimmingCharacters(in: .whitespaces)) else { return fallback }
            minute = parsed
        }
        guard (0...23).contains(hour), (0...59).contains(minute) else { return fallback }
        return hour * 60 + minute
    }

    static func format(_ minutes: Int) -> String {
        let clamped = min(max(minutes, 0), minutesPerDay - 1)
        return String(format: "%02d:%02d", clamped / 60, clamped % 60)
    }

    /// An end at or before its start is a typo, not a plan.
    static func sanitiseRange(start: Int, end: Int, minimum: Int = 15) -> (start: Int, end: Int) {
        let safeStart = min(max(start, 0), minutesPerDay - minimum)
        let safeEnd = min(max(end, safeStart + minimum), minutesPerDay)
        return (safeStart, safeEnd)
    }

    /// Epoch day, matching Java's `LocalDate.toEpochDay()` so the two platforms
    /// key a day identically.
    static func epochDay(from date: Date, calendar: Calendar = .current) -> Int {
        let start = calendar.startOfDay(for: date)
        return Int(floor(start.timeIntervalSince1970 / 86_400))
    }

    static func date(fromEpochDay day: Int) -> Date {
        Date(timeIntervalSince1970: TimeInterval(day) * 86_400)
    }
}
