package il.co.tradesmanager.ui.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.core.safety.Investigations
import il.co.tradesmanager.data.local.entity.IncidentActionEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.InvestigationRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * One incident and its investigation: the report as filed, what directly
 * caused it, what lay behind it, what was found, and the corrective actions
 * with who does them and by when. Printed, it is the page a client's safety
 * manager or an insurer asks for after an accident.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentDetailScreen(
    container: AppContainer,
    incidentId: String,
    onBack: () -> Unit,
) {
    val viewModel: IncidentDetailViewModel =
        viewModel(factory = ViewModelFactory(container) { IncidentDetailViewModel(it, incidentId) })
    val incident by viewModel.incident.collectAsStateWithLifecycle()
    val investigation by viewModel.investigation.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val zone = ZoneId.systemDefault()
    var editing by remember { mutableStateOf(false) }
    var raising by remember { mutableStateOf(false) }
    var closingAction by remember { mutableStateOf<IncidentActionEntity?>(null) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.iv_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val current = incident
                    if (current != null) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.IncidentInvestigationReport(
                                        incident = current,
                                        jobName = jobName,
                                        investigation = investigation,
                                        actions = actions,
                                        today = LocalDate.now(),
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
    ) { padding ->
        val current = incident
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current == null) {
                Text(stringResource(R.string.iv_gone), style = MaterialTheme.typography.bodyMedium)
            } else {
                val severity = Incidents.parse(current.severity)
                val at = Instant.ofEpochMilli(current.occurredAt).atZone(zone)
                Text(
                    stringResource(severityLabel(severity)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (Incidents.needsEscalating(severity)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(current.description, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.iv_reported, current.reportedByName, Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(jobName ?: stringResource(R.string.inc_no_job), style = MaterialTheme.typography.bodyMedium)
                if (evidence.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        evidence.forEach { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = stringResource(R.string.iv_evidence),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewing = photo },
                            )
                        }
                    }
                }

                HorizontalDivider()
                val record = investigation
                val state = Investigations.state(severity, record != null, record?.closedAt != null)
                Text(stringResource(R.string.iv_investigation), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(investigationStateLabel(state)),
                    color = if (state == Investigations.State.NEEDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (Investigations.required(severity) && record?.closedAt == null) {
                    Text(stringResource(R.string.iv_required_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (record == null) {
                    if (canEdit) {
                        Button(onClick = { editing = true }) { Text(stringResource(R.string.iv_start)) }
                    }
                } else {
                    Labelled(R.string.iv_immediate_cause, record.immediateCause)
                    Labelled(
                        R.string.iv_causes,
                        Investigations.decode(record.causes).map { stringResource(causeLabel(it)) }.joinToString(" · ").ifBlank { null },
                    )
                    Labelled(R.string.iv_findings, record.findings)
                    Text(
                        stringResource(R.string.iv_started_by, record.startedByName, day(record.startedAt, zone, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    record.closedAt?.let { closedAt ->
                        Text(
                            stringResource(R.string.iv_closed_by, record.closedByName.orEmpty(), day(closedAt, zone, locale)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (canEdit && record.closedAt == null) {
                        OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.iv_edit)) }
                    }

                    HorizontalDivider()
                    Text(stringResource(R.string.iv_actions), style = MaterialTheme.typography.titleMedium)
                    if (actions.isEmpty()) {
                        Text(stringResource(R.string.iv_no_actions), style = MaterialTheme.typography.bodyMedium)
                    }
                    actions.forEach { action ->
                        ActionCard(
                            action = action,
                            locale = locale,
                            zone = zone,
                            onClose = { closingAction = action }.takeIf { canEdit && action.closedAt == null },
                        )
                    }
                    if (canEdit && record.closedAt == null) {
                        OutlinedButton(onClick = { raising = true }) { Text(stringResource(R.string.iv_add_action)) }
                        Button(onClick = { viewModel.close() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.iv_close))
                        }
                        Text(stringResource(R.string.iv_close_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            refusal?.let {
                Text(stringResource(refusalText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (editing) {
        val record = investigation
        FindingsDialog(
            immediateCause = record?.immediateCause.orEmpty(),
            causes = Investigations.decode(record?.causes),
            findings = record?.findings.orEmpty(),
            onDismiss = { editing = false },
            onSave = { cause, causes, findings ->
                viewModel.save(cause, causes, findings)
                editing = false
            },
        )
    }
    if (raising) {
        ActionDialog(
            onDismiss = { raising = false },
            onRaise = { text, owner, dueOn ->
                viewModel.raise(text, owner, dueOn)
                raising = false
            },
        )
    }
    closingAction?.let { action ->
        CloseActionDialog(
            number = action.number,
            onDismiss = { closingAction = null },
            onClose = { note ->
                viewModel.closeAction(action.id, note)
                closingAction = null
            },
        )
    }
    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // An incident has no plan, and its evidence is never taken off.
            isPlan = true,
            onSetAsPlan = {},
            onDelete = null,
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun Labelled(label: Int, value: String?) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: stringResource(R.string.iv_not_written), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ActionCard(action: IncidentActionEntity, locale: Locale, zone: ZoneId, onClose: (() -> Unit)?) {
    val dueOn = action.dueOnDay?.let(LocalDate::ofEpochDay)
    val state = Investigations.actionState(dueOn, action.closedAt != null, LocalDate.now())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.iv_action_line, action.number, action.text), style = MaterialTheme.typography.bodyLarge)
            action.ownerName?.let { Text(stringResource(R.string.iv_action_owner, it), style = MaterialTheme.typography.bodySmall) }
            Text(
                actionStateText(action, state, locale, zone),
                style = MaterialTheme.typography.bodySmall,
                color = if (state == Investigations.ActionState.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onClose != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.iv_close_action)) }
                }
            }
        }
    }
}

@Composable
private fun actionStateText(action: IncidentActionEntity, state: Investigations.ActionState, locale: Locale, zone: ZoneId): String {
    val dueOn = action.dueOnDay?.let(LocalDate::ofEpochDay)
    return when (state) {
        Investigations.ActionState.DONE -> stringResource(
            R.string.iv_action_done,
            day(action.closedAt ?: 0L, zone, locale),
            action.closedByName.orEmpty(),
            action.closingNote.orEmpty(),
        )
        Investigations.ActionState.OVERDUE -> stringResource(R.string.iv_action_overdue, Formats.date(dueOn ?: LocalDate.now(), locale))
        Investigations.ActionState.OPEN ->
            dueOn?.let { stringResource(R.string.iv_action_due, Formats.date(it, locale)) } ?: stringResource(R.string.mt_no_date)
    }
}

/** What caused it, what lay behind it, what was found. Each may wait; closing asks for the first two. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingsDialog(
    immediateCause: String,
    causes: Set<Investigations.Cause>,
    findings: String,
    onDismiss: () -> Unit,
    onSave: (String, Set<Investigations.Cause>, String) -> Unit,
) {
    var cause by remember { mutableStateOf(immediateCause) }
    var chosen by remember { mutableStateOf(causes) }
    var found by remember { mutableStateOf(findings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.iv_investigation)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = cause,
                    onValueChange = { cause = it },
                    label = { Text(stringResource(R.string.iv_immediate_cause)) },
                    supportingText = { Text(stringResource(R.string.iv_immediate_cause_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.iv_causes), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Investigations.Cause.entries.forEach { option ->
                        FilterChip(
                            selected = option in chosen,
                            onClick = { chosen = if (option in chosen) chosen - option else chosen + option },
                            label = { Text(stringResource(causeLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = found,
                    onValueChange = { found = it },
                    label = { Text(stringResource(R.string.iv_findings)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(cause, chosen, found) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Something to be done: what, who, and by when if a day was set. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionDialog(
    onDismiss: () -> Unit,
    onRaise: (text: String, owner: String, dueOn: LocalDate?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var dueInDays by remember { mutableStateOf<Long?>(7L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.iv_add_action)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.iv_action_what)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text(stringResource(R.string.iv_action_who)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.iv_action_when), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DUE_OFFERS.forEach { (days, label) ->
                        FilterChip(selected = dueInDays == days, onClick = { dueInDays = days }, label = { Text(stringResource(label)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onRaise(text, owner, dueInDays?.let { LocalDate.now().plusDays(it) }) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CloseActionDialog(number: Int, onDismiss: () -> Unit, onClose: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.iv_close_action_title, number)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.mt_what_was_done)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = note.isNotBlank(), onClick = { onClose(note) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun day(at: Long, zone: ZoneId, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), locale)

internal fun investigationStateLabel(state: Investigations.State): Int = when (state) {
    Investigations.State.NEEDED -> R.string.iv_state_needed
    Investigations.State.OPEN -> R.string.iv_state_open
    Investigations.State.CLOSED -> R.string.iv_state_closed
    Investigations.State.NOT_STARTED -> R.string.iv_state_not_started
}

internal fun causeLabel(cause: Investigations.Cause): Int = when (cause) {
    Investigations.Cause.WAY_OF_WORKING -> R.string.iv_cause_way_of_working
    Investigations.Cause.EQUIPMENT -> R.string.iv_cause_equipment
    Investigations.Cause.SITE_CONDITIONS -> R.string.iv_cause_site_conditions
    Investigations.Cause.PROTECTIVE_EQUIPMENT -> R.string.iv_cause_protective_equipment
    Investigations.Cause.SUPERVISION -> R.string.iv_cause_supervision
    Investigations.Cause.TRAINING -> R.string.iv_cause_training
    Investigations.Cause.PLANNING -> R.string.iv_cause_planning
    Investigations.Cause.COMMUNICATION -> R.string.iv_cause_communication
    Investigations.Cause.OTHER -> R.string.iv_cause_other
}

/** Exhaustive with no `else`, so the next refusal somebody adds cannot become a blank line. */
private fun refusalText(refusal: InvestigationRepository.Refusal): Int = when (refusal) {
    InvestigationRepository.Refusal.NOT_ALLOWED -> R.string.iv_refused_not_allowed
    InvestigationRepository.Refusal.NOT_STARTED -> R.string.iv_refused_not_started
    InvestigationRepository.Refusal.BLANK_IMMEDIATE_CAUSE -> R.string.iv_refused_immediate_cause
    InvestigationRepository.Refusal.NO_CAUSE -> R.string.iv_refused_no_cause
    InvestigationRepository.Refusal.ACTIONS_OPEN -> R.string.iv_refused_actions_open
    InvestigationRepository.Refusal.ALREADY_CLOSED -> R.string.iv_refused_closed
    InvestigationRepository.Refusal.BLANK_ACTION -> R.string.iv_refused_blank_action
    InvestigationRepository.Refusal.DUE_BEFORE_INCIDENT -> R.string.iv_refused_due
    InvestigationRepository.Refusal.BLANK_CLOSING_NOTE -> R.string.iv_refused_note
    InvestigationRepository.Refusal.ACTION_ALREADY_CLOSED -> R.string.iv_refused_action_closed
    InvestigationRepository.Refusal.UNKNOWN -> R.string.iv_refused_unknown
}

/** By when, in days ahead; null is "no date set". */
private val DUE_OFFERS = listOf(
    3L to R.string.mt_in_3_days,
    7L to R.string.mt_in_a_week,
    14L to R.string.mt_in_2_weeks,
    null to R.string.mt_no_date,
)
