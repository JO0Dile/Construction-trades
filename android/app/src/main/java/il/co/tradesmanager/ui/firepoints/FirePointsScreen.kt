package il.co.tradesmanager.ui.firepoints

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.FirePoints
import il.co.tradesmanager.data.repository.FirePointRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PickDate
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * One job's fire points, in walk-round order once the ones needing
 * somebody are out of the way: a fault first and in red, then a service
 * date passed, then a look overdue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirePointsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: FirePointsViewModel =
        viewModel(factory = ViewModelFactory(container) { FirePointsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var adding by remember { mutableStateOf(false) }
    var recordingFault by remember { mutableStateOf(false) }
    var pickingService by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fp_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The register as a document: the sheet for the site office wall and the fire inspector.
                    if (rows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.FirePointRegister(
                                        jobName = jobName,
                                        points = rows.map { it.point },
                                        checks = rows.flatMap { it.looks },
                                    ),
                                    languageTag = languageTag,
                                    locale = locale,
                                    rightToLeft = layoutDirection == LayoutDirection.Rtl,
                                )
                                context.startActivity(Exporter.shareIntent(context, result))
                            },
                        ) {
                            Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.set_export))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fp_add))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.fp_empty),
                hint = stringResource(R.string.fp_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.point.id }) { row ->
                val point = row.point
                ListItem(
                    overlineContent = {
                        Text(
                            listOfNotNull(point.reference, stringResource(firePointKindLabel(row.kind)), point.tagNumber)
                                .joinToString(SEPARATOR),
                        )
                    },
                    headlineContent = { Text(point.location, maxLines = 2) },
                    supportingContent = {
                        Text(
                            stateLine(row, locale),
                            color = stateColour(row.state),
                            fontWeight = if (FirePoints.needsAttention(row.state)) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openPoint(point.id) },
                )
            }
        }
    }

    open?.let { row ->
        DetailDialog(
            row = row,
            locale = locale,
            mayWrite = mayWrite,
            onDismiss = { viewModel.openPoint(null) },
            onFine = { viewModel.check(row.point.id, ok = true, note = "") },
            onFault = { recordingFault = true },
            onServiced = { pickingService = true },
            onRemove = { removing = true },
        )
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onAdd = { kind, location, tag, serviceDueOn ->
                adding = false
                viewModel.add(kind, location, tag, serviceDueOn)
            },
        )
    }

    val faultRow = open
    if (recordingFault && faultRow != null) {
        FaultDialog(
            onDismiss = { recordingFault = false },
            onRecord = { note ->
                recordingFault = false
                viewModel.check(faultRow.point.id, ok = false, note = note)
            },
        )
    }

    val serviceRow = open
    if (pickingService && serviceRow != null) {
        PickDate(
            initial = pickerMillis(LocalDate.now().plusYears(1)),
            title = stringResource(R.string.fp_next_service_question),
            onDismiss = { pickingService = false },
            onPick = { millis ->
                pickingService = false
                if (millis != null) viewModel.serviced(serviceRow.point.id, FirePointsViewModel.dayOf(millis))
            },
        )
    }

    val removingRow = open
    if (removing && removingRow != null) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.fp_remove_question, removingRow.point.reference)) },
            text = { Text(stringResource(R.string.fp_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        viewModel.remove(removingRow.point.id)
                    },
                ) { Text(stringResource(R.string.fp_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(firePointRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun stateColour(state: FirePoints.State): Color = when (state) {
    FirePoints.State.FAULT, FirePoints.State.SERVICE_OVERDUE, FirePoints.State.CHECK_OVERDUE, FirePoints.State.SERVICE_DUE_SOON ->
        MaterialTheme.colorScheme.error
    FirePoints.State.READY -> MaterialTheme.colorScheme.primary
    FirePoints.State.REMOVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun stateLine(row: FirePointsViewModel.Row, locale: Locale): String {
    val latest = row.latest
    return when (row.state) {
        FirePoints.State.FAULT -> stringResource(R.string.fp_state_fault, latest?.note.orEmpty())
        FirePoints.State.SERVICE_OVERDUE -> stringResource(R.string.fp_state_service_overdue, dayText(row.serviceDueOn ?: LocalDate.now(), locale))
        FirePoints.State.CHECK_OVERDUE ->
            if (latest == null) {
                stringResource(R.string.fp_state_never_checked)
            } else {
                stringResource(R.string.fp_state_check_overdue, dateOf(latest.checkedAt, locale))
            }
        FirePoints.State.SERVICE_DUE_SOON -> stringResource(R.string.fp_state_service_soon, dayText(row.serviceDueOn ?: LocalDate.now(), locale))
        FirePoints.State.READY -> stringResource(R.string.fp_state_ready, dateOf(latest?.checkedAt ?: row.point.addedAt, locale))
        FirePoints.State.REMOVED -> stringResource(R.string.fp_state_removed, dateOf(row.point.removedAt ?: row.point.addedAt, locale))
    }
}

@Composable
private fun DetailDialog(
    row: FirePointsViewModel.Row,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onFine: () -> Unit,
    onFault: () -> Unit,
    onServiced: () -> Unit,
    onRemove: () -> Unit,
) {
    val point = row.point
    val inPlace = point.removedAt == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(point.reference + SEPARATOR + stringResource(firePointKindLabel(row.kind))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(point.location, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        point.tagNumber?.let { stringResource(R.string.fp_tag_is, it) },
                        row.serviceDueOn?.let {
                            // A fault is the headline; a run-out service is still said here.
                            if (inPlace && it.isBefore(LocalDate.now())) {
                                stringResource(R.string.fp_state_service_overdue, dayText(it, locale))
                            } else {
                                stringResource(R.string.fp_service_due, dayText(it, locale))
                            }
                        } ?: stringResource(R.string.fp_no_service_date),
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stateLine(row, locale), color = stateColour(row.state), fontWeight = FontWeight.Bold)
                if (mayWrite && inPlace) {
                    Button(onClick = onFine, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fp_looked_fine))
                    }
                    OutlinedButton(onClick = onFault, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fp_found_fault))
                    }
                }
                Text(stringResource(R.string.fp_looks), style = MaterialTheme.typography.labelLarge)
                if (row.looks.isEmpty()) {
                    Text(stringResource(R.string.fp_state_never_checked), style = MaterialTheme.typography.bodySmall)
                } else {
                    row.looks.take(LOOKS_SHOWN).forEach { look ->
                        Text(
                            listOfNotNull(
                                dateOf(look.checkedAt, locale),
                                look.checkedByName,
                                stringResource(if (look.ok) R.string.fp_fine else R.string.fp_fault),
                                look.note,
                            ).joinToString(SEPARATOR),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (look.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    if (row.looks.size > LOOKS_SHOWN) {
                        Text(
                            pluralStringResource(R.plurals.fp_looks_more, row.looks.size - LOOKS_SHOWN, row.looks.size - LOOKS_SHOWN),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        stringResource(R.string.fp_added_by, dateOf(point.addedAt, locale), point.addedByName),
                        point.removedAt?.let { stringResource(R.string.fp_state_removed, dateOf(it, locale)) },
                        point.removedByName,
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (mayWrite && inPlace) {
                    OutlinedButton(onClick = onServiced, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fp_serviced))
                    }
                    OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fp_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun FaultDialog(
    onDismiss: () -> Unit,
    onRecord: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fp_found_fault)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.fp_fault_what)) },
                supportingText = { Text(stringResource(R.string.fp_fault_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = note.isNotBlank(), onClick = { onRecord(note) }) { Text(stringResource(R.string.fp_record_fault)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A fire point going up: what it is and where are asked for, the tag and label date if they are there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onAdd: (kind: FirePoints.Kind, location: String, tag: String, serviceDueOn: LocalDate?) -> Unit,
) {
    var kind by remember { mutableStateOf(FirePoints.Kind.POWDER) }
    var location by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var serviceDueOn by remember { mutableStateOf<LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val locale = currentLocale()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fp_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.fp_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FirePoints.Kind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(firePointKindLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.fp_location)) },
                    supportingText = { Text(stringResource(R.string.fp_location_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text(stringResource(R.string.fp_tag)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.fp_service_label), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        serviceDueOn?.let { stringResource(R.string.fp_service_due, dayText(it, locale)) }
                            ?: stringResource(R.string.fp_service_pick),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = location.isNotBlank(),
                onClick = { onAdd(kind, location, tag, serviceDueOn) },
            ) { Text(stringResource(R.string.fp_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (pickingDate) {
        PickDate(
            initial = serviceDueOn?.let { pickerMillis(it) },
            title = stringResource(R.string.fp_service_question),
            onDismiss = { pickingDate = false },
            onPick = { millis ->
                pickingDate = false
                serviceDueOn = millis?.let { FirePointsViewModel.dayOf(it) }
            },
        )
    }
}

/** The calendar works in midnight UTC, whatever the phone's zone. */
private fun pickerMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun dayText(day: LocalDate, locale: Locale): String = Formats.date(day, locale)

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun firePointKindLabel(kind: FirePoints.Kind): Int = when (kind) {
    FirePoints.Kind.POWDER -> R.string.fp_kind_powder
    FirePoints.Kind.CO2 -> R.string.fp_kind_co2
    FirePoints.Kind.FOAM -> R.string.fp_kind_foam
    FirePoints.Kind.WATER -> R.string.fp_kind_water
    FirePoints.Kind.BLANKET -> R.string.fp_kind_blanket
    FirePoints.Kind.HOSE_REEL -> R.string.fp_kind_hose_reel
}

@StringRes
private fun firePointRefusalText(refusal: FirePointRepository.Refusal): Int = when (refusal) {
    FirePointRepository.Refusal.NOT_ALLOWED -> R.string.fp_refused_not_allowed
    FirePointRepository.Refusal.BLANK_LOCATION -> R.string.fp_refused_location
    FirePointRepository.Refusal.SERVICE_DUE_IN_PAST -> R.string.fp_refused_service_past
    FirePointRepository.Refusal.FAULT_NEEDS_NOTE -> R.string.fp_refused_fault_note
    FirePointRepository.Refusal.ALREADY_REMOVED -> R.string.fp_refused_removed
    FirePointRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** The newest looks in the dialog; the export carries all of them. */
private const val LOOKS_SHOWN = 6

private const val SEPARATOR = " · "
