package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.catalog.CatalogItemFile
import il.co.tradesmanager.data.catalog.CatalogSource
import il.co.tradesmanager.data.catalog.ProjectTemplateDto
import il.co.tradesmanager.data.catalog.TemplateFile
import il.co.tradesmanager.data.local.dao.ProjectDao
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
