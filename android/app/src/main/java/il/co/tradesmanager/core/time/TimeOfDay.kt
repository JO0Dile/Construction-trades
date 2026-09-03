package il.co.tradesmanager.core.time

/**
 * Parsing and formatting of a time of day held as minutes past midnight.
 *
 * Kept out of the Compose layer so the awkward cases — a colon missed on a
 * gloved thumb, a dot typed instead, an hour of 25 — are covered by ordinary
 * unit tests rather than by a UI test on an emulator.
 */
object TimeOfDay {

    const val MINUTES_PER_DAY: Int = 24 * 60

    /**
     * Reads "HH:mm" as typed on site. Accepts a dot as the separator and a
     * bare hour. Returns [fallback] for anything out of range rather than
     * refusing the entry: losing the task note matters more than the time.
     */
    fun parse(text: String, fallback: Int): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return fallback
        val parts = trimmed.split(':', '.', limit = 2)
        val hour = parts[0].trim().toIntOrNull() ?: return fallback
        val minute = parts.getOrNull(1)?.trim()?.let { it.toIntOrNull() ?: return fallback } ?: 0
        if (hour !in 0..23 || minute !in 0..59) return fallback
        return hour * 60 + minute
    }

    /** Zero-padded 24-hour rendering, independent of locale digits. */
    fun format(minutes: Int): String {
        val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
        return "%02d:%02d".format(clamped / 60, clamped % 60)
    }

    /**
     * An end time that is at or before its start is a typo, not a plan. Nudge
     * it out to a readable minimum rather than storing a zero-height block.
     */
    fun sanitiseRange(start: Int, end: Int, minimumMinutes: Int = 15): Pair<Int, Int> {
        val safeStart = start.coerceIn(0, MINUTES_PER_DAY - minimumMinutes)
        val safeEnd = maxOf(end, safeStart + minimumMinutes).coerceAtMost(MINUTES_PER_DAY)
        return safeStart to safeEnd
    }
}
