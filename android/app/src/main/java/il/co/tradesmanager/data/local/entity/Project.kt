package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A job. [parentProjectId] gives sub-projects and zones — a building holds
 * floors, a floor holds rooms — without a second table.
 */
@Entity(tableName = "projects", indices = [Index("status"), Index("parentProjectId")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Free label: House, Lobby, Floor, Room, or whatever the site calls it. */
    val kindLabel: String,
    val parentProjectId: String? = null,
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val status: String,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val notes: String? = null,
    val templateId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A material or tool the job needs, and how much of it is already allocated. */
@Entity(
    tableName = "project_materials",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("inventoryItemId")],
)
data class ProjectMaterialEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val inventoryItemId: String?,
    val catalogItemId: String?,
    val label: String,
    val unit: String,
    val requiredQuantity: Double,
    val allocatedQuantity: Double = 0.0,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "project_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class ProjectTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val sortOrder: Int,
    val isDone: Boolean = false,
    val doneAt: Long? = null,
    val doneByName: String? = null,
)

@Entity(
    tableName = "milestones",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val dueDate: Long?,
    val completedAt: Long? = null,
)
