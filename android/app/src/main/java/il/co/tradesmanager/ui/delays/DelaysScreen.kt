package il.co.tradesmanager.ui.delays

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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.work.Delays
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.DelayRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.PhotoViewer
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.LocalDate
import java.util.Locale

/**
 * One job's delay events.
 *
 * The tally sits on top because it is the first thing anybody preparing a
 * claim asks for, and the events without notice are counted in red because
 * they are the ones most likely to be lost.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DelaysScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: DelaysViewModel =
        viewModel(factory = ViewModelFactory(container) { DelaysViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val tally by viewModel.tally.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val openPhotos by viewModel.openPhotos.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var recording by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var noticing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val addPhoto = rememberImageAdder(
        newCameraTarget = { viewModel.newPhotoTarget() },
        onCaptured = { viewModel.photoCaptured(it) },
        onPicked = { viewModel.photoPicked(it) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.de_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The register as a document: a PDF to print or send, and a CSV.
                    if (rows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.DelayRegister(jobName = jobName, events = rows.map { it.event }, today = LocalDate.now()),
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
                FloatingActionButton(onClick = { recording = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.de_record))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.de_empty),
                hint = stringResource(R.string.de_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.de_events, tally.events, tally.events),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tally.daysByCause.entries.sortedByDescending { it.value }.forEach { (cause, days) ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        stringResource(delayCauseLabel(cause)) + SEPARATOR +
                                            pluralStringResource(R.plurals.de_days, days.toInt(), days.toInt()),
                                    )
                                },
                            )
                        }
                    }
                    Text(stringResource(R.string.de_overlap_note), style = MaterialTheme.typography.bodySmall)
                    if (tally.withoutNotice > 0) {
                        Text(
                            pluralStringResource(R.plurals.de_without_notice, tally.withoutNotice, tally.withoutNotice),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(rows, key = { it.event.id }) { row ->
                ListItem(
                    overlineContent = { Text(row.event.reference + SEPARATOR + stringResource(delayCauseLabel(row.cause))) },
                    headlineContent = { Text(row.event.description, maxLines = 3) },
                    supportingContent = {
                        Column {
                            Text(spanLine(row, locale))
                            if (!row.noticeGiven) {
                                Text(
                                    stringResource(R.string.de_no_notice),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openEvent(row.event.id) },
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
            onDismiss = { viewModel.openEvent(null) },
            onEnd = { ending = true },
            onNotice = { noticing = true },
            onAddPhoto = addPhoto,
            onViewPhoto = { viewing = it },
        )
    }

    if (recording) {
        RecordDialog(
            onDismiss = { recording = false },
            onRecord = { cause, description, affected, daysAgo, related ->
                recording = false
                viewModel.record(cause, description, affected, daysAgo, related)
            },
        )
    }

    val endingRow = open
    if (ending && endingRow != null) {
        DayDialog(
            title = R.string.de_ended_title,
            onDismiss = { ending = false },
            onPick = { daysAgo ->
                ending = false
                viewModel.end(endingRow.event.id, daysAgo)
            },
        )
    }

    val noticeRow = open
    if (noticing && noticeRow != null) {
        NoticeDialog(
            onDismiss = { noticing = false },
            onNotice = { to, daysAgo ->
                noticing = false
                viewModel.notice(noticeRow.event.id, to, daysAgo)
            },
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            // "Set as plan" belongs to a job's own plan, not to a delay.
            isPlan = true,
            onSetAsPlan = {},
            onDelete = {
                viewModel.deletePhoto(photo)
                viewing = null
            }.takeIf { mayWrite },
            onDismiss = { viewing = null },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(delayRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** "From 3 Oct, still going · 4 days", or "3 Oct to 5 Oct · 3 days". */
