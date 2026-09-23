package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A roll call after the site was cleared.
 *
 * Kept whether or not everybody was found. A roll call that ended with a name
 * unaccounted for is the single most important record this app holds, and a
 * table that could be emptied of those is worse than no table.
 *
 * Nothing deletes these and nothing edits one once [endedAt] is set. See
 * core.safety.Muster for the rules.
 */
@Entity(
    tableName = "musters",
    // Only what a query uses. Every read of this table is ordered by
    // startedAt; nothing filters by job, and an index nobody queries is a
    // write slowed down for nothing.
    indices = [Index("startedAt")],
)
data class MusterEntity(
    @PrimaryKey val id: String,
    /**
     * The site that was cleared.
     *
     * Worked out from the open check-ins rather than chosen -- see
     * core.safety.Muster.siteOf -- and null when they do not agree on one.
     */
    val projectId: String? = null,
    /** The firm, or null for somebody working alone. Nullable like every other. */
    val companyId: String? = null,
    val startedAt: Long,
    /** Null while it is running. Once set, the row is a record. */
    val endedAt: Long? = null,
    /** DRILL or EMERGENCY — see core.safety.Muster.Reason. */
    val reason: String,
    val startedByAccountId: String,
    /** As it read at the time, for the same reason a violation copies the name. */
    val startedByName: String,
    /**
     * What it was: the alarm point, the drill scenario, what the fire brigade
     * were told. Free text, because the sentence somebody writes at the gate is
     * not something a controlled list can hold.
     */
    val note: String? = null,
    /**
     * How many names were still unaccounted for when it ended.
     *
     * Written once at the end rather than counted from the rows each time.
     * The rows are the truth and this agrees with them; it exists so that a
     * list of past roll calls can show the number that matters without
     * reading every person on every one of them.
     */
    val unaccountedAtEnd: Int? = null,
)

/**
 * One name on one roll call.
 *
 * The name is copied on rather than read back through [personId], like every
 * other record in this app that names somebody. A roll call from two years ago
 * has to still say who was on it after the account is renamed or gone.
 */
@Entity(
    tableName = "muster_people",
    indices = [Index("musterId")],
)
data class MusterPersonEntity(
    @PrimaryKey val id: String,
    val musterId: String,
    /** The account that checked in, or null for somebody added at the gate. */
    val personId: String? = null,
    val name: String,
    /** UNACCOUNTED, PRESENT or ACCOUNTED_ELSEWHERE — see core.safety.Muster.State. */
    val state: String,
    /**
     * Their check-in had been open longer than a shift when the roll call
     * started, so the name may be a ghost. Recorded rather than acted on: the
     * flag is shown to whoever is reading the list, and they decide.
     */
    val staleCheckIn: Boolean = false,
    /** Not in the check-in data at all — a visitor, a driver, a subcontractor. */
    val addedDuringRollCall: Boolean = false,
    /**
     * How they were accounted for, when it was not by being looked at.
     *
     * Null for PRESENT, because somebody standing in front of you needs no
     * explanation, and required for ACCOUNTED_ELSEWHERE, because otherwise the
     * quick way to finish a roll call is to tick everybody off without ringing
     * anyone. Enforced in core.safety.Muster and again in the repository.
     */
    val account: String? = null,
    val settledAt: Long? = null,
)
