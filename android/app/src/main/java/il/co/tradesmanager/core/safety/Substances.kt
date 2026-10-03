package il.co.tradesmanager.core.safety

import java.time.LocalDate

/**
 * What is kept on the site that can hurt somebody: the diesel for the
 * generator, the gas bottles for the roofer's torch, the form oil, the
 * epoxy and its hardener, the acid for the brick, the solvent in the
 * paint store, the cement itself.
 *
 * When somebody is splashed, or the store catches fire, the first two
 * questions are what it was and where the rest of it is; the third is what
 * the maker's safety data sheet says to do. So each substance gets a number,
 * the hazards its label shows, where it is kept and how much, the
 * precautions and the first aid, and the date of the data sheet on file.
 * One with no sheet on file is shown first. One taken off the site is marked
 * off, not deleted, because what was on the site last month is what somebody
 * asks about when a worker's cough does not go away.
 *
 * Which substances need a permit, and in what quantities, is the
 * regulations' to say and not this register's.
 */
object Substances {

    /** The nine pictograms a label can carry. A substance usually shows more than one. */
    enum class Hazard {
        EXPLOSIVE,
        FLAMMABLE,
        OXIDISING,
        GAS_UNDER_PRESSURE,
        CORROSIVE,
        TOXIC,
        HEALTH_HAZARD,
        HARMFUL,
        ENVIRONMENT,
    }

    enum class Refusal {
        BLANK_NAME,
        NO_HAZARD,
        BLANK_KEPT_WHERE,
        SHEET_IN_FUTURE,
        SHEET_NOT_NEWER,
        ALREADY_REMOVED,
    }

    enum class State {
        /** On the site with no safety data sheet on file. */
        NO_SHEET,

        /** On the site with a sheet older than [SHEET_OLD_YEARS]: worth asking the supplier for the current one. */
        SHEET_OLD,
        ON_SITE,

        /** Taken off the site; kept on the record. */
        REMOVED,
    }

    /**
     * Five years: the review period some regulators set for a data sheet, and
     * the usual rule of thumb where none is set. The screen says to ask for
     * the current one, not that the old one has stopped counting.
     */
    const val SHEET_OLD_YEARS = 5L

    /** A register entry is on it because it is hazardous, so it says how. */
    fun addRefusal(name: String, hazards: Set<Hazard>, keptWhere: String, sheetOn: LocalDate?, today: LocalDate): Refusal? = when {
        name.isBlank() -> Refusal.BLANK_NAME
        hazards.isEmpty() -> Refusal.NO_HAZARD
        keptWhere.isBlank() -> Refusal.BLANK_KEPT_WHERE
        sheetOn != null && sheetOn.isAfter(today) -> Refusal.SHEET_IN_FUTURE
        else -> null
    }

    /** A newer sheet replaces the date on file; an older or the same one is not newer. */
    fun sheetRefusal(sheetOn: LocalDate, current: LocalDate?, removed: Boolean, today: LocalDate): Refusal? = when {
        removed -> Refusal.ALREADY_REMOVED
        sheetOn.isAfter(today) -> Refusal.SHEET_IN_FUTURE
        current != null && !sheetOn.isAfter(current) -> Refusal.SHEET_NOT_NEWER
        else -> null
    }

    fun removeRefusal(removed: Boolean): Refusal? = if (removed) Refusal.ALREADY_REMOVED else null

    fun state(sheetOn: LocalDate?, removed: Boolean, today: LocalDate): State = when {
        removed -> State.REMOVED
        sheetOn == null -> State.NO_SHEET
        sheetOn.plusYears(SHEET_OLD_YEARS).isBefore(today) -> State.SHEET_OLD
        else -> State.ON_SITE
    }

    /** No sheet first, then an old one, then the rest on site, then what has gone; within each, by name. */
    fun order(rows: List<Pair<State, String>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { rows[it].second.lowercase() }))

    /** "HS-001": hazardous substances, numbered per job. */
    fun reference(countOnJob: Int): String = "HS-" + (countOnJob + 1).toString().padStart(3, '0')

    /** Stored as the names joined by commas, in the enum's order, so the same set is always the same string. */
    fun encode(hazards: Set<Hazard>): String = hazards.sortedBy { it.ordinal }.joinToString(",") { it.name }

    /** A name this version does not know is dropped rather than guessed at. */
    fun decode(stored: String): Set<Hazard> =
        stored.split(',').mapNotNull { name -> Hazard.entries.firstOrNull { it.name == name.trim() } }.toSet()
}
