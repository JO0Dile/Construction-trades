package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A safety violation written against a named person.
 *
 * The name and ID number are copied onto the row rather than read back
 * through [againstAccountId]. A violation is an accusation with a cost on it,
 * and what it said about who it named has to stay what it said — the same
 * reason a payment application copies the packages it covers and an audit
 * entry hashes what it holds. Somebody changing their display name must not
 * quietly rewrite a record already stood behind.
 *
 * Evidence lives in the photos table under `Owner.VIOLATION`, keyed on this
 * row's id, and may be a still or a video.
 *
 * Nothing deletes these. A cancelled draft is kept and marked, because a
 * pattern of violations raised and dropped against the same person is itself
 * worth being able to see.
 */
@Entity(
    tableName = "violations",
    indices = [
        Index("companyId"),
        Index("againstAccountId"),
        Index("status"),
    ],
)
data class ViolationEntity(
    @PrimaryKey val id: String,
    /**
     * The firm whose site it happened on, or null for somebody working alone.
     *
     * Nullable like every other companyId in the schema. This one was not,
     * and that single word made the whole feature dead for a personal
     * account: a self-employed tradesman has a membership with no company on
     * it, so the screen offered a button that could never write a row.
     */
    val companyId: String? = null,
    /** The job, when it was on one. Not every violation is. */
    val projectId: String? = null,
    val againstAccountId: String,
    /** As it read when the violation was written. */
    val againstName: String,
    /** As it read when the violation was written. */
    val againstIdNumber: String,
    val description: String,
    /**
     * What it is being charged at, or null for no charge.
     *
     * Null and zero are different: zero says this cost nothing, null says
     * nobody put a charge on it. See core.safety.Violations.
     */
    val costAmount: Double? = null,
    /** DRAFT, CONFIRMED or CANCELLED — see core.safety.Violations.Status. */
    val status: String,
    val recordedByAccountId: String,
    val recordedByName: String,
    val recordedAt: Long,
    val confirmedAt: Long? = null,
    val cancelledAt: Long? = null,
)
