package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One piece of protective equipment handed to one person, and signed for.
 *
 * Belongs to a firm rather than a job: the helmet goes with the man from one
 * site to the next, and the question an inspector asks is about the man.
 *
 * [accountId] is null for somebody the app has no account for — a day
 * labourer a subcontractor brought — who is recorded by [holderName] alone.
 * [inventoryItemId] is null for something that never came off this phone's
 * stock list. [itemName] is kept either way, as it read when it was handed
 * over, so the record still says what it was after the stock row has gone.
 *
 * See core.safety.Ppe for the rules.
 */
@Entity(
    tableName = "ppe_issues",
    // Read by firm for the register, by person for a profile.
    indices = [Index("companyId"), Index("accountId")],
)
data class PpeIssueEntity(
    @PrimaryKey val id: String,
    val companyId: String?,
    val accountId: String?,
    val holderName: String,
    val inventoryItemId: String?,
    val itemName: String,
    val quantity: Int,
    /** "L", "43", "10" — whatever the thing is sized in. */
    val size: String? = null,
    val issuedAt: Long,
    val replaceBy: Long? = null,
    /** The holder's own signature: see core.security.Signature. */
    val signature: String,
    val issuedByAccountId: String?,
    val issuedByName: String,
    val handedBackAt: Long? = null,
)
