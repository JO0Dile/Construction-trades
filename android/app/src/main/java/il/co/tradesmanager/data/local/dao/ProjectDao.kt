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

/** Projection for [ProjectDao.observeTaskProgress]. */
data class ProjectProgress(val projectId: String, val total: Int, val done: Int) {
    val fraction: Double get() = if (total == 0) 0.0 else done.toDouble() / total
}

@Dao
interface ProjectDao {

    /**
     * The jobs of one company.
     *
     * Every job list is scoped this way. Somebody who works for two firms on
     * the same phone must not see one firm's work while signed in to the
     * other, and scoping the job scopes everything hanging off it — tasks,
     * materials, money, permits, snags all reach their company through here.
     *
     * A null companyId matches jobs that belong to no company: a sole trader's
     * own work. It is written out rather than left to `= :companyId`, because
     * in SQL nothing equals NULL, not even NULL.
     */
    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAt IS NULL AND ((:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId)
        ORDER BY dueDate IS NULL, dueDate, updatedAt DESC
        """,
    )
    fun observeProjects(companyId: String?): Flow<List<ProjectEntity>>

    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAt IS NULL AND status = :status AND ((:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId)
        """,
    )
    fun observeProjectsByStatus(status: String, companyId: String?): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: String): Flow<ProjectEntity?>

    /**
     * Jobs past their due date and not finished.
     *
     * "Not finished" is the point: a job delivered late is history, a job
     * still running late is a phone call somebody has to make today.
     */
    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAt IS NULL AND status != 'DONE'
          AND dueDate IS NOT NULL AND dueDate < :now
          AND ((:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId)
        ORDER BY dueDate
        """,
    )
    fun observeOverdue(now: Long, companyId: String?): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Upsert
    suspend fun upsertMaterials(materials: List<ProjectMaterialEntity>)

    @Query("SELECT * FROM project_materials WHERE projectId = :projectId ORDER BY sortOrder")
    fun observeMaterials(projectId: String): Flow<List<ProjectMaterialEntity>>

    /** A one-shot read, for booking a delivery against the job's own lines. */
    @Query("SELECT * FROM project_materials WHERE projectId = :projectId ORDER BY sortOrder")
    suspend fun materialsFor(projectId: String): List<ProjectMaterialEntity>

    @Delete
    suspend fun deleteMaterial(material: ProjectMaterialEntity)

    @Upsert
    suspend fun upsertTasks(tasks: List<ProjectTaskEntity>)

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId ORDER BY sortOrder")
    fun observeTasks(projectId: String): Flow<List<ProjectTaskEntity>>

    @Query("DELETE FROM project_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("UPDATE project_tasks SET isDone = :done, doneAt = :at, doneByName = :by WHERE id = :taskId")
    suspend fun setTaskDone(taskId: String, done: Boolean, at: Long?, by: String?)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM project_tasks WHERE projectId = :projectId")
    suspend fun nextTaskSortOrder(projectId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM project_materials WHERE projectId = :projectId")
    suspend fun nextMaterialSortOrder(projectId: String): Int

    @Query("SELECT COUNT(*) FROM project_tasks WHERE projectId = :projectId AND isDone = 1")
    fun observeDoneTaskCount(projectId: String): Flow<Int>

    /**
     * Done and total per project, in one query. The list screen shows a
     * progress bar per row, and a query per row would be one round trip per
     * project on every recomposition.
     */
    @Query(
        """
        SELECT projectId,
               COUNT(*) AS total,
               SUM(CASE WHEN isDone THEN 1 ELSE 0 END) AS done
        FROM project_tasks
        GROUP BY projectId
        """,
    )
    fun observeTaskProgress(): Flow<List<ProjectProgress>>

    @Upsert
    suspend fun upsertMilestones(milestones: List<MilestoneEntity>)

    @Query("SELECT * FROM milestones WHERE projectId = :projectId ORDER BY dueDate IS NULL, dueDate")
    fun observeMilestones(projectId: String): Flow<List<MilestoneEntity>>
}
