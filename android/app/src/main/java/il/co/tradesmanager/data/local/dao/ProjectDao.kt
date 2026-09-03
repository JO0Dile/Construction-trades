package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.MilestoneEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY dueDate IS NULL, dueDate, updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND status = :status")
    fun observeProjectsByStatus(status: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: String): Flow<ProjectEntity?>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Upsert
    suspend fun upsertMaterials(materials: List<ProjectMaterialEntity>)

    @Query("SELECT * FROM project_materials WHERE projectId = :projectId ORDER BY sortOrder")
    fun observeMaterials(projectId: String): Flow<List<ProjectMaterialEntity>>

    @Delete
    suspend fun deleteMaterial(material: ProjectMaterialEntity)

    @Upsert
    suspend fun upsertTasks(tasks: List<ProjectTaskEntity>)

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId ORDER BY sortOrder")
    fun observeTasks(projectId: String): Flow<List<ProjectTaskEntity>>

    @Query("UPDATE project_tasks SET isDone = :done, doneAt = :at, doneByName = :by WHERE id = :taskId")
    suspend fun setTaskDone(taskId: String, done: Boolean, at: Long?, by: String?)

    @Query("SELECT COUNT(*) FROM project_tasks WHERE projectId = :projectId AND isDone = 1")
    fun observeDoneTaskCount(projectId: String): Flow<Int>

    @Upsert
    suspend fun upsertMilestones(milestones: List<MilestoneEntity>)

    @Query("SELECT * FROM milestones WHERE projectId = :projectId ORDER BY dueDate IS NULL, dueDate")
    fun observeMilestones(projectId: String): Flow<List<MilestoneEntity>>
}
