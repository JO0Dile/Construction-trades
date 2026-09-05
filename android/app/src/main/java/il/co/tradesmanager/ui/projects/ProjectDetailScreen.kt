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
import androidx.compose.runtime.getValue
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
import il.co.tradesmanager.core.i18n.Formats
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
fun ProjectDetailScreen(container: AppContainer, projectId: String, onBack: () -> Unit) {
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { ProjectDetailViewModel(it, projectId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        LazyColumn(Modifier.padding(padding)) {
            if (project != null) {
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        DetailRow(stringResource(R.string.proj_status), stringResource(statusLabel(project.status)))
                        project.city?.let { DetailRow(stringResource(R.string.proj_address), it) }
                        project.clientName?.let { DetailRow(stringResource(R.string.proj_client), it) }
                    }
                }
            }

            item {
                ProjectImages(
                    images = state.images,
                    planId = state.plan?.id,
                    onAdd = addImage,
                    onOpen = { viewing = it },
                )
            }

            if (state.tasks.isNotEmpty()) {
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
            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.proj_tasks),
                    contentDescription = stringResource(R.string.proj_add_task),
                    onAdd = { addingTask = true },
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
                            onCheckedChange = { viewModel.setTaskDone(task.id, it) },
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeTask(task) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                )
            }

            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.proj_materials),
                    contentDescription = stringResource(R.string.proj_add_material),
                    onAdd = { addingMaterial = true },
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
                        IconButton(onClick = { viewModel.removeMaterial(material) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                )
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
    onAdd: () -> Unit,
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
                OutlinedButton(onClick = onAdd) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Text(
                        text = stringResource(R.string.photo_add),
                        modifier = Modifier.padding(start = 8.dp),
                    )
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
