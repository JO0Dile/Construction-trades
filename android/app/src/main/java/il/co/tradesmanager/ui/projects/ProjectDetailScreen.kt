package il.co.tradesmanager.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.ItemThumbnail
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionHeaderWithAdd
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import il.co.tradesmanager.ui.components.unitLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    container: AppContainer,
    projectId: String,
    onOpenMoney: () -> Unit,
    onOpenDailyLog: () -> Unit,
    onOpenConcrete: () -> Unit,
    onOpenScaffolds: () -> Unit,
    onOpenLifts: () -> Unit,
    onOpenTemporaryWorks: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { ProjectDetailViewModel(it, projectId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val money by viewModel.financials.collectAsStateWithLifecycle()
    // A job is all five lenses at once, so each section asks separately. A
    // finance clerk opening a job sees what it cost, not the task list.
    val signedIn = session as? SessionRepository.State.SignedIn
    val canSeePlan = signedIn?.canRead(Lens.PLAN) != false
    val canEditPlan = signedIn?.canWrite(Lens.PLAN) != false
    val canSeeStuff = signedIn?.canRead(Lens.STUFF) != false
    val canEditStuff = signedIn?.canWrite(Lens.STUFF) != false
    val canSeeMoney = signedIn?.canRead(Lens.MONEY) != false
    val canSeeEvidence = signedIn?.canRead(Lens.EVIDENCE) != false
    val canEditEvidence = signedIn?.canWrite(Lens.EVIDENCE) != false
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val project = state.project
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }
    var addingMaterial by remember { mutableStateOf(false) }
    var addingTask by remember { mutableStateOf(false) }
    val addImage = rememberImageAdder(
        newCameraTarget = viewModel::newCameraTarget,
        onCaptured = viewModel::onCaptured,
        onPicked = viewModel::onPicked,
    )

    // Typed explicitly: a null add-action is what turns a section read-only,
    // and inferring that through a chain is the kind of thing that quietly
    // becomes non-null again during a refactor.
    // Adding to a job sheet appends, so on a list of any length the line you
    // just typed lands below the fold and the screen looks like it ignored
    // you. Scrolling to it is the difference between "that did not work" and
    // "there it is".
    //
    // The row counts below mirror the LazyColumn's sections. If a section is
    // added or moved there, it has to move here too — there is no way in
    // Compose to ask a lazy list where a key ended up when it is off screen.
    val listState = rememberLazyListState()
    val rowsAboveTasks = (if (project != null) 1 else 0) +
        (if (canSeeMoney) 1 else 0) +
        // The daily log, concrete, the scaffold register, the lift plans, the
        // temporary works register, then the photographs. Concrete is a
        // delivery, so it follows Stuff; the rest are Evidence.
        (if (canSeeEvidence) 1 else 0) +
        (if (canSeeStuff) 1 else 0) +
        (if (canSeeEvidence) 4 else 0) +
        (if (state.tasks.isNotEmpty() && canSeePlan) 1 else 0)
    val firstTaskRow = rowsAboveTasks + 1
    val firstMaterialRow = firstTaskRow +
        (if (state.tasks.isEmpty()) 1 else 0) + state.tasks.size + 1

    var knownTasks by remember(projectId) { mutableIntStateOf(-1) }
    LaunchedEffect(state.tasks.size) {
        val previous = knownTasks
        knownTasks = state.tasks.size
        if (canSeePlan && previous >= 0 && state.tasks.size > previous) {
            listState.animateScrollToItem(firstTaskRow + state.tasks.size - 1)
        }
    }

    var knownMaterials by remember(projectId) { mutableIntStateOf(-1) }
    LaunchedEffect(state.materials.size) {
        val previous = knownMaterials
        knownMaterials = state.materials.size
        if (canSeeStuff && previous >= 0 && state.materials.size > previous) {
            listState.animateScrollToItem(firstMaterialRow + state.materials.size - 1)
        }
    }

    val addTaskAction: (() -> Unit)? = if (canEditPlan) {
        { addingTask = true }
    } else {
        null
    }
    val addMaterialAction: (() -> Unit)? = if (canEditStuff) {
        { addingMaterial = true }
    } else {
        null
    }
    val addPhotoAction: (() -> Unit)? = if (canEditEvidence) addImage else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: stringResource(R.string.proj_title)) },
                actions = {
                    val project = state.project
                    IconButton(
                        enabled = project != null,
                        onClick = {
                            if (project == null) return@IconButton
                            val result = Exporter.write(
                                context = context,
                                document = ExportDocument.ProjectSheet(project, state.tasks, state.materials),
                                languageTag = languageTag,
                                locale = locale,
                                rightToLeft = layoutDirection == LayoutDirection.Rtl,
                            )
                            context.startActivity(Exporter.shareIntent(context, result))
                        },
                    ) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = stringResource(R.string.set_export),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding)) {
            if (project != null) {
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        DetailRow(stringResource(R.string.proj_status), stringResource(statusLabel(project.status)))
                        project.city?.let { DetailRow(stringResource(R.string.proj_address), it) }
                        project.clientName?.let { DetailRow(stringResource(R.string.proj_client), it) }
                    }
                }
            }

            // The Money lens, as one line and a way in. A job's finances are
            // too much to inline here and too important to bury in a menu.
            if (canSeeMoney) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.money_title)) },
                        supportingContent = {
                            Text(
                                if (money.revisedContract == 0.0) {
                                    stringResource(R.string.money_empty)
                                } else {
                                    stringResource(R.string.money_margin) + " " +
                                        Formats.money(money.margin, locale)
                                },
                            )
                        },
                        trailingContent = {
                            Text(
                                Formats.money(money.revisedContract, locale),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onOpenMoney),
                    )
                }
            }

            // The day's log lives beside the money for the same reason: it is
            // too much to inline and too important to bury in a menu.
            if (canSeeEvidence) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.log_title)) },
                        supportingContent = { Text(stringResource(R.string.log_notes_hint)) },
                        modifier = Modifier.clickable(onClick = onOpenDailyLog),
                    )
                }
            }

            // Concrete is under Stuff, not Evidence: it is material arriving
            // on a lorry, and the person who books it in is the one who books
            // in everything else that gets delivered.
            if (canSeeStuff) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.pour_title)) },
                        supportingContent = { Text(stringResource(R.string.pour_row_hint)) },
                        modifier = Modifier.clickable(onClick = onOpenConcrete),
                    )
                }
            }

            // The scaffold register sits under Evidence, not Stuff: nobody
            // is booking a scaffold in, they are recording that somebody
            // competent looked at it and what they found.
            if (canSeeEvidence) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.scf_title)) },
                        supportingContent = { Text(stringResource(R.string.scf_row_hint)) },
                        modifier = Modifier.clickable(onClick = onOpenScaffolds),
                    )
                }
            }

            // Lifts are Evidence for the same reason scaffolds are: the row
            // is a record that somebody worked out the numbers and named the
            // three people, not a note that a crane turned up.
            if (canSeeEvidence) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lift_title)) },
                        supportingContent = { Text(stringResource(R.string.lift_row_hint)) },
                        modifier = Modifier.clickable(onClick = onOpenLifts),
                    )
                }
            }

            if (canSeeEvidence) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.tw_title)) },
                        supportingContent = { Text(stringResource(R.string.tw_row_hint)) },
                        modifier = Modifier.clickable(onClick = onOpenTemporaryWorks),
                    )
                }
            }

            if (canSeeEvidence) {
                item {
                    ProjectImages(
                        images = state.images,
                        planId = state.plan?.id,
                        onAdd = addPhotoAction,
                        onOpen = { viewing = it },
                    )
                }
            }

            if (state.tasks.isNotEmpty() && canSeePlan) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(
                            stringResource(R.string.proj_progress) + "  " +
                                Formats.percent(state.progress, locale),
                            modifier = Modifier.padding(horizontal = 0.dp),
                        )
                        LinearProgressIndicator(
                            progress = { state.progress.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Both sections are drawn whether or not they hold anything. A job
            // started from nothing has to be fillable, and a section that only
            // appears once it has content can never be the thing you add to.
            if (canSeePlan) {
                item {
                    SectionHeaderWithAdd(
                        title = stringResource(R.string.proj_tasks),
                        contentDescription = stringResource(R.string.proj_add_task),
                        onAdd = addTaskAction,
                    )
                }
                if (state.tasks.isEmpty()) {
                    item { SectionPlaceholder(stringResource(R.string.proj_tasks_empty)) }
                }
                items(state.tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.title) },
                        leadingContent = {
                            Checkbox(
                                checked = task.isDone,
                                enabled = canEditPlan,
                                onCheckedChange = { viewModel.setTaskDone(task.id, it) },
                            )
                        },
                        trailingContent = {
                            if (canEditPlan) {
                                IconButton(onClick = { viewModel.removeTask(task) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            if (canSeeStuff) {
                item {
                    SectionHeaderWithAdd(
                        title = stringResource(R.string.proj_materials),
                        contentDescription = stringResource(R.string.proj_add_material),
                        onAdd = addMaterialAction,
                    )
                }
                if (state.materials.isEmpty()) {
                    item { SectionPlaceholder(stringResource(R.string.proj_materials_empty)) }
                }
                items(state.materials, key = { it.id }) { material ->
                    ListItem(
                        leadingContent = {
                            ItemThumbnail(
                                category = state.categoryOf(material),
                                kind = "MATERIAL",
                                catalogItemId = material.catalogItemId,
                                size = 36,
                            )
                        },
                        headlineContent = { Text(material.label) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.proj_required_qty) + ": " +
                                    Formats.quantity(material.requiredQuantity, locale) + " " +
                                    stringResource(unitLabel(material.unit)),
                            )
                        },
                        trailingContent = {
                            if (canEditStuff) {
                                IconButton(onClick = { viewModel.removeMaterial(material) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (addingMaterial) {
        AddMaterialDialog(
            languageTag = languageTag,
            search = viewModel::searchCatalog,
            onDismiss = { addingMaterial = false },
            onAdd = { label, unit, quantity, catalogItemId ->
                viewModel.addMaterial(label, unit, quantity, catalogItemId)
                addingMaterial = false
            },
        )
    }

    if (addingTask) {
        AddTaskDialog(
            onDismiss = { addingTask = false },
            onAdd = {
                viewModel.addTask(it)
                addingTask = false
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            isPlan = photo.id == state.plan?.id,
            onSetAsPlan = {
                viewModel.setAsPlan(photo)
                viewing = null
            },
            onDelete = {
                viewModel.deletePhoto(photo)
                viewing = null
            },
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun ProjectImages(
    images: List<PhotoEntity>,
    planId: String?,
    /** Null when this person may see the photographs but not add any. */
    onAdd: (() -> Unit)?,
    onOpen: (PhotoEntity) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionHeader(stringResource(R.string.photo_section))

        if (images.isEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.photo_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.photo_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                if (onAdd != null) {
                    OutlinedButton(onClick = onAdd) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                        Text(
                            text = stringResource(R.string.photo_add),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(photo) },
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.note,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (photo.id == planId) {
                            // The drawing is what people hunt for on a site,
                            // so it says so on the thumbnail.
                            Text(
                                text = stringResource(R.string.photo_plan_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (onAdd != null) {
                    item {
                        OutlinedButton(
                            onClick = onAdd,
                            modifier = Modifier.size(110.dp),
                        ) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = stringResource(R.string.photo_add),
                            )
                        }
                    }
                }
            }
        }
    }
}
