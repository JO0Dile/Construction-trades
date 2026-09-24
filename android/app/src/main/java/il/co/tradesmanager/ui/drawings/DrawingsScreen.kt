package il.co.tradesmanager.ui.drawings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.DrawingEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.DrawingRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One job's drawings: the revision of each to build from, and what it replaced.
 *
 * The list only ever shows current revisions. An earlier one is a tap away on
 * its drawing, marked as replaced, because the one mistake this screen is for
 * is somebody working off a revision that has been superseded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: DrawingsViewModel =
        viewModel(factory = ViewModelFactory(container) { DrawingsViewModel(it, projectId) })
    val mayRecord by viewModel.mayRecord.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val photoCounts by viewModel.photoCounts.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val openHistory by viewModel.openHistory.collectAsStateWithLifecycle()
    val openPhotos by viewModel.openPhotos.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var recording by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val addSheet = rememberImageAdder(
        newCameraTarget = { viewModel.newSheetTarget() },
        onCaptured = { viewModel.sheetCaptured(it) },
        onPicked = { viewModel.sheetPicked(it) },
    )

    val drawing = open
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        drawing?.let { listOf(it.number, it.title).filter { part -> part.isNotBlank() }.joinToString(SEPARATOR) }
                            ?: stringResource(R.string.dwg_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (drawing != null) viewModel.openDrawing(null) else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (mayRecord && drawing == null) {
                FloatingActionButton(onClick = { recording = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dwg_record))
                }
            }
        },
    ) { padding ->
        if (drawing == null) {
            if (current.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.dwg_empty),
                    hint = stringResource(R.string.dwg_blurb),
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(Modifier.padding(padding)) {
                    item {
                        Text(
                            stringResource(R.string.dwg_blurb),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(current, key = { it.id }) { row ->
                        ListItem(
                            overlineContent = { Text(row.number) },
                            headlineContent = { Text(row.title.ifBlank { row.number }) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        stringResource(R.string.dwg_revision, row.revision),
                                        stringResource(R.string.dwg_received, dateOf(row.receivedAt, locale)),
                                        if ((photoCounts[row.id] ?: 0) == 0) stringResource(R.string.dwg_no_sheet) else null,
                                    ).joinToString(SEPARATOR),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.openDrawing(row.id) },
                        )
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(
                            if (drawing.supersededAt == null) R.string.dwg_current else R.string.dwg_superseded_banner,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (drawing.supersededAt == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        listOfNotNull(
                            stringResource(R.string.dwg_revision, drawing.revision),
                            stringResource(R.string.dwg_received, dateOf(drawing.receivedAt, locale)),
                            drawing.recordedByName,
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    drawing.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { SectionHeader(stringResource(R.string.dwg_sheet)) }
            if (openPhotos.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.dwg_no_sheet)) }
            } else {
                item { SheetThumbnails(openPhotos) { viewing = it } }
            }
            if (mayRecord) {
                item {
                    OutlinedButton(
                        onClick = addSheet,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.dwg_add_sheet)) }
                }
            }
            item { SectionHeader(stringResource(R.string.dwg_history)) }
            if (openHistory.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.dwg_no_history)) }
            }
            items(openHistory, key = { it.id }) { earlier ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dwg_revision, earlier.revision)) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                stringResource(R.string.dwg_received, dateOf(earlier.receivedAt, locale)),
                                earlier.supersededAt?.let { stringResource(R.string.dwg_replaced_on, dateOf(it, locale)) },
                            ).joinToString(SEPARATOR),
                        )
                    },
                    modifier = Modifier.clickable { viewModel.openDrawing(earlier.id) },
                )
            }
        }
    }

    if (recording) {
        RecordDialog(
            currentOf = viewModel::currentOf,
            onDismiss = { recording = false },
            onRecord = { number, title, revision, notes ->
                recording = false
                viewModel.record(number, title, revision, notes)
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" belongs to a job's own plan, not to one sheet.
            isPlan = true,
            onSetAsPlan = {},
            onDelete = {
                viewModel.deletePhoto(photo)
                viewing = null
            },
            onDismiss = { viewing = null },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(refusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetThumbnails(photos: List<PhotoEntity>, onOpen: (PhotoEntity) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        photos.forEach { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = stringResource(R.string.dwg_sheet),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clickable { onOpen(photo) },
            )
        }
    }
}

/**
 * Recording a drawing, or a new revision of one.
 *
 * Typing a number that is already on the register says which revision the
 * new one replaces, before it is saved -- the person holding the new sheet
 * should see that the old one is about to stop being the one to build from.
 */
@Composable
private fun RecordDialog(
    currentOf: (String) -> DrawingEntity?,
    onDismiss: () -> Unit,
    onRecord: (number: String, title: String, revision: String, notes: String) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val existing = if (number.isBlank()) null else currentOf(number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dwg_record)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.dwg_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing != null) {
                    Text(
                        stringResource(R.string.dwg_will_replace, existing.revision),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dwg_title_field)) },
                    // A new revision keeps its drawing's title unless another is typed.
                    placeholder = { existing?.title?.takeIf { it.isNotBlank() }?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = revision,
                    onValueChange = { revision = it },
                    label = { Text(stringResource(R.string.dwg_revision_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.dwg_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.isNotBlank() && revision.isNotBlank(),
                onClick = { onRecord(number, title, revision, notes) },
            ) { Text(stringResource(R.string.dwg_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
private fun refusalText(refusal: DrawingRepository.Refusal): Int = when (refusal) {
    DrawingRepository.Refusal.NOT_ALLOWED -> R.string.dwg_refused_not_allowed
    DrawingRepository.Refusal.NO_NUMBER -> R.string.dwg_refused_number
    DrawingRepository.Refusal.NO_REVISION -> R.string.dwg_refused_revision
    DrawingRepository.Refusal.ALREADY_CURRENT -> R.string.dwg_refused_current
    DrawingRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

private const val SEPARATOR = " · "
