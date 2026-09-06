package il.co.tradesmanager.ui.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Permits
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.LoadingState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One permit: its precautions, and the two signatures that bracket it.
 *
 * The order of the page is the order of the job. The precautions come first
 * because they are what has to be true before anything else happens; issuing
 * comes second and is refused until they are all ticked; signing back comes
 * last and stays available after the permit has run out, because the work
 * stopping and the area being checked are two different events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitDetailScreen(
    container: AppContainer,
    permitId: String,
    onBack: () -> Unit,
) {
    val viewModel: PermitDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { PermitDetailViewModel(it, permitId) },
    )
    val permit by viewModel.permit.collectAsStateWithLifecycle()
    val precautions by viewModel.precautions.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val now = rememberNow()
    val locale = currentLocale()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var issuing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    val current = permit
    val state = current?.let { Permits.state(it.status, it.validFrom, it.validTo, now) }
    val checked = precautions.count { it.checked }
    val canIssue = current != null && canEdit && Permits.canIssue(
        status = current.status,
        precautionsTotal = precautions.size,
        precautionsChecked = checked,
        // The window is chosen in the dialog; here we only ask whether the rest
        // of the permit is ready, so a plausible window stands in for one.
        validFrom = now,
        validTo = now + 1,
    )

    // The fire watch, on the kinds of work that leave something behind. Ticks
    // with the clock, so a permit held open on screen becomes closable on its
    // own rather than needing somebody to back out and come in again.
    val watchMinutesLeft = if (current != null && Permits.needsFireWatch(current.type)) {
        Permits.fireWatchMinutesLeft(current.workStoppedAt, current.validTo, now)
    } else {
        0L
    }
    val canCloseNow = current != null && canEdit && Permits.canClose(
        status = current.status,
        type = current.type,
        workStoppedAt = current.workStoppedAt,
        validTo = current.validTo,
        now = now,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.reference ?: stringResource(R.string.ptw_title)) },
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
        if (current == null || state == null) {
            LoadingState(
                message = stringResource(R.string.ptw_title),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(permitTypeLabel(current.type)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(current.description, style = MaterialTheme.typography.headlineSmall)
                    PermitStateChip(state = state, validTo = current.validTo, now = now)
                }
            }

            if (state == Permits.State.EXPIRED) {
                item { PermitExpiredBanner() }
            }

            item { PermitFacts(permit = current, locale = locale) }

            item {
                SectionHeader(
                    stringResource(R.string.ptw_precautions) +
                        " · " + checked + "/" + precautions.size,
                )
            }
            items(precautions, key = { it.id }) { precaution ->
                ListItem(
                    leadingContent = {
                        Checkbox(
                            checked = precaution.checked,
                            // A permit that has been issued is a signed record.
                            // Un-ticking a precaution afterwards would rewrite
                            // what somebody put their name to.
                            enabled = canEdit && current.status == Permits.Status.DRAFT,
                            onCheckedChange = { viewModel.tick(precaution, it) },
                        )
                    },
                    headlineContent = { Text(precaution.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current.status == Permits.Status.DRAFT) {
                        if (!canIssue && canEdit) {
                            Text(
                                text = stringResource(R.string.ptw_issue_blocked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = { issuing = true },
                            enabled = canIssue,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ptw_issue))
                        }
                    }
                    if (current.status == Permits.Status.ISSUED && canEdit) {
                        // Two buttons where there used to be one. On hot work
                        // the hour between them is the fire watch, and the
                        // whole point is that they are not the same act.
                        if (current.workStoppedAt == null) {
                            Button(
                                onClick = { viewModel.recordWorkStopped() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.ptw_work_stopped))
                            }
                        }
                        if (watchMinutesLeft > 0L) {
                            Text(
                                text = stringResource(R.string.ptw_fire_watch) + " · " +
                                    pluralCount(R.plurals.ptw_watch_minutes, watchMinutesLeft),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.ptw_fire_watch_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { closing = true },
                            enabled = canCloseNow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ptw_close))
                        }
                        OutlinedButton(
                            onClick = { viewModel.cancel() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ptw_cancel))
                        }
                    }
                }
            }
        }
    }

    if (issuing) {
        IssuePermitDialog(
            now = now,
            onDismiss = { issuing = false },
            onIssue = { from, to ->
                issuing = false
                viewModel.issue(from, to)
            },
        )
    }

    if (closing) {
        ClosePermitDialog(
            onDismiss = { closing = false },
            onClose = { notes ->
                closing = false
                viewModel.close(notes)
            },
        )
    }
}

@Composable
private fun PermitExpiredBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.ptw_expired_note),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun PermitFacts(permit: PermitEntity, locale: java.util.Locale) {
    Column {
        DetailRow(stringResource(R.string.ptw_issued_to), permit.issuedToName)
        permit.location?.let { DetailRow(stringResource(R.string.ptw_location), it) }
        permit.issuedByName?.let { DetailRow(stringResource(R.string.ptw_issued_by), it) }
        permit.validFrom?.let {
            DetailRow(stringResource(R.string.ptw_valid_from), formatMoment(it, locale))
        }
        permit.validTo?.let {
            DetailRow(stringResource(R.string.ptw_valid_to), formatMoment(it, locale))
        }
        permit.closedByName?.let { DetailRow(stringResource(R.string.ptw_close), it) }
        permit.closeNotes?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

private fun formatMoment(millis: Long, locale: java.util.Locale): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return Formats.dateTime(zoned.toLocalDate(), zoned.toLocalTime(), locale)
}

/**
 * How long the permit runs for.
 *
 * Hours from now rather than two date-and-time pickers, because that is how a
 * permit is actually issued: at the start of a shift, for that shift. The
 * "tomorrow morning" option exists because permits genuinely do get written up
 * the evening before, and a permit that is live overnight when nobody is on
 * site is the wrong answer to that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssuePermitDialog(
    now: Long,
    onDismiss: () -> Unit,
    onIssue: (validFrom: Long, validTo: Long) -> Unit,
) {
    var hours by remember { mutableStateOf(8) }
    var startTomorrow by remember { mutableStateOf(false) }
    val hourMillis = 60L * 60L * 1000L
    val from = if (startTomorrow) tomorrowMorning(now) else now
    val to = from + hours * hourMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ptw_issue)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.ptw_valid_from),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !startTomorrow,
                        onClick = { startTomorrow = false },
                        label = { Text(stringResource(R.string.time_now)) },
                    )
                    FilterChip(
                        selected = startTomorrow,
                        onClick = { startTomorrow = true },
                        label = { Text(stringResource(R.string.time_tomorrow_morning)) },
                    )
                }
                Text(
                    text = stringResource(R.string.ptw_valid_to),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 8, 12).forEach { candidate ->
                        FilterChip(
                            selected = hours == candidate,
                            onClick = { hours = candidate },
                            label = { Text(pluralCount(R.plurals.ptw_hours, candidate.toLong())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.ptw_valid_to) + ": " +
                        formatMoment(to, currentLocale()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onIssue(from, to) }) {
                Text(stringResource(R.string.ptw_issue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Seven in the morning, local time, which is when an Israeli site starts. */
private fun tomorrowMorning(now: Long): Long {
    val zone = ZoneId.systemDefault()
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return today.plusDays(1).atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli()
}

@Composable
private fun ClosePermitDialog(onDismiss: () -> Unit, onClose: (String?) -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ptw_close)) },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.ptw_close_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onClose(notes.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.ptw_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
