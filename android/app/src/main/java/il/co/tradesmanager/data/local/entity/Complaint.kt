package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One complaint about the site from outside it, and the answer to it.
 *
 * The answer is written once. [contact] is a private person's phone number
 * or address more often than not, so it is kept for answering them and left
 * out of every export. See core.evidence.Complaints for the rules.
 */
@Entity(
    tableName = "complaints",
    indices = [Index("projectId")],
)
data class ComplaintEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** CP-001, numbered per job. */
    val reference: String,
    /** Who complained: "the neighbour at 12 Herzl", "the municipality's inspector". */
    val fromWhom: String,
    /** How to reach them back, if they left a way. */
    val contact: String? = null,
    /** A Complaints.Channel name. */
    val channel: String,
    /** A Complaints.Subject name. */
    val subject: String,
    val description: String,
    val receivedAt: Long,
    val receivedByName: String,
    /** What was done about it, and what they were told. */
    val response: String? = null,
    val answeredAt: Long? = null,
    val answeredByName: String? = null,
)
