package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One time a checklist was actually worked through, and who signed it. */
@Entity(tableName = "checklist_runs", indices = [Index("templateId"), Index("projectId")])
data class ChecklistRunEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val projectId: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    val signedByName: String? = null,
    val signedById: String? = null,
    /** Serialised signature strokes, drawn on the device at sign-off. */
    val signatureStrokes: String? = null,
    /** True while any critical check is unanswered or failed. */
    val blocked: Boolean = true,
)

@Entity(
    tableName = "checklist_run_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class ChecklistRunItemEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val templateItemId: String,
    /** PASS, FAIL or NOT_APPLICABLE; unanswered rows simply have no row state. */
    val state: String,
    val note: String? = null,
    val photoId: String? = null,
    val answeredAt: Long? = null,
)

@Entity(tableName = "incidents", indices = [Index("projectId"), Index("occurredAt")])
data class IncidentEntity(
    @PrimaryKey val id: String,
    val projectId: String? = null,
    val severity: String,
    val description: String,
    val occurredAt: Long,
    val reportedByName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val signatureStrokes: String? = null,
    val createdAt: Long,
)