@Composable
private fun spanLine(row: DelaysViewModel.Row, locale: Locale): String {
    val span = row.endedOn?.let {
        stringResource(R.string.de_span, Formats.date(row.startedOn, locale), Formats.date(it, locale))
    } ?: stringResource(R.string.de_since, Formats.date(row.startedOn, locale))
    return span + SEPARATOR + pluralStringResource(R.plurals.de_days, row.days.toInt(), row.days.toInt())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailDialog(
    row: DelaysViewModel.Row,
    photos: List<PhotoEntity>,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
    onNotice: () -> Unit,
    onAddPhoto: () -> Unit,
    onViewPhoto: (PhotoEntity) -> Unit,
) {
    val event = row.event
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.reference + SEPARATOR + stringResource(delayCauseLabel(row.cause))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(event.description, style = MaterialTheme.typography.bodyLarge)
                Text(spanLine(row, locale), fontWeight = FontWeight.Bold)
                event.affectedWork?.let {
                    Text(stringResource(R.string.de_affected_is, it), style = MaterialTheme.typography.bodyMedium)
                }
                event.relatedReference?.let {
                    Text(stringResource(R.string.de_related_is, it), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.de_recorded_by, event.recordedByName), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.de_notice), style = MaterialTheme.typography.labelLarge)
                val notifiedOn = event.notifiedOnDay?.let(LocalDate::ofEpochDay)
                if (notifiedOn != null) {
                    Text(
                        stringResource(R.string.de_notified, event.notifiedTo.orEmpty(), Formats.date(notifiedOn, locale)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.de_no_notice),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (mayWrite) {
                        OutlinedButton(onClick = onNotice, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.de_record_notice))
                        }
                    }
                }
                Text(stringResource(R.string.de_photos), style = MaterialTheme.typography.labelLarge)
                if (photos.isEmpty()) {
                    Text(stringResource(R.string.de_no_photos), style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = stringResource(R.string.de_photos),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clickable { onViewPhoto(photo) },
                            )
                        }
                    }
                }
                if (mayWrite) {
                    OutlinedButton(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.de_add_photo))
                    }
                }
            }
        },
        confirmButton = {
            if (mayWrite && row.ongoing) {
                TextButton(onClick = onEnd) { Text(stringResource(R.string.de_end)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayWrite && row.ongoing) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDialog(
    onDismiss: () -> Unit,
    onRecord: (cause: Delays.Cause, description: String, affected: String, startedDaysAgo: Int, related: String) -> Unit,
) {
    var cause by remember { mutableStateOf(Delays.Cause.WEATHER) }
    var description by remember { mutableStateOf("") }
    var affected by remember { mutableStateOf("") }
    var related by remember { mutableStateOf("") }
    var daysAgo by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.de_record)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.de_cause), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Delays.Cause.entries.forEach { option ->
                        FilterChip(
                            selected = cause == option,
                            onClick = { cause = option },
                            label = { Text(stringResource(delayCauseLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.de_description)) },
                    supportingText = { Text(stringResource(R.string.de_description_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = affected,
                    onValueChange = { affected = it },
                    label = { Text(stringResource(R.string.de_affected)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = related,
                    onValueChange = { related = it },
                    label = { Text(stringResource(R.string.de_related)) },
                    supportingText = { Text(stringResource(R.string.de_related_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.de_started), style = MaterialTheme.typography.labelLarge)
                DaysAgoChips(daysAgo, STARTED_OFFERS) { daysAgo = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank(),
                onClick = { onRecord(cause, description, affected, daysAgo, related) },
            ) { Text(stringResource(R.string.de_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysAgoChips(selected: Int, offers: List<Pair<Int, Int>>, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        offers.forEach { (days, label) ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** A day, offered as today or a few days back: the day it ended is usually yesterday. */
@Composable
private fun DayDialog(@StringRes title: Int, onDismiss: () -> Unit, onPick: (daysAgo: Int) -> Unit) {
    var daysAgo by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { DaysAgoChips(daysAgo, ENDED_OFFERS) { daysAgo = it } },
        confirmButton = {
            TextButton(onClick = { onPick(daysAgo) }) { Text(stringResource(R.string.de_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeDialog(onDismiss: () -> Unit, onNotice: (to: String, daysAgo: Int) -> Unit) {
    var to by remember { mutableStateOf("") }
    var daysAgo by remember { mutableStateOf(0) }
    val usual = listOf(R.string.de_to_client, R.string.de_to_supervisor, R.string.de_to_manager).map { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.de_record_notice)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.de_notice_blurb), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    usual.forEach { who ->
                        FilterChip(selected = to == who, onClick = { to = who }, label = { Text(who) })
                    }
                }
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text(stringResource(R.string.de_notified_to)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.de_notice_on), style = MaterialTheme.typography.labelLarge)
                DaysAgoChips(daysAgo, ENDED_OFFERS) { daysAgo = it }
            }
        },
        confirmButton = {
            TextButton(enabled = to.isNotBlank(), onClick = { onNotice(to, daysAgo) }) {
                Text(stringResource(R.string.de_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@StringRes
internal fun delayCauseLabel(cause: Delays.Cause): Int = when (cause) {
    Delays.Cause.WEATHER -> R.string.de_cause_weather
    Delays.Cause.LATE_INFORMATION -> R.string.de_cause_information
    Delays.Cause.CLIENT_CHANGE -> R.string.de_cause_client
    Delays.Cause.NO_ACCESS -> R.string.de_cause_access
    Delays.Cause.UTILITIES -> R.string.de_cause_utilities
    Delays.Cause.AUTHORITY -> R.string.de_cause_authority
    Delays.Cause.SUPPLY -> R.string.de_cause_supply
    Delays.Cause.LABOUR -> R.string.de_cause_labour
    Delays.Cause.OTHER -> R.string.de_cause_other
}

@StringRes
private fun delayRefusalText(refusal: DelayRepository.Refusal): Int = when (refusal) {
    DelayRepository.Refusal.NOT_ALLOWED -> R.string.de_refused_not_allowed
    DelayRepository.Refusal.BLANK_DESCRIPTION -> R.string.de_refused_description
    DelayRepository.Refusal.STARTS_IN_FUTURE -> R.string.de_refused_future
    DelayRepository.Refusal.ENDS_BEFORE_START -> R.string.de_refused_ends_early
    DelayRepository.Refusal.ALREADY_ENDED -> R.string.de_refused_ended
    DelayRepository.Refusal.NOTICE_ALREADY_GIVEN -> R.string.de_refused_notice_given
    DelayRepository.Refusal.NOBODY_NOTIFIED -> R.string.de_refused_nobody
    DelayRepository.Refusal.NOTICE_BEFORE_START -> R.string.de_refused_notice_early
    DelayRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** When it started, in days back from today. */
private val STARTED_OFFERS = listOf(
    0 to R.string.de_today,
    1 to R.string.de_yesterday,
    2 to R.string.de_2_days_ago,
    3 to R.string.de_3_days_ago,
    7 to R.string.de_a_week_ago,
)

/** When it ended, or when notice went: recent, since both are written down soon after. */
private val ENDED_OFFERS = listOf(
    0 to R.string.de_today,
    1 to R.string.de_yesterday,
    2 to R.string.de_2_days_ago,
)

private const val SEPARATOR = " · "
