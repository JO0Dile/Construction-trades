package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.catalog.CatalogItemFile
import il.co.tradesmanager.data.catalog.CatalogSource
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.catalog.ProjectTemplateDto
import il.co.tradesmanager.data.catalog.TemplateFile
import il.co.tradesmanager.data.local.dao.ProjectDao
import il.co.tradesmanager.data.local.dao.ProjectProgress
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectRepository(
    private val dao: ProjectDao,
    private val source: CatalogSource,
    private val audit: AuditTrail,
) {

    object Status {
        const val PLANNED = "PLANNED"
        const val ACTIVE = "ACTIVE"
        const val ON_HOLD = "ON_HOLD"
        const val DONE = "DONE"
    }

    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()
    fun observeActive(): Flow<List<ProjectEntity>> = dao.observeProjectsByStatus(Status.ACTIVE)
    fun observeProject(id: String): Flow<ProjectEntity?> = dao.observeProject(id)
    fun observeMaterials(id: String): Flow<List<ProjectMaterialEntity>> = dao.observeMaterials(id)
    fun observeTasks(id: String): Flow<List<ProjectTaskEntity>> = dao.observeTasks(id)
    fun observeProgress(): Flow<List<ProjectProgress>> = dao.observeTaskProgress()

    suspend fun save(project: ProjectEntity, actorName: String): ProjectEntity {
        val now = System.currentTimeMillis()
        val stored = project.copy(updatedAt = now, createdAt = project.createdAt.takeIf { it > 0 } ?: now)
        dao.upsert(stored)
        audit.record(ENTITY, stored.id, AuditTrail.Action.UPDATE, actorName, stored.name)
        return stored
    }

    suspend fun setTaskDone(taskId: String, done: Boolean, actorName: String) {
        dao.setTaskDone(taskId, done, if (done) System.currentTimeMillis() else null, actorName.ifBlank { null })
        audit.record("project_task", taskId, AuditTrail.Action.UPDATE, actorName, if (done) "done" else "reopened")
    }

    /**
     * The kinds of place a job can be — house, lobby, stairwell, roof. These
     * are suggestions read from the catalogue, not a fixed set: [createBlank]
     * takes whatever label it is handed, so a site that calls a place
     * something else can just type it.
     */
    suspend fun projectKinds(): List<ProjectKind> = withContext(Dispatchers.IO) {
        source.manifest().projectKinds
    }

    /**
     * A job with nothing in it.
     *
     * Templates only cover work the app already knows about, and most jobs are
     * not that. An empty project is the honest starting point: a name, what
     * sort of place it is, and the user adds the materials and tasks the job
     * actually needs.
     */
    suspend fun createBlank(
        name: String,
        kindLabel: String,
        actorName: String,
    ): ProjectEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            kindLabel = kindLabel.trim(),
            status = Status.PLANNED,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(project)
        audit.record(ENTITY, project.id, AuditTrail.Action.CREATE, actorName, project.name)
        project
    }

    /**
     * Adds a material line the user typed.
     *
     * [catalogItemId] is optional on purpose. A template line points at the
     * catalogue and gets an icon and a unit for free, but plenty of what goes
     * into a job has no catalogue entry — offcuts, a length of hose, whatever
     * the merchant had. Those still belong on the sheet.
     */
    suspend fun addMaterial(
        projectId: String,
        label: String,
        unit: String,
        quantity: Double,
        catalogItemId: String? = null,
        actorName: String,
    ) {
        val line = ProjectMaterialEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            inventoryItemId = null,
            catalogItemId = catalogItemId,
            label = label.trim(),
            unit = unit,
            requiredQuantity = quantity,
            sortOrder = dao.nextMaterialSortOrder(projectId),
        )
        dao.upsertMaterials(listOf(line))
        audit.record("project_material", line.id, AuditTrail.Action.CREATE, actorName, line.label)
    }

    suspend fun removeMaterial(material: ProjectMaterialEntity, actorName: String) {
        dao.deleteMaterial(material)
        audit.record("project_material", material.id, AuditTrail.Action.DELETE, actorName, material.label)
    }

    suspend fun addTask(projectId: String, title: String, actorName: String) {
        val task = ProjectTaskEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            title = title.trim(),
            sortOrder = dao.nextTaskSortOrder(projectId),
        )
        dao.upsertTasks(listOf(task))
        audit.record("project_task", task.id, AuditTrail.Action.CREATE, actorName, task.title)
    }

    suspend fun removeTask(task: ProjectTaskEntity, actorName: String) {
        dao.deleteTask(task.id)
        audit.record("project_task", task.id, AuditTrail.Action.DELETE, actorName, task.title)
    }

    /** Templates for the trades the user works in, read straight from assets. */
    suspend fun templatesForTrades(tradeIds: Collection<String>): List<Pair<String, ProjectTemplateDto>> =
        withContext(Dispatchers.IO) {
            source.manifest().trades
                .filter { it.id in tradeIds }
                .flatMap { trade ->
                    val path = trade.templatesFile ?: return@flatMap emptyList()
                    source.decode<TemplateFile>(path).templates.map { trade.id to it }
                }
        }

    /**
     * Creates a project from a template, expanding its material lines and task
     * list. Material labels are resolved through the catalogue in the language
     * that is active now, so a job sheet reads in the language of whoever set
     * it up rather than in the template's authoring language.
     */
    suspend fun createFromTemplate(
        tradeId: String,
        template: ProjectTemplateDto,
        name: String,
        kindLabel: String,
        languageTag: String,
        actorName: String,
    ): ProjectEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val projectId = UUID.randomUUID().toString()

        val project = ProjectEntity(
            id = projectId,
            name = name.ifBlank { template.names.resolve(languageTag) },
            kindLabel = kindLabel,
            status = Status.PLANNED,
            startDate = null,
            dueDate = null,
            notes = template.descriptions.resolve(languageTag),
            templateId = template.id,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(project)

        val catalogue = catalogueIndex(tradeId)
        dao.upsertMaterials(
            template.materials.mapIndexed { index, line ->
                val item = catalogue[line.itemId]
                ProjectMaterialEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    inventoryItemId = null,
                    catalogItemId = line.itemId,
                    label = item?.names?.resolve(languageTag) ?: line.itemId,
                    unit = item?.unit ?: "PCS",
                    requiredQuantity = line.quantity,
                    sortOrder = index,
                )
            },
        )
        dao.upsertTasks(
            template.tasks.sortedBy { it.sortOrder }.mapIndexed { index, task ->
                ProjectTaskEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = task.titles.resolve(languageTag),
                    sortOrder = index,
                )
            },
        )

        audit.record(
            ENTITY, projectId, AuditTrail.Action.CREATE, actorName,
            "Created from template ${template.id}",
        )
        project
    }

    private fun catalogueIndex(tradeId: String) =
        source.manifest().trades.firstOrNull { it.id == tradeId }
            ?.let { source.decode<CatalogItemFile>(it.itemsFile).items.associateBy { item -> item.id } }
            .orEmpty()

    private companion object {
        const val ENTITY = "project"
    }
}
