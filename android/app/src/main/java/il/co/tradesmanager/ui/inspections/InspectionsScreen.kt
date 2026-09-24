package il.co.tradesmanager.ui.inspections

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.InspectionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * One job's inspection requests.
 *
 * Overdue and failed ones first and in red: the reason anybody opens this on
 * the morning of a pour is to find out whether the steel has been passed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: InspectionsViewModel =
        viewModel(factory = ViewModelFactory(container) { InspectionsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val openPhotos by viewModel.openPhotos.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var requesting by remember { mutableStateOf(false) }
    var deciding by remember { mutableStateOf(false) }
    var askingAgain by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val addForm = rememberImageAdder(
        newCameraTarget = { viewModel.newFormTarget() },
        onCaptured = { viewModel.formCaptured(it) },
        onPicked = { viewModel.formPicked(it) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ir_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { requesting = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ir_request))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ir_empty),
                hint = stringResource(R.string.ir_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.inspection.id }) { row ->
                val inspection = row.inspection
                val alarming = row.state == Inspections.State.OVERDUE || row.state == Inspections.State.FAILED
                ListItem(
                    overlineContent = {
                        Text(
                            listOf(
                                inspection.reference,
                                stringResource(inspectionKindLabel(Inspections.kindOf(inspection.kind))),
                                inspection.requestedOf,
                            ).joinToString(SEPARATOR),
                        )
                    },
                    headlineContent = { Text(inspection.element, maxLines = 3) },
                    supportingContent = {
                        Text(
                            stateLine(row, locale),
                            color = stateColour(row.state),
                            fontWeight = if (alarming) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openInspection(inspection.id) },
                )
            }
        }
    }

    open?.let { row ->
        DetailDialog(
            row = row,
            photos = openPhotos,
            locale = locale,
            mayWrite = mayWrite,
            onDismiss = { viewModel.openInspection(null) },
            onDecide = { deciding = true },
            onAskAgain = { askingAgain = true },
            onAddForm = addForm,
            onViewPhoto = { viewing = it },
        )
    }

    if (requesting) {
        RequestDialog(
            onDismiss = { requesting = false },
            onRequest = { kind, element, requestedOf, wantedOn ->
                requesting = false
                viewModel.request(kind, element, requestedOf, wantedOn)
            },
        )
    }

    val decidingRow = open
    if (deciding && decidingRow != null) {
        DecideDialog(
            onDismiss = { deciding = false },
            onDecide = { result, inspector, comments ->
                deciding = false
                viewModel.decide(decidingRow.inspection.id, result, inspector, comments)
            },
        )
    }

    val failedRow = open
    if (askingAgain && failedRow != null) {
        AskAgainDialog(
            onDismiss = { askingAgain = false },
            onAsk = { wantedOn ->
                askingAgain = false
                viewModel.reinspect(failedRow.inspection.id, wantedOn)
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" belongs to a job's own plan, not to one form.
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
            text = { Text(stringResource(inspectionRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun stateColour(state: Inspections.State): Color = when (state) {
    Inspections.State.OVERDUE, Inspections.State.FAILED -> MaterialTheme.colorScheme.error
    Inspections.State.PASSED -> MaterialTheme.colorScheme.primary
    Inspections.State.WAITING, Inspections.State.REINSPECTED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun stateLine(row: InspectionsViewModel.Row, locale: Locale): String {
    val inspection = row.inspection
    val decided = inspection.decidedAt ?: inspection.requestedAt
    val inspector = inspection.inspectorName.orEmpty()
    return when (row.state) {
        Inspections.State.OVERDUE ->
            stringResource(R.string.ir_overdue, dateOf(inspection.wantedOn ?: inspection.requestedAt, locale))
        Inspections.State.FAILED -> stringResource(R.string.ir_failed_on, dateOf(decided, locale), inspector)
        Inspections.State.REINSPECTED -> stringResource(R.string.ir_reinspected_as, row.reinspectedAs.orEmpty())
        Inspections.State.PASSED -> listOfNotNull(
            stringResource(R.string.ir_passed_on, dateOf(decided, locale), inspector),
            row.clearedPour?.let { stringResource(R.string.ir_cleared, it) },
        ).joinToString(SEPARATOR)
        Inspections.State.WAITING -> inspection.wantedOn?.let { stringResource(R.string.ir_wanted, dateOf(it, locale)) }
            ?: stringResource(R.string.ir_asked_on, dateOf(inspection.requestedAt, locale))
    }
}

/** A request in full: what was asked, what was found, the form, and what can be done next. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailDialog(
    row: InspectionsViewModel.Row,
    photos: List<PhotoEntity>,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onDecide: () -> Unit,
    onAskAgain: () -> Unit,
    onAddForm: () -> Unit,
    onViewPhoto: (PhotoEntity) -> Unit,
) {
    val inspection = row.inspection
    val result = Inspections.resultOf(inspection.result)
    val mayDecide = mayWrite && inspection.decidedAt == null
    val mayAskAgain = mayWrite && row.state == Inspections.State.FAILED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(inspection.reference + SEPARATOR + stringResource(inspectionKindLabel(Inspections.kindOf(inspection.kind)))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(inspection.element, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        inspection.requestedOf,
                        stringResource(R.string.ir_asked_on, dateOf(inspection.requestedAt, locale)),
                        inspection.requestedByName,
                        inspection.wantedOn?.let { stringResource(R.string.ir_wanted, dateOf(it, locale)) },
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                row.reinspectionOf?.let {
                    Text(stringResource(R.string.ir_reinspection_of, it), style = MaterialTheme.typography.bodySmall)
                }
                if (result != null) {
                    Text(stringResource(R.string.ir_result), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(resultLabel(result)),
                        color = stateColour(row.state),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(
                            inspection.inspectorName,
                            inspection.decidedAt?.let { dateOf(it, locale) },
                            inspection.resultRecordedByName?.let { stringResource(R.string.ir_recorded_by, it) },
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    inspection.comments?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                row.reinspectedAs?.let {
                    Text(stringResource(R.string.ir_reinspected_as, it), style = MaterialTheme.typography.bodySmall)
                }
                row.clearedPour?.let {
                    Text(stringResource(R.string.ir_cleared, it), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.ir_form), style = MaterialTheme.typography.labelLarge)
                if (photos.isEmpty()) {
                    Text(stringResource(R.string.ir_no_form), style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = stringResource(R.string.ir_form),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clickable { onViewPhoto(photo) },
                            )
                        }
                    }
                }
                if (mayWrite) {
                    OutlinedButton(onClick = onAddForm, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ir_add_form))
                    }
                }
            }
        },
        confirmButton = {
            when {
                mayDecide -> TextButton(onClick = onDecide) { Text(stringResource(R.string.ir_record_result)) }
                mayAskAgain -> TextButton(onClick = onAskAgain) { Text(stringResource(R.string.ir_ask_again)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayDecide || mayAskAgain) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
}

/**
 * Asking for an inspection. The kind and who is asked are picked from the
 * usual answers or typed, and the day from the ones a foreman actually says.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestDialog(
    onDismiss: () -> Unit,
    onRequest: (kind: Inspections.Kind, element: String, requestedOf: String, wantedOn: Long?) -> Unit,
) {
    var kind by remember { mutableStateOf(Inspections.Kind.REINFORCEMENT) }
    var element by remember { mutableStateOf("") }
    var requestedOf by remember { mutableStateOf("") }
    var wantedInDays by remember { mutableStateOf<Int?>(null) }
    val usual = listOf(R.string.ir_of_engineer, R.string.ir_of_supervisor, R.string.ir_of_electrical, R.string.ir_of_client)
        .map { stringResource(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ir_request)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ir_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Inspections.Kind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(inspectionKindLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = element,
                    onValueChange = { element = it },
                    label = { Text(stringResource(R.string.ir_element)) },
                    supportingText = { Text(stringResource(R.string.ir_element_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ir_requested_of), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    usual.forEach { who ->
                        FilterChip(selected = requestedOf == who, onClick = { requestedOf = who }, label = { Text(who) })
                    }
                }
                OutlinedTextField(
                    value = requestedOf,
                    onValueChange = { requestedOf = it },
                    label = { Text(stringResource(R.string.ir_requested_of_typed)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                WantedOnChips(wantedInDays) { wantedInDays = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = element.isNotBlank() && requestedOf.isNotBlank(),
                onClick = { onRequest(kind, element, requestedOf, wantedInDays?.let(::daysFromToday)) },
            ) { Text(stringResource(R.string.ir_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WantedOnChips(selected: Int?, onSelect: (Int?) -> Unit) {
    Text(stringResource(R.string.ir_wanted_on), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.ir_no_date)) },
        )
        WANTED_IN.forEach { (days, label) ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/**
 * What the inspector found. The name is the person's, not their title: "the
 * engineer" passed nothing, somebody with a name did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecideDialog(
    onDismiss: () -> Unit,
    onDecide: (result: Inspections.Result, inspector: String, comments: String) -> Unit,
) {
    var result by remember { mutableStateOf<Inspections.Result?>(null) }
    var inspector by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    val needsComments = result == Inspections.Result.FAILED || result == Inspections.Result.PASSED_WITH_COMMENTS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ir_record_result)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Inspections.Result.entries.forEach { option ->
                        FilterChip(
                            selected = result == option,
                            onClick = { result = option },
                            label = { Text(stringResource(resultLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = inspector,
                    onValueChange = { inspector = it },
                    label = { Text(stringResource(R.string.ir_inspector)) },
                    supportingText = { Text(stringResource(R.string.ir_inspector_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = {
                        Text(
                            stringResource(
                                if (result == Inspections.Result.FAILED) R.string.ir_why_failed else R.string.ir_comments,
                            ),
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val chosen = result
            TextButton(
                enabled = chosen != null && inspector.isNotBlank() && (!needsComments || comments.isNotBlank()),
                onClick = { if (chosen != null) onDecide(chosen, inspector, comments) },
            ) { Text(stringResource(R.string.ir_record)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AskAgainDialog(onDismiss: () -> Unit, onAsk: (wantedOn: Long?) -> Unit) {
    var wantedInDays by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ir_ask_again)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ir_ask_again_blurb), style = MaterialTheme.typography.bodyMedium)
                WantedOnChips(wantedInDays) { wantedInDays = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAsk(wantedInDays?.let(::daysFromToday)) }) { Text(stringResource(R.string.ir_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun daysFromToday(days: Int): Long =
    LocalDate.now().plusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun inspectionKindLabel(kind: Inspections.Kind): Int = when (kind) {
    Inspections.Kind.REINFORCEMENT -> R.string.ir_kind_reinforcement
    Inspections.Kind.FORMWORK -> R.string.ir_kind_formwork
    Inspections.Kind.WATERPROOFING -> R.string.ir_kind_waterproofing
    Inspections.Kind.PIPE_PRESSURE -> R.string.ir_kind_pipe_pressure
    Inspections.Kind.ELECTRICAL -> R.string.ir_kind_electrical
    Inspections.Kind.OTHER -> R.string.ir_kind_other
}

@StringRes
private fun resultLabel(result: Inspections.Result): Int = when (result) {
    Inspections.Result.PASSED -> R.string.ir_result_passed
    Inspections.Result.PASSED_WITH_COMMENTS -> R.string.ir_result_comments
    Inspections.Result.FAILED -> R.string.ir_result_failed
}

@StringRes
internal fun inspectionRefusalText(refusal: InspectionRepository.Refusal): Int = when (refusal) {
    InspectionRepository.Refusal.NOT_ALLOWED -> R.string.ir_refused_not_allowed
    InspectionRepository.Refusal.BLANK_ELEMENT -> R.string.ir_refused_element
    InspectionRepository.Refusal.NOBODY_ASKED -> R.string.ir_refused_nobody
    InspectionRepository.Refusal.WANTED_TOO_EARLY -> R.string.ir_refused_date
    InspectionRepository.Refusal.ALREADY_DECIDED -> R.string.ir_refused_decided
    InspectionRepository.Refusal.NO_INSPECTOR -> R.string.ir_refused_inspector
    InspectionRepository.Refusal.FAILED_WITHOUT_REASON -> R.string.ir_refused_reason
    InspectionRepository.Refusal.COMMENTS_MISSING -> R.string.ir_refused_comments
    InspectionRepository.Refusal.NOT_FAILED -> R.string.ir_refused_not_failed
    InspectionRepository.Refusal.ALREADY_REINSPECTED -> R.string.ir_refused_reinspected
    InspectionRepository.Refusal.NOT_PASSED -> R.string.ir_refused_not_passed
    InspectionRepository.Refusal.NOT_FOR_A_POUR -> R.string.ir_refused_not_pour
    InspectionRepository.Refusal.ALREADY_CLEARED_A_POUR -> R.string.ir_refused_cleared
    InspectionRepository.Refusal.OTHER_JOB -> R.string.ir_refused_job
    InspectionRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** "Wanted on" offers, in days from today. */
private val WANTED_IN = listOf(
    0 to R.string.ir_today,
    1 to R.string.ir_tomorrow,
    2 to R.string.ir_in_2_days,
)

private const val SEPARATOR = " · "
