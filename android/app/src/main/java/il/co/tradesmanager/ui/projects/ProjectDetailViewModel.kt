package il.co.tradesmanager.ui.projects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.core.safety.FirePoints
import il.co.tradesmanager.core.safety.Risks
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.core.work.Attention
import il.co.tradesmanager.core.work.Queries
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.catalog.WorkStage
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.repository.EmergencySheetRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.firepoints.FirePointsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectDetailViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class State(
        val project: ProjectEntity? = null,
        val materials: List<ProjectMaterialEntity> = emptyList(),
        val tasks: List<ProjectTaskEntity> = emptyList(),
        /** Catalogue id -> category, so a material line can pick its icon. */
        val categories: Map<String, String> = emptyMap(),
        val images: List<PhotoEntity> = emptyList(),
    ) {
        /** A job has exactly one plan; the rest are progress photos. */
        val plan: PhotoEntity?
            get() = images.firstOrNull { it.ownerType == PhotoRepository.Owner.PROJECT_PLAN }

        fun categoryOf(material: ProjectMaterialEntity): String =
            material.catalogItemId?.let { categories[it] }.orEmpty()

        /** Task completion drives the progress bar; an empty list is 0, not NaN. */
        val progress: Double
            get() = if (tasks.isEmpty()) 0.0 else tasks.count { it.isDone }.toDouble() / tasks.size
    }

    val state: StateFlow<State> = combine(
        container.projects.observeProject(projectId),
        container.projects.observeMaterials(projectId),
        container.projects.observeTasks(projectId),
        container.catalogDao.observeCategories(),
        container.photos.observeProjectImages(projectId),
    ) { project, materials, tasks, categories, images ->
        State(project, materials, tasks, categories.associate { it.id to it.category }, images)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /**
     * The parts this job is made of: its floors, its flats, its plots.
     *
     * Each is a job in its own right with its own tasks, materials,
     * photographs and snags -- which is the point. Twenty floors flattened
     * into one job cannot say which floor anything happened on, and twenty
     * separate jobs cannot say which building they are in.
     */
    val parts: StateFlow<List<ProjectEntity>> = container.projects.observeParts(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job this one is a part of, when it is one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val parent: StateFlow<ProjectEntity?> = state
        .map { it.project?.parentProjectId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else container.projects.observeProject(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Adds a part to this job.
     *
     * Only one level deep is offered: a part cannot itself be broken up here.
     * A site with buildings with floors with rooms is four levels the data
     * allows, and a screen that lets somebody build one is a screen where the
     * job they are looking for is four taps from where they expected it. If
     * that turns out to be needed it should be built deliberately.
     */
    fun addPart(name: String, kindLabel: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        container.projects.createBlank(
            name = name,
            kindLabel = kindLabel,
            actorName = container.settings.settings.first().actorName,
            parentProjectId = projectId,
        )
    }

    /** Enough of the Money lens for the one line that opens it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val financials: StateFlow<JobFinancials> = container.session.state
        .flatMapLatest { session ->
            val role = (session as? SessionRepository.State.SignedIn)?.role
            if (role?.canRead(Lens.MONEY) == true) container.money.observeFinancials(projectId) else flowOf(JobFinancials())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobFinancials())

    /** What the person looking at this job is allowed to see and change. */
    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /**
     * What on this job is waiting on somebody, by each register's own rule.
     * The plan's registers are counted only for somebody who may read the
     * plan, the site record's only for somebody who may read that: for
     * anybody else those lines are absent, not zero.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val attention: StateFlow<List<Attention.Line>> = container.session.state
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            val fromPlan = if (role != null && role.canRead(Lens.PLAN)) {
                combine(
                    container.designQueries.observeForProject(projectId),
                    container.submittals.observeForProject(projectId),
                    container.delays.observeForProject(projectId),
                ) { queries, submittals, delays ->
                    val now = System.currentTimeMillis()
                    val zone = ZoneId.systemDefault()
                    val sentAgain = submittals.mapNotNull { it.resubmissionOf }.toSet()
                    val materials = submittals.map {
                        Submittals.state(Submittals.decisionOf(it.decision), it.neededBy, it.id in sentAgain, now, zone)
                    }
                    mapOf(
                        Attention.Item.QUERIES_OVERDUE to queries.count {
                            Queries.state(it.neededBy, it.answeredAt, now, zone) == Queries.State.OVERDUE
                        },
                        Attention.Item.MATERIALS_REJECTED to materials.count { it == Submittals.State.REJECTED },
                        Attention.Item.MATERIALS_OVERDUE to materials.count { it == Submittals.State.OVERDUE },
                        Attention.Item.DELAYS_RUNNING to delays.count { it.endedOnDay == null },
                        Attention.Item.DELAYS_WITHOUT_NOTICE to delays.count { it.notifiedOnDay == null },
                    )
                }
            } else {
                flowOf(emptyMap<Attention.Item, Int>())
            }
            val fromRecord = if (role != null && role.canRead(Lens.EVIDENCE)) {
                combine(
                    container.inspections.observeForProject(projectId),
                    container.risks.observeForProject(projectId),
                    container.complaints.observeForProject(projectId),
                    container.substances.observeForProject(projectId),
                    combine(
                        container.firePoints.observeForProject(projectId),
                        container.firePoints.observeChecksForProject(projectId),
                        container.emergencySheets.observe(projectId),
                    ) { points, checks, sheet -> FirePointsViewModel.rowsOf(points, checks) to sheet },
                ) { inspections, risks, complaints, substances, (firePoints, emergency) ->
                    val now = System.currentTimeMillis()
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now()
                    val askedAgain = inspections.mapNotNull { it.reinspectionOf }.toSet()
                    val inspected = inspections.map {
                        Inspections.state(Inspections.resultOf(it.result), it.wantedOn, it.id in askedAgain, now, zone)
                    }
                    val riskStates = risks.map {
                        Risks.state(it.closed, Risks.score(it.likelihoodAfter, it.severityAfter), it.reviewOnDay?.let(LocalDate::ofEpochDay), today)
                    }
                    mapOf(
                        Attention.Item.INSPECTIONS_FAILED to inspected.count { it == Inspections.State.FAILED },
                        Attention.Item.INSPECTIONS_OVERDUE to inspected.count { it == Inspections.State.OVERDUE },
                        Attention.Item.RISKS_EXTREME to riskStates.count { it == Risks.State.EXTREME },
                        Attention.Item.RISK_REVIEWS_OVERDUE to riskStates.count { it == Risks.State.REVIEW_OVERDUE },
                        Attention.Item.COMPLAINTS_WAITING to complaints.count {
                            Complaints.state(it.receivedAt, it.answeredAt, now, zone) == Complaints.State.WAITING_LONG
                        },
                        Attention.Item.FIRE_POINTS to firePoints.count { FirePoints.needsAttention(it.state) },
                        Attention.Item.EMERGENCY_INFO_MISSING to EmergencySheet.missing(EmergencySheetRepository.sheetOf(emergency)).size,
                        Attention.Item.SUBSTANCES_WITHOUT_SHEET to substances.count {
                            Substances.state(it.sheetOnDay?.let(LocalDate::ofEpochDay), it.removedAt != null, today) == Substances.State.NO_SHEET
                        },
                    )
                }
            } else {
                flowOf(emptyMap<Attention.Item, Int>())
            }
            combine(fromPlan, fromRecord) { plan, record -> Attention.lines(plan + record) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    /**
     * The very first image added to a job becomes its plan, because that is
     * almost always what it is — someone photographing the drawing before
     * they start.
     */
    fun onCaptured(photoId: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = ownerTypeForNewImage(),
            ownerId = projectId,
            actorName = actor,
        )
    }

    fun onPicked(uri: Uri) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(
            source = uri,
            ownerType = ownerTypeForNewImage(),
            ownerId = projectId,
            actorName = actor,
        )
    }

    /**
     * When the job runs.
     *
     * Both columns have been on the table since the beginning and nothing has
     * ever written to either, which is why `observeOverdue` -- the dashboard
     * tile that answers "which jobs have run late" -- filters
     * `dueDate IS NOT NULL` against a column where it never is, and has
     * therefore always been empty. The job list's `ORDER BY dueDate` has been
     * ordering by nothing too.
     *
     * Null clears. A job whose date was set by mistake has to be able to go
     * back to having none, and "no date" is a real answer -- plenty of work is
     * open-ended until somebody signs something.
     */
    fun setDates(startDate: Long?, dueDate: Long?) = viewModelScope.launch {
        val project = state.value.project ?: return@launch
        container.projects.save(
            project.copy(
                startDate = startDate,
                dueDate = dueDate,
            ),
            actorName = container.settings.settings.first().actorName,
        )
    }

    /**
     * Where the job is and who it is for.
     *
     * All optional and all editable after the fact, because a job is usually
     * created in ten seconds when it is won and filled in properly later.
     * Blank clears the field rather than keeping the old value: somebody who
     * empties a box means it, and a form that quietly refuses to forget is
     * one nobody trusts with a correction.
     */
    fun setPlaceAndClient(
        street: String,
        city: String,
        postalCode: String,
        clientName: String,
        clientPhone: String,
    ) = viewModelScope.launch {
        val project = state.value.project ?: return@launch
        container.projects.save(
            project.copy(
                street = street.trim().takeIf { it.isNotEmpty() },
                city = city.trim().takeIf { it.isNotEmpty() },
                postalCode = postalCode.trim().takeIf { it.isNotEmpty() },
                clientName = clientName.trim().takeIf { it.isNotEmpty() },
                clientPhone = clientPhone.trim().takeIf { it.isNotEmpty() },
            ),
            actorName = container.settings.settings.first().actorName,
        )
    }

    private fun ownerTypeForNewImage(): String =
        if (state.value.plan == null) {
            PhotoRepository.Owner.PROJECT_PLAN
        } else {
            PhotoRepository.Owner.PROJECT_PHOTO
        }

    fun setAsPlan(photo: PhotoEntity) = viewModelScope.launch {
        val role = (container.session.state.first() as? SessionRepository.State.SignedIn)?.role
        if (role == null || !role.canWrite(Lens.EVIDENCE)) return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.markAsPlan(photo, state.value.plan, actor)
    }

    /** Only for somebody who may change the site's record; the job's photographs are part of it. */
    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        val role = (container.session.state.first() as? SessionRepository.State.SignedIn)?.role
        if (role == null || !role.canWrite(Lens.EVIDENCE)) return@launch
        container.photos.delete(photo, container.settings.settings.first().actorName)
    }

    /**
     * Catalogue matches for the add-material dialog. Returns the raw rows so
     * the caller resolves names in whatever language is on screen — the same
     * search in Hebrew and in Arabic hits the same rows.
     */
    suspend fun searchCatalog(query: String): List<CatalogItemEntity> {
        if (query.isBlank()) return emptyList()
        val tradeIds = container.catalogDao.selectedTradeIds()
        return container.catalogDao.searchCatalogItems(tradeIds, query.trim())
    }

    fun addMaterial(label: String, unit: String, quantity: Double, catalogItemId: String?) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            container.projects.addMaterial(
                projectId = projectId,
                label = label,
                unit = unit,
                quantity = quantity,
                catalogItemId = catalogItemId,
                actorName = actor,
            )
        }

    fun removeMaterial(material: ProjectMaterialEntity) = viewModelScope.launch {
        container.projects.removeMaterial(material, container.settings.settings.first().actorName)
    }

    fun addTask(title: String, stageId: String? = null) = viewModelScope.launch {
        container.projects.addTask(
            projectId = projectId,
            title = title,
            actorName = container.settings.settings.first().actorName,
            stageId = stageId,
        )
    }

    /** The stages of a job, for the picker. Read-only reference data. */
    val stages: List<WorkStage> get() = container.scopes.stages

    fun stageName(id: String?, languageTag: String): String? =
        container.scopes.stage(id)?.names?.resolve(languageTag)

    fun setTaskStage(task: ProjectTaskEntity, stageId: String?) = viewModelScope.launch {
        container.projects.setTaskStage(
            task = task,
            stageId = stageId,
            scopeId = task.scopeId.takeIf { stageId != null },
            actorName = container.settings.settings.first().actorName,
        )
    }

    fun removeTask(task: ProjectTaskEntity) = viewModelScope.launch {
        container.projects.removeTask(task, container.settings.settings.first().actorName)
    }

    fun setTaskDone(taskId: String, done: Boolean) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.projects.setTaskDone(taskId, done, actor)
    }
}
