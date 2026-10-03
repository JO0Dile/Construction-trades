package il.co.tradesmanager.ui.meetings

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
import il.co.tradesmanager.core.work.Meetings
import il.co.tradesmanager.data.repository.MeetingRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.LocalDate
import java.util.Locale

/**
 * One job's meetings. The points still open come first, overdue ones in
 * red, because what the next meeting starts with is what the last one
 * agreed and nobody has done; the meetings themselves follow, newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: MeetingsViewModel =
        viewModel(factory = ViewModelFactory(container) { MeetingsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val register by viewModel.register.collectAsStateWithLifecycle()
    val openMeeting by viewModel.openMeeting.collectAsStateWithLifecycle()
    val openPoint by viewModel.openPoint.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var recording by remember { mutableStateOf(false) }
    var raisingFor by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }

    val stillOpen = register.points.filter { it.state != Meetings.ActionState.DONE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mt_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Every point, open and closed, with the meeting that raised it: the action log.
                    if (register.meetings.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.MeetingActionLog(
                                        jobName = jobName,
                                        meetings = register.meetings.map { it.meeting },
                                        actions = register.points.map { it.action },
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
                FloatingActionButton(onClick = { recording = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.mt_record))
                }
            }
        },
    ) { padding ->
        if (register.meetings.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.mt_empty),
                hint = stringResource(R.string.mt_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            item {
                SectionHeader(pluralStringResource(R.plurals.mt_points_open, stillOpen.size, stillOpen.size))
            }
            items(stillOpen, key = { "p-" + it.action.id }) { point ->
                PointRow(point, locale, onClick = { viewModel.selectPoint(point.action.id) })
            }
            item { SectionHeader(stringResource(R.string.mt_meetings)) }
            items(register.meetings, key = { "m-" + it.meeting.id }) { minutes ->
                ListItem(
                    overlineContent = {
                        Text(
                            minutes.meeting.reference + SEPARATOR +
                                stringResource(meetingKindLabel(minutes.kind)) + SEPARATOR +
                                Formats.date(minutes.heldOn, locale),
                        )
                    },
                    headlineContent = { Text(minutes.meeting.attendees ?: stringResource(R.string.mt_no_attendees), maxLines = 2) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.mt_points_of_meeting, minutes.points.size, minutes.points.size, minutes.stillOpen))
                    },
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectMeeting(minutes.meeting.id) },
                )
            }
        }
    }

    openMeeting?.let { minutes ->
        MeetingDialog(
            minutes = minutes,
            locale = locale,
            mayWrite = mayWrite,
            onDismiss = { viewModel.selectMeeting(null) },
            onRaise = { raisingFor = minutes.meeting.id },
            onOpenPoint = { viewModel.selectPoint(it) },
        )
    }

    openPoint?.let { point ->
        PointDialog(
            point = point,
            locale = locale,
            mayClose = mayWrite && point.state != Meetings.ActionState.DONE,
            onDismiss = { viewModel.selectPoint(null) },
            onClose = { closing = true },
        )
    }

    if (recording) {
        RecordDialog(
            onDismiss = { recording = false },
            onRecord = { kind, heldOn, attendees, notes ->
                recording = false
                viewModel.record(kind, heldOn, attendees, notes)
            },
        )
    }

    raisingFor?.let { meetingId ->
        RaiseDialog(
            onDismiss = { raisingFor = null },
            onRaise = { text, owner, dueOn ->
                raisingFor = null
                viewModel.raise(meetingId, text, owner, dueOn)
            },
        )
    }

    val closingPoint = openPoint
    if (closing && closingPoint != null) {
        CloseDialog(
            reference = closingPoint.action.reference,
            onDismiss = { closing = false },
            onClose = { note ->
                closing = false
                viewModel.close(closingPoint.action.id, note)
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(meetingRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun PointRow(point: MeetingsViewModel.Point, locale: Locale, onClick: () -> Unit) {
    val action = point.action
    ListItem(
        overlineContent = { Text(listOfNotNull(action.reference, action.ownerName).joinToString(SEPARATOR)) },
        headlineContent = { Text(action.text, maxLines = 3) },
        supportingContent = {
            Text(
                pointStateLine(point, locale),
                color = pointColour(point.state),
                fontWeight = if (point.state == Meetings.ActionState.OVERDUE) FontWeight.Bold else FontWeight.Normal,
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun pointColour(state: Meetings.ActionState): Color = when (state) {
    Meetings.ActionState.OVERDUE -> MaterialTheme.colorScheme.error
    Meetings.ActionState.OPEN -> MaterialTheme.colorScheme.onSurfaceVariant
    Meetings.ActionState.DONE -> MaterialTheme.colorScheme.primary
}

@Composable
private fun pointStateLine(point: MeetingsViewModel.Point, locale: Locale): String = when (point.state) {
    Meetings.ActionState.OVERDUE -> stringResource(R.string.mt_overdue, Formats.date(point.dueOn ?: LocalDate.now(), locale))
    Meetings.ActionState.OPEN ->
        point.dueOn?.let { stringResource(R.string.mt_due, Formats.date(it, locale)) } ?: stringResource(R.string.mt_no_date)
    Meetings.ActionState.DONE -> stringResource(R.string.mt_done_on, asDay(point.action.closedAt ?: point.action.raisedAt, locale))
}

@Composable
private fun MeetingDialog(
    minutes: MeetingsViewModel.Minutes,
    locale: Locale,
    mayWrite: Boolean,
    onDismiss: () -> Unit,
    onRaise: () -> Unit,
    onOpenPoint: (String) -> Unit,
) {
    val meeting = minutes.meeting
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meeting.reference + SEPARATOR + stringResource(meetingKindLabel(minutes.kind))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    Formats.date(minutes.heldOn, locale) + SEPARATOR + stringResource(R.string.mt_minuted_by, meeting.recordedByName),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.mt_attendees), style = MaterialTheme.typography.labelLarge)
                Text(meeting.attendees ?: stringResource(R.string.mt_no_attendees), style = MaterialTheme.typography.bodyMedium)
                meeting.notes?.let {
                    Text(stringResource(R.string.mt_notes), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.mt_points), style = MaterialTheme.typography.labelLarge)
                if (minutes.points.isEmpty()) {
                    Text(stringResource(R.string.mt_no_points), style = MaterialTheme.typography.bodySmall)
                }
                minutes.points.forEach { point ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenPoint(point.action.id) }.padding(vertical = 4.dp)) {
                        Text(point.action.reference + SEPARATOR + point.action.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(point.action.ownerName, pointStateLine(point, locale)).joinToString(SEPARATOR),
                            style = MaterialTheme.typography.bodySmall,
                            color = pointColour(point.state),
                        )
                    }
                }
                if (mayWrite) {
                    OutlinedButton(onClick = onRaise, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mt_raise))
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
private fun PointDialog(
    point: MeetingsViewModel.Point,
    locale: Locale,
    mayClose: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    val action = point.action
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.reference) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(action.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(action.ownerName, pointStateLine(point, locale)).joinToString(SEPARATOR),
                    color = pointColour(point.state),
                    fontWeight = FontWeight.Bold,
                )
                if (action.closedAt != null) {
                    Text(stringResource(R.string.mt_what_was_done), style = MaterialTheme.typography.labelLarge)
                    Text(action.closingNote.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    action.closedByName?.let { Text(stringResource(R.string.mt_closed_by, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (mayClose) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.mt_close_point)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayClose) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
}

/** A meeting as minuted: what kind, which day, who was there, anything said. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDialog(
    onDismiss: () -> Unit,
    onRecord: (kind: Meetings.Kind, heldOn: LocalDate, attendees: String, notes: String) -> Unit,
) {
    var kind by remember { mutableStateOf(Meetings.Kind.COORDINATION) }
    var daysAgo by remember { mutableStateOf(0L) }
    var attendees by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mt_record)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.mt_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Meetings.Kind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(stringResource(meetingKindLabel(option))) })
                    }
                }
                Text(stringResource(R.string.mt_held), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HELD_OFFERS.forEach { (days, label) ->
                        FilterChip(selected = daysAgo == days, onClick = { daysAgo = days }, label = { Text(stringResource(label)) })
                    }
                }
                OutlinedTextField(
                    value = attendees,
                    onValueChange = { attendees = it },
                    label = { Text(stringResource(R.string.mt_attendees)) },
                    supportingText = { Text(stringResource(R.string.mt_attendees_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.mt_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRecord(kind, LocalDate.now().minusDays(daysAgo), attendees, notes) }) {
                Text(stringResource(R.string.mt_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A point agreed: what, who, and by when if a day was set. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseDialog(
    onDismiss: () -> Unit,
    onRaise: (text: String, owner: String, dueOn: LocalDate?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var dueInDays by remember { mutableStateOf<Long?>(7L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mt_raise)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.mt_point_what)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text(stringResource(R.string.mt_point_who)) },
                    supportingText = { Text(stringResource(R.string.mt_point_who_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.mt_point_when), style = MaterialTheme.typography.labelLarge)
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
            ) { Text(stringResource(R.string.mt_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CloseDialog(
    reference: String,
    onDismiss: () -> Unit,
    onClose: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mt_close_question, reference)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.mt_what_was_done)) },
                supportingText = { Text(stringResource(R.string.mt_what_was_done_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = note.isNotBlank(), onClick = { onClose(note) }) { Text(stringResource(R.string.mt_close_point)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun asDay(at: Long, locale: Locale): String =
    Formats.date(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun meetingKindLabel(kind: Meetings.Kind): Int = when (kind) {
    Meetings.Kind.COORDINATION -> R.string.mt_kind_coordination
    Meetings.Kind.PROGRESS -> R.string.mt_kind_progress
    Meetings.Kind.SAFETY -> R.string.mt_kind_safety
    Meetings.Kind.DESIGN -> R.string.mt_kind_design
    Meetings.Kind.OTHER -> R.string.mt_kind_other
}

@StringRes
private fun meetingRefusalText(refusal: MeetingRepository.Refusal): Int = when (refusal) {
    MeetingRepository.Refusal.NOT_ALLOWED -> R.string.mt_refused_not_allowed
    MeetingRepository.Refusal.HELD_IN_FUTURE -> R.string.mt_refused_future
    MeetingRepository.Refusal.BLANK_ACTION -> R.string.mt_refused_blank
    MeetingRepository.Refusal.DUE_BEFORE_MEETING -> R.string.mt_refused_due
    MeetingRepository.Refusal.BLANK_CLOSING_NOTE -> R.string.mt_refused_note
    MeetingRepository.Refusal.ALREADY_CLOSED -> R.string.mt_refused_closed
    MeetingRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** When it was held, in days back: minutes are usually written the same day or the next. */
private val HELD_OFFERS = listOf(
    0L to R.string.mt_today,
    1L to R.string.de_yesterday,
    2L to R.string.de_2_days_ago,
    7L to R.string.mt_a_week_ago,
)

/** By when, in days ahead; null is "no date set". */
private val DUE_OFFERS = listOf(
    3L to R.string.mt_in_3_days,
    7L to R.string.mt_in_a_week,
    14L to R.string.mt_in_2_weeks,
    null to R.string.mt_no_date,
)

private const val SEPARATOR = " · "
