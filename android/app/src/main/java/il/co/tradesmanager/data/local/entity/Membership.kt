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
    /**
     * Who stood on the gate and admitted them, when anybody did.
     *
     * Null on every membership that was not created at a gate — a firm's first
     * owner, somebody HR put on the books from an office, and every row
     * written before the gate existed. Null is therefore "no induction was
     * taken here", which is a true thing to record and not a gap to backfill.
     */
    val admittedByAccountId: String? = null,
    /**
     * The gate keeper's name as it was that day.
     *
     * Snapshotted, like the name on a violation. The person who signed a
     * worker in may have left the firm by the time anybody reads the record,
     * and a register that then shows a blank is worse than useless.
     */
    val admittedByName: String? = null,
    /**
     * What the person drew with their finger at the gate, encoded by
     * [il.co.tradesmanager.core.security.Signature].
     *
     * Held per membership rather than on the account, because the account has
     * one induction signature and a person joins as many firms as they like.
     * Signing for one site's rules is not signing for another's, and one
     * signature standing for all of them is the record failing at the exact
     * moment somebody needs it.
     */
    val admissionSignature: String? = null,
    /**
     * The membership this one answers to, or null for somebody at the top.
     *
     * The chain of command, one link at a time. It decides who may be shown
     * what a person is paid -- see [il.co.tradesmanager.core.access.Chain] --
     * so it is a permission, not an org chart drawn for tidiness.
     *
     * Another membership rather than an account, because the same person is a
     * foreman for one firm and on the tools for another, and being somebody's
     * boss on one job says nothing about the next.
     *
     * Null is the normal starting state and not a gap. The gate admits people
     * and deliberately does not rank them; rank is set afterwards by whoever
     * engaged them. Until then they answer to whoever owns the firm, which is
     * true whether or not anybody has drawn the chart.
     */
    val reportsToMembershipId: String? = null,
)
