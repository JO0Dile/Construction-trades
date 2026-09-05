package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A ticket somebody holds: work at height, site manager, forklift, welding.
 *
 * Hangs off an account rather than a separate worker record, because a person
 * on a job is a member of the company — that is the whole reason accounts came
 * before this. Two tables for "a person" is exactly how an app becomes 350
 * apps taped together.
 *
 * [expiresOn] is nullable: a trade licence may not expire, and inventing a
 * date for one would produce a warning nobody can ever clear.
 */
@Entity(tableName = "certifications", indices = [Index("accountId")])
data class CertificationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val title: String,
    /** The number on the card, for an inspector who asks to see it. */
    val reference: String?,
    val issuedOn: Long?,
    val expiresOn: Long?,
    val notes: String?,
    val createdAt: Long,
)
