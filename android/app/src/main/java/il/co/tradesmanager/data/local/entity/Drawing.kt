package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One revision of one drawing, as it arrived on the job.
 *
 * A new revision is a new row, and the one it replaces gets [supersededAt].
 * Nothing is overwritten: which revision somebody was building from on a
 * given day is exactly what gets asked when a wall turns out to be in the
 * wrong place. The sheet itself, photographed or picked from the files the
 * architect sent, is in the photos table under `Owner.DRAWING`.
 *
 * See core.evidence.Drawings for the rules.
 */
@Entity(
    tableName = "drawings",
    indices = [Index("projectId")],
)
data class DrawingEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** As written on the title block: A-101, S-02, E-3. */
    val number: String,
    val title: String,
    val revision: String,
    val receivedAt: Long,
    /** When a newer revision arrived. Null while this is the one to build from. */
    val supersededAt: Long? = null,
    val notes: String? = null,
    val recordedByName: String,
)
