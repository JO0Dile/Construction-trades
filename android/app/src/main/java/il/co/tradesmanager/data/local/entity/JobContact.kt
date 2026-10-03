package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One person or firm from outside, on one job, and how to reach them. A
 * changed number replaces the old one, and the audit trail keeps that it
 * changed; somebody who left the job is a date, not a delete. The phone and
 * email are a private person's more often than not, and the privacy notice
 * says so. See core.work.Contacts.
 */
@Entity(
    tableName = "job_contacts",
    indices = [Index("projectId")],
)
data class JobContactEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val organisation: String? = null,
    /** A Contacts.Kind name. */
    val kind: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val addedAt: Long,
    val addedByName: String,
    val updatedAt: Long,
    val removedAt: Long? = null,
    val removedByName: String? = null,
)
