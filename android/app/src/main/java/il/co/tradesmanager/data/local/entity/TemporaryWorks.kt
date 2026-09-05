package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item of temporary works, from its design to the day it comes down.
 *
 * Flat rather than a parent with a row per event, because unlike a scaffold —
 * which is inspected over and over — each of these happens once. A thing is
 * designed once, checked once, erected once, loaded once and struck once, and a
 * child table would be five rows that can never have a sixth.
 *
 * [supportsPourId] is the link that makes the strike gate worth having. When
 * this is propping under a pour the app already recorded, the register knows
 * when the concrete went in, and can refuse to let anybody take the props out
 * before the days the engineer specified. That decision is otherwise made
 * verbally by whoever needs the props upstairs, and afterwards nobody can say
 * who released it.
 */
@Entity(
    tableName = "temporary_works",
    indices = [Index("projectId"), Index("supportsPourId")],
)
data class TemporaryWorksEntity(
    @PrimaryKey val id: String,
    /** Site form: TW-001. */
    val reference: String,
    val projectId: String,
    /** What it is: props to level 3 slab, façade retention to the north wall. */
    val description: String,
    /** PROPPING, FORMWORK, SHORING, FACADE_RETENTION, EDGE_PROTECTION, OTHER. */
    val kind: String,
    /** ZERO, ONE, TWO or THREE — see core.safety.TemporaryWorks. */
    val checkCategory: String,
    /** The drawing or calculation this was built from. */
    val designReference: String?,
    val designerName: String?,
    val checkerName: String?,
    val checkedAt: Long?,
    val erectedAt: Long?,
    /** When somebody compared what was built against what was drawn. */
    val inspectedAt: Long?,
    val inspectedByName: String?,
    val loadedAt: Long?,
    /**
     * The pour this is holding up, if any.
     *
     * Not a foreign key: propping often supports concrete poured before this
     * register existed, or by somebody else, and a constraint would mean
     * either inventing a pour row or leaving the link empty. The gate treats a
     * missing pour as "nothing curing", which is the safe reading.
     */
    val supportsPourId: String?,
    /** Copied from the pour when it is linked, typed in when it is not. */
    val supportsPourAt: Long?,
    /** The engineer's number, not the app's. Defaulted, never calculated. */
    val minimumStrikingDays: Long,
    /** Who said it may come down, and when they said it. */
    val releasedByName: String?,
    val releasedAt: Long?,
    val struckAt: Long?,
    val notes: String?,
    val createdByName: String,
    val createdAt: Long,
    val updatedAt: Long,
)
