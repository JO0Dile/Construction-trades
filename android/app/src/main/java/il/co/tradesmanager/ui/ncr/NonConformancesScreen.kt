package il.co.tradesmanager.ui.ncr

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
import androidx.compose.material.icons.filled.IosShare
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
import il.co.tradesmanager.core.evidence.NonConformances
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.NonConformanceEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.NonConformanceRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * One job's non-conformance register.
 *
 * Overdue first and in red, then those nobody has decided about, because an
 * NCR left undecided is the one that is still open at the handover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonConformancesScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: NonConformancesViewModel =
        viewModel(factory = ViewModelFactory(container) { NonConformancesViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val openPhotos by viewModel.openPhotos.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var raising by remember { mutableStateOf(false) }
    var deciding by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val addPhoto = rememberImageAdder(
        newCameraTarget = { viewModel.newPhotoTarget() },
        onCaptured = { viewModel.photoCaptured(it) },
        onPicked = { viewModel.photoPicked(it) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ncr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (rows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.NonConformanceRegister(
                                        jobName = jobName,
                                        reports = rows.map { it.report },
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
        floatingActionButton = {
            if (mayWrite) {
                FloatingActionButton(onClick = { raising = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ncr_raise))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ncr_empty),
                hint = stringResource(R.string.ncr_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.report.id }) { row ->
                val report = row.report
                ListItem(
                    overlineContent = { Text(report.reference + SEPARATOR + report.element) },
                    headlineContent = { Text(report.finding, maxLines = 3) },
                    supportingContent = {
                        Column {
                            Text(report.requirement, style = MaterialTheme.typography.bodySmall)
                            Text(
                                stateText(row.state, report, locale),
                                color = stateColour(row.state),
                                fontWeight = if (row.state == NonConformances.State.OVERDUE) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openReport(report.id) },
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
            onDismiss = { viewModel.openReport(null) },
            onDecide = { deciding = true },
            onClose = { closing = true },
            onAddPhoto = addPhoto,
            onViewPhoto = { viewing = it },
        )
    }

    if (raising) {
        RaiseDialog(
            onDismiss = { raising = false },
            onRaise = { element, requirement, finding, foundBy ->
                raising = false
                viewModel.raise(element, requirement, finding, foundBy)
            },
        )
    }

    val decidingRow = open
    if (deciding && decidingRow != null) {
        DecideDialog(
            reference = decidingRow.report.reference,
            onDismiss = { deciding = false },
            onDecide = { disposition, acceptedBy, correction, dueInDays ->
                deciding = false
                viewModel.decide(decidingRow.report.id, disposition, acceptedBy, correction, dueInDays)
            },
        )
    }

    val closingRow = open
    if (closing && closingRow != null) {
        CloseDialog(
            reference = closingRow.report.reference,
            onDismiss = { closing = false },
            onClose = { verification ->
                closing = false
                viewModel.close(closingRow.report.id, verification)
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" belongs to a job's own plan, not to one report.
            isPlan = true,
            onSetAsPlan = {},
            onDelete = {
                viewModel.deletePhoto(photo)
                viewing = null
            }.takeIf { mayWrite && open?.report?.closedAt == null },
            onDismiss = { viewing = null },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(ncrRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailDialog(
    row: NonConformancesViewModel.Row,
    photos: List<PhotoEntity>,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onDecide: () -> Unit,
    onClose: () -> Unit,
    onAddPhoto: () -> Unit,
    onViewPhoto: (PhotoEntity) -> Unit,
) {
    val report = row.report
    val disposition = NonConformances.dispositionOf(report.disposition)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(report.reference + SEPARATOR + report.element) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stateText(row.state, report, locale), color = stateColour(row.state), fontWeight = FontWeight.Bold)
                Labelled(R.string.ncr_requirement, report.requirement)
                Labelled(R.string.ncr_finding, report.finding)
                Labelled(R.string.ncr_found_by, stringResource(foundByLabel(NonConformances.foundByOf(report.foundBy))))
                Text(
                    stringResource(R.string.ncr_raised_by, report.raisedByName, dateOf(report.raisedAt, locale)),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { photo ->
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = stringResource(R.string.ncr_finding),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clickable { onViewPhoto(photo) },
                        )
                    }
                }
                if (mayWrite && report.closedAt == null) {
                    OutlinedButton(onClick = onAddPhoto) { Text(stringResource(R.string.ncr_add_photo)) }
                }
                if (disposition != null) {
                    Labelled(R.string.ncr_decision, stringResource(dispositionLabel(disposition)))
                    report.acceptedBy?.let { Labelled(R.string.ncr_accepted_by, it) }
                    report.correction?.let { Labelled(R.string.ncr_correction, it) }
                    Text(
                        stringResource(R.string.ncr_decided_by, report.decidedByName.orEmpty(), dateOf(report.decidedAt ?: report.raisedAt, locale)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                report.closedAt?.let { closedAt ->
                    Text(
                        stringResource(R.string.ncr_closed_by, report.closedByName.orEmpty(), dateOf(closedAt, locale), report.verification.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            when {
                mayWrite && report.decidedAt == null -> TextButton(onClick = onDecide) { Text(stringResource(R.string.ncr_decide)) }
                mayWrite && report.closedAt == null -> TextButton(onClick = onClose) { Text(stringResource(R.string.ncr_close)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = if (mayWrite && report.closedAt == null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
        } else {
            null
        },
    )
}

@Composable
private fun Labelled(@StringRes label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseDialog(
    onDismiss: () -> Unit,
    onRaise: (element: String, requirement: String, finding: String, foundBy: NonConformances.FoundBy) -> Unit,
) {
    var element by remember { mutableStateOf("") }
    var requirement by remember { mutableStateOf("") }
    var finding by remember { mutableStateOf("") }
    var foundBy by remember { mutableStateOf(NonConformances.FoundBy.OWN_QUALITY_CONTROL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ncr_raise)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = element,
                    onValueChange = { element = it },
                    label = { Text(stringResource(R.string.ncr_element)) },
                    supportingText = { Text(stringResource(R.string.ncr_element_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = requirement,
                    onValueChange = { requirement = it },
                    label = { Text(stringResource(R.string.ncr_requirement)) },
                    supportingText = { Text(stringResource(R.string.ncr_requirement_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = finding,
                    onValueChange = { finding = it },
                    label = { Text(stringResource(R.string.ncr_finding)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ncr_found_by), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NonConformances.FoundBy.entries.forEach { option ->
                        FilterChip(selected = foundBy == option, onClick = { foundBy = option }, label = { Text(stringResource(foundByLabel(option))) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = element.isNotBlank() && requirement.isNotBlank() && finding.isNotBlank(),
                onClick = { onRaise(element, requirement, finding, foundBy) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecideDialog(
    reference: String,
    onDismiss: () -> Unit,
    onDecide: (NonConformances.Disposition, String, String, Long?) -> Unit,
) {
    var disposition by remember { mutableStateOf(NonConformances.Disposition.REPAIR) }
    var acceptedBy by remember { mutableStateOf("") }
    var correction by remember { mutableStateOf("") }
    var dueInDays by remember { mutableStateOf<Long?>(7L) }
    val keeping = disposition == NonConformances.Disposition.ACCEPT_AS_IS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ncr_decide) + SEPARATOR + reference) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NonConformances.Disposition.entries.forEach { option ->
                        FilterChip(selected = disposition == option, onClick = { disposition = option }, label = { Text(stringResource(dispositionLabel(option))) })
                    }
                }
                if (keeping) {
                    OutlinedTextField(
                        value = acceptedBy,
                        onValueChange = { acceptedBy = it },
                        label = { Text(stringResource(R.string.ncr_accepted_by)) },
                        supportingText = { Text(stringResource(R.string.ncr_accepted_by_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = correction,
                        onValueChange = { correction = it },
                        label = { Text(stringResource(R.string.ncr_correction)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.iv_action_when), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DUE_OFFERS.forEach { (days, label) ->
                            FilterChip(selected = dueInDays == days, onClick = { dueInDays = days }, label = { Text(stringResource(label)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = if (keeping) acceptedBy.isNotBlank() else correction.isNotBlank(),
                onClick = { onDecide(disposition, acceptedBy, correction, dueInDays.takeUnless { keeping }) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CloseDialog(reference: String, onDismiss: () -> Unit, onClose: (String) -> Unit) {
    var verification by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ncr_close) + SEPARATOR + reference) },
        text = {
            OutlinedTextField(
                value = verification,
                onValueChange = { verification = it },
                label = { Text(stringResource(R.string.ncr_verification)) },
                supportingText = { Text(stringResource(R.string.ncr_verification_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = verification.isNotBlank(), onClick = { onClose(verification) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun stateText(state: NonConformances.State, report: NonConformanceEntity, locale: Locale): String {
    val dueOn = report.dueOnDay?.let(LocalDate::ofEpochDay)
    return when (state) {
        NonConformances.State.OVERDUE -> stringResource(R.string.ncr_state_overdue, Formats.date(dueOn ?: LocalDate.now(), locale))
        NonConformances.State.AWAITING_DECISION -> stringResource(R.string.ncr_state_awaiting, dateOf(report.raisedAt, locale))
        NonConformances.State.IN_HAND ->
            dueOn?.let { stringResource(R.string.ncr_state_in_hand, Formats.date(it, locale)) } ?: stringResource(R.string.ncr_state_in_hand_no_date)
        NonConformances.State.CLOSED -> stringResource(R.string.ncr_state_closed, dateOf(report.closedAt ?: report.raisedAt, locale))
    }
}

@Composable
private fun stateColour(state: NonConformances.State): Color = when (state) {
    NonConformances.State.OVERDUE, NonConformances.State.AWAITING_DECISION -> MaterialTheme.colorScheme.error
    NonConformances.State.IN_HAND -> MaterialTheme.colorScheme.onSurfaceVariant
    NonConformances.State.CLOSED -> MaterialTheme.colorScheme.primary
}

internal fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun foundByLabel(foundBy: NonConformances.FoundBy): Int = when (foundBy) {
    NonConformances.FoundBy.OWN_QUALITY_CONTROL -> R.string.ncr_by_own_qc
    NonConformances.FoundBy.SUPERVISOR -> R.string.ncr_by_supervisor
    NonConformances.FoundBy.CLIENT -> R.string.ncr_by_client
    NonConformances.FoundBy.AUTHORITY -> R.string.ncr_by_authority
    NonConformances.FoundBy.LABORATORY -> R.string.ncr_by_laboratory
}

@StringRes
internal fun dispositionLabel(disposition: NonConformances.Disposition): Int = when (disposition) {
    NonConformances.Disposition.REWORK -> R.string.ncr_disp_rework
    NonConformances.Disposition.REPAIR -> R.string.ncr_disp_repair
    NonConformances.Disposition.ACCEPT_AS_IS -> R.string.ncr_disp_accept_as_is
    NonConformances.Disposition.REMOVE -> R.string.ncr_disp_remove
}

/** Exhaustive with no `else`, so the next refusal somebody adds cannot become a blank line. */
@StringRes
private fun ncrRefusalText(refusal: NonConformanceRepository.Refusal): Int = when (refusal) {
    NonConformanceRepository.Refusal.NOT_ALLOWED -> R.string.iv_refused_not_allowed
    NonConformanceRepository.Refusal.BLANK_ELEMENT -> R.string.ncr_refused_element
    NonConformanceRepository.Refusal.BLANK_REQUIREMENT -> R.string.ncr_refused_requirement
    NonConformanceRepository.Refusal.BLANK_FINDING -> R.string.ncr_refused_finding
    NonConformanceRepository.Refusal.NO_ACCEPTOR -> R.string.ncr_refused_acceptor
    NonConformanceRepository.Refusal.BLANK_CORRECTION -> R.string.ncr_refused_correction
    NonConformanceRepository.Refusal.DUE_BEFORE_RAISED -> R.string.ncr_refused_due
    NonConformanceRepository.Refusal.ALREADY_DECIDED -> R.string.ncr_refused_decided
    NonConformanceRepository.Refusal.NOT_DECIDED -> R.string.ncr_refused_not_decided
    NonConformanceRepository.Refusal.BLANK_VERIFICATION -> R.string.ncr_refused_verification
    NonConformanceRepository.Refusal.ALREADY_CLOSED -> R.string.ncr_refused_closed
    NonConformanceRepository.Refusal.UNKNOWN -> R.string.iv_refused_unknown
}

/** By when, in days ahead; null is "no date set". */
private val DUE_OFFERS = listOf(
    3L to R.string.mt_in_3_days,
    7L to R.string.mt_in_a_week,
    14L to R.string.mt_in_2_weeks,
    null to R.string.mt_no_date,
)

private const val SEPARATOR = " · "
