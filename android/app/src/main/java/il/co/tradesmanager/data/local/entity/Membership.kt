package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One person's place in one company. See
 * [il.co.tradesmanager.core.access.Membership] for what it means.
 *
 * This table, not the account row, is what says which lenses somebody gets. A
 * person is one identity — one ID number, one photograph, one induction they
 * signed — and any number of these, because the same tradesperson is a site
 * manager for one firm and on the tools for another.
 *
 * Leaving is [leftAt], not a delete. Who was on a job in March is a question
 * somebody asks in September, usually with a solicitor in the room.
 */
@Entity(
    tableName = "memberships",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("companyId")],
)
data class MembershipEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    /** Null is the membership somebody has to themselves: their own tools. */
    val companyId: String?,
    val role: String,
    val joinedAt: Long,
    val leftAt: Long?,
)
