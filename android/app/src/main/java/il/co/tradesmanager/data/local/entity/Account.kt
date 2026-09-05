package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A firm. Its members are the [AccountEntity] rows pointing at it.
 *
 * [registrationNumber] is the ח.פ. / ע.מ. that has to appear on an Israeli
 * invoice. It is optional because a one-person operation signing up on a
 * Tuesday morning should not be stopped at a field they would have to go and
 * look up.
 */
@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val registrationNumber: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Someone who signs in on this device.
 *
 * A null [companyId] is a personal account: one person, their own tools, no
 * roles to think about. They are stored as [il.co.tradesmanager.core.access.Role.OWNER]
 * so every permission check has exactly one shape, and a sole trader never
 * meets the permission model at all.
 *
 * The passcode is stored as a PBKDF2 hash and a per-account salt, never as
 * itself. Both are null when the account has no passcode, which is allowed and
 * common — see [il.co.tradesmanager.core.security.Passcode].
 */
@Entity(tableName = "accounts", indices = [Index("companyId")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val companyId: String?,
    val role: String,
    val passcodeHash: String?,
    val passcodeSalt: String?,
    val createdAt: Long,
    val lastSignInAt: Long?,
    /** Removed members are kept, because the audit trail names them. */
    val deletedAt: Long?,
    /**
     * What they type to sign in.
     *
     * Separate from [displayName] because the two answer different questions.
     * A display name is what a register should read; a username is what picks
     * one person out of five. Two people called Hammam on one job is an
     * ordinary Tuesday, and a list showing "hammam" twice is not a list
     * anybody can sign in from.
     *
     * Nullable, because accounts made before this existed have none. They keep
     * signing in by display name, which still works.
     */
    val username: String?,
    /**
     * The תעודת זהות or passport number the site office holds.
     *
     * Optional, because a sole trader setting the app up on a Tuesday morning
     * should not be stopped at a field, but the thing a manager actually
     * identifies a person by — names on a site repeat, and this one does not.
     * It is also accepted at the sign-in field for exactly that reason.
     */
    val idNumber: String?,
    /**
     * The induction this person signed, drawn with a finger. Encoded by
     * [il.co.tradesmanager.core.security.Signature].
     */
    val inductionSignature: String?,
    /** When they signed it. Null means they have not, and the app does not open. */
    val inductedAt: Long?,
)
