package il.co.tradesmanager.ui.submittals

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
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import il.co.tradesmanager.data.repository.SubmittalRepository
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
 * One job's material submittals.
 *
 * Overdue and rejected ones first and in red: the reason anybody opens this
 * is to find out whether the tile can be ordered yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmittalsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: SubmittalsViewModel =
        viewModel(factory = ViewModelFactory(container) { SubmittalsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val openPhotos by viewModel.openPhotos.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()

    var submitting by remember { mutableStateOf(false) }
    var deciding by remember { mutableStateOf(false) }
    var resubmitting by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val addSheet = rememberImageAdder(
        newCameraTarget = { viewModel.newSheetTarget() },
        onCaptured = { viewModel.sheetCaptured(it) },
        onPicked = { viewModel.sheetPicked(it) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ms_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { submitting = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ms_submit))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ms_empty),
                hint = stringResource(R.string.ms_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.submittal.id }) { row ->
                val submittal = row.submittal
                val alarming = row.state == Submittals.State.OVERDUE || row.state == Submittals.State.REJECTED
                ListItem(
                    overlineContent = { Text(referenceLabel(submittal) + SEPARATOR + submittal.submittedTo) },
                    headlineContent = { Text(submittal.item, maxLines = 3) },
                    supportingContent = {
                        Text(
                            stateLine(row, locale),
                            color = stateColour(row.state),
                            fontWeight = if (alarming) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openSubmittal(submittal.id) },
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
            onDismiss = { viewModel.openSubmittal(null) },
            onDecide = { deciding = true },
            onResubmit = { resubmitting = true },
            onAddSheet = addSheet,
            onViewPhoto = { viewing = it },
        )
    }

    if (submitting) {
        SubmitDialog(
            onDismiss = { submitting = false },
            onSubmit = { item, supplier, location, submittedTo, neededBy ->
                submitting = false
                viewModel.submit(item, supplier, location, submittedTo, neededBy)
            },
        )
    }

    val decidingRow = open
    if (deciding && decidingRow != null) {
        DecideDialog(
            onDismiss = { deciding = false },
            onDecide = { decision, reviewer, notes ->
                deciding = false
                viewModel.decide(decidingRow.submittal.id, decision, reviewer, notes)
            },
        )
    }

    val rejectedRow = open
    if (resubmitting && rejectedRow != null) {
        ResubmitDialog(
            previousItem = rejectedRow.submittal.item,
            onDismiss = { resubmitting = false },
            onResubmit = { item, neededBy ->
                resubmitting = false
                viewModel.resubmit(rejectedRow.submittal.id, item, neededBy)
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" belongs to a job's own plan, not to a datasheet.
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
            text = { Text(stringResource(submittalRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** "MS-004", or "MS-004 rev. 1" from the second go on. */
@Composable
private fun referenceLabel(submittal: SubmittalEntity): String =
    if (submittal.revision == 0) {
        submittal.reference
    } else {
        stringResource(R.string.ms_revision_of, submittal.reference, submittal.revision)
    }

@Composable
private fun stateColour(state: Submittals.State): Color = when (state) {
    Submittals.State.OVERDUE, Submittals.State.REJECTED -> MaterialTheme.colorScheme.error
    Submittals.State.APPROVED -> MaterialTheme.colorScheme.primary
    Submittals.State.WAITING, Submittals.State.SUPERSEDED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun stateLine(row: SubmittalsViewModel.Row, locale: Locale): String {
    val submittal = row.submittal
    val decided = submittal.decidedAt ?: submittal.submittedAt
    val reviewer = submittal.reviewerName.orEmpty()
    return when (row.state) {
        Submittals.State.OVERDUE ->
            stringResource(R.string.ms_overdue, dateOf(submittal.neededBy ?: submittal.submittedAt, locale))
        Submittals.State.REJECTED -> stringResource(R.string.ms_rejected_on, dateOf(decided, locale), reviewer)
        Submittals.State.SUPERSEDED -> stringResource(R.string.ms_superseded, row.followedBy ?: submittal.revision + 1)
        Submittals.State.APPROVED -> stringResource(R.string.ms_approved_on, dateOf(decided, locale), reviewer)
        Submittals.State.WAITING -> submittal.neededBy?.let { stringResource(R.string.ms_needed, dateOf(it, locale)) }
            ?: stringResource(R.string.ms_sent_on, dateOf(submittal.submittedAt, locale))
    }
}

/** One revision in full: what was proposed, what was said, the datasheet, and what can be done next. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailDialog(
    row: SubmittalsViewModel.Row,
    photos: List<PhotoEntity>,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onDecide: () -> Unit,
    onResubmit: () -> Unit,
    onAddSheet: () -> Unit,
    onViewPhoto: (PhotoEntity) -> Unit,
) {
    val submittal = row.submittal
    val decision = Submittals.decisionOf(submittal.decision)
    val mayDecide = mayWrite && submittal.decidedAt == null
    val mayResubmit = mayWrite && row.state == Submittals.State.REJECTED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(referenceLabel(submittal)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(submittal.item, style = MaterialTheme.typography.bodyLarge)
                listOfNotNull(submittal.supplier, submittal.location).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    listOfNotNull(
                        submittal.submittedTo,
                        stringResource(R.string.ms_sent_on, dateOf(submittal.submittedAt, locale)),
                        submittal.submittedByName,
                        submittal.neededBy?.let { stringResource(R.string.ms_needed, dateOf(it, locale)) },
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (submittal.revision > 0) {
                    Text(
                        stringResource(R.string.ms_follows, submittal.revision - 1),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (decision != null) {
                    Text(stringResource(R.string.ms_decision), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(decisionLabel(decision)),
                        color = stateColour(row.state),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(
                            submittal.reviewerName,
                            submittal.decidedAt?.let { dateOf(it, locale) },
                            submittal.decisionRecordedByName?.let { stringResource(R.string.ms_recorded_by, it) },
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    submittal.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                row.followedBy?.let {
                    Text(stringResource(R.string.ms_superseded, it), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.ms_sheet), style = MaterialTheme.typography.labelLarge)
                if (photos.isEmpty()) {
                    Text(stringResource(R.string.ms_no_sheet), style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = stringResource(R.string.ms_sheet),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clickable { onViewPhoto(photo) },
                            )
                        }
                    }
                }
                if (mayWrite) {
                    OutlinedButton(onClick = onAddSheet, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ms_add_sheet))
                    }
                }
            }
        },
        confirmButton = {
            when {
                mayDecide -> TextButton(onClick = onDecide) { Text(stringResource(R.string.ms_record_decision)) }
                mayResubmit -> TextButton(onClick = onResubmit) { Text(stringResource(R.string.ms_send_again)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayDecide || mayResubmit) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubmitDialog(
    onDismiss: () -> Unit,
    onSubmit: (item: String, supplier: String, location: String, submittedTo: String, neededBy: Long?) -> Unit,
) {
    var item by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var submittedTo by remember { mutableStateOf("") }
    var neededInDays by remember { mutableStateOf<Int?>(null) }
    val usual = listOf(R.string.ms_to_architect, R.string.ms_to_supervisor, R.string.ms_to_engineer, R.string.ms_to_client)
        .map { stringResource(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ms_submit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text(stringResource(R.string.ms_item)) },
                    supportingText = { Text(stringResource(R.string.ms_item_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(stringResource(R.string.ms_supplier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.ms_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ms_submitted_to), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    usual.forEach { who ->
                        FilterChip(selected = submittedTo == who, onClick = { submittedTo = who }, label = { Text(who) })
                    }
                }
                OutlinedTextField(
                    value = submittedTo,
                    onValueChange = { submittedTo = it },
                    label = { Text(stringResource(R.string.ms_submitted_to_typed)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NeededByChips(neededInDays) { neededInDays = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = item.isNotBlank() && submittedTo.isNotBlank(),
                onClick = { onSubmit(item, supplier, location, submittedTo, neededInDays?.let(::daysFromToday)) },
            ) { Text(stringResource(R.string.ms_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NeededByChips(selected: Int?, onSelect: (Int?) -> Unit) {
    Text(stringResource(R.string.ms_needed_by), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.ms_no_date)) },
        )
        NEEDED_IN.forEach { (days, label) ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** What the reviewer said. The name is the person's, not their title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecideDialog(
    onDismiss: () -> Unit,
    onDecide: (decision: Submittals.Decision, reviewer: String, notes: String) -> Unit,
) {
    var decision by remember { mutableStateOf<Submittals.Decision?>(null) }
    var reviewer by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val needsNotes = decision == Submittals.Decision.REJECTED || decision == Submittals.Decision.APPROVED_AS_NOTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ms_record_decision)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Submittals.Decision.entries.forEach { option ->
                        FilterChip(
                            selected = decision == option,
                            onClick = { decision = option },
                            label = { Text(stringResource(decisionLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = reviewer,
                    onValueChange = { reviewer = it },
                    label = { Text(stringResource(R.string.ms_reviewer)) },
                    supportingText = { Text(stringResource(R.string.ms_reviewer_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = {
                        Text(
                            stringResource(
                                if (decision == Submittals.Decision.REJECTED) R.string.ms_why_rejected else R.string.ms_notes,
                            ),
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val chosen = decision
            TextButton(
                enabled = chosen != null && reviewer.isNotBlank() && (!needsNotes || notes.isNotBlank()),
                onClick = { if (chosen != null) onDecide(chosen, reviewer, notes) },
            ) { Text(stringResource(R.string.ms_record)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ResubmitDialog(
    previousItem: String,
    onDismiss: () -> Unit,
    onResubmit: (item: String, neededBy: Long?) -> Unit,
) {
    var item by remember { mutableStateOf(previousItem) }
    var neededInDays by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ms_send_again)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ms_send_again_blurb), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    label = { Text(stringResource(R.string.ms_item)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                NeededByChips(neededInDays) { neededInDays = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = item.isNotBlank(),
                onClick = { onResubmit(item, neededInDays?.let(::daysFromToday)) },
            ) { Text(stringResource(R.string.ms_send)) }
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
private fun decisionLabel(decision: Submittals.Decision): Int = when (decision) {
    Submittals.Decision.APPROVED -> R.string.ms_decision_approved
    Submittals.Decision.APPROVED_AS_NOTED -> R.string.ms_decision_noted
    Submittals.Decision.REJECTED -> R.string.ms_decision_rejected
}

@StringRes
private fun submittalRefusalText(refusal: SubmittalRepository.Refusal): Int = when (refusal) {
    SubmittalRepository.Refusal.NOT_ALLOWED -> R.string.ms_refused_not_allowed
    SubmittalRepository.Refusal.BLANK_ITEM -> R.string.ms_refused_item
    SubmittalRepository.Refusal.NOBODY_ASKED -> R.string.ms_refused_nobody
    SubmittalRepository.Refusal.NEEDED_BY_TOO_EARLY -> R.string.ms_refused_date
    SubmittalRepository.Refusal.ALREADY_DECIDED -> R.string.ms_refused_decided
    SubmittalRepository.Refusal.NO_REVIEWER -> R.string.ms_refused_reviewer
    SubmittalRepository.Refusal.REJECTED_WITHOUT_REASON -> R.string.ms_refused_reason
    SubmittalRepository.Refusal.NOTES_MISSING -> R.string.ms_refused_notes
    SubmittalRepository.Refusal.NOT_REJECTED -> R.string.ms_refused_not_rejected
    SubmittalRepository.Refusal.ALREADY_RESUBMITTED -> R.string.ms_refused_resubmitted
    SubmittalRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** "Needed by" offers, in days from today: an order has a lead time. */
private val NEEDED_IN = listOf(
    3 to R.string.ms_in_3_days,
    7 to R.string.ms_in_a_week,
    14 to R.string.ms_in_2_weeks,
)

private const val SEPARATOR = " · "
