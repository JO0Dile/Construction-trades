package il.co.tradesmanager.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.safety.Muster
import il.co.tradesmanager.data.local.entity.MusterEntity
import il.co.tradesmanager.data.repository.MusterRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * The roll call.
 *
 * Two screens in one, because they are never both wanted: a roll call is
 * running, or it is not. When one is running nothing else is on the page —
 * no history, no explanation, no settings — because somebody is holding this
 * phone in a car park with an alarm going and the only thing that matters is
 * the list of names and which of them are missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusterScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: MusterViewModel =
        viewModel(factory = ViewModelFactory(container) { MusterViewModel(it) })
    val live by viewModel.live.collectAsStateWithLifecycle()
    val roll by viewModel.roll.collectAsStateWithLifecycle()
    val onSite by viewModel.onSite.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val jobNames by viewModel.jobNames.collectAsStateWithLifecycle()
    val mayRun by viewModel.mayRun.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val refusalText = refusal?.let { stringResource(sentence(it)) }
    LaunchedEffect(refusalText) {
        if (refusalText != null) {
            snackbar.showSnackbar(refusalText)
            viewModel.clearRefusal()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.muster_title)) },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = roll
        if (live != null && current != null) {
            LiveRollCall(
                roll = current,
                onPresent = viewModel::present,
                onElsewhere = viewModel::elsewhere,
                onStillMissing = viewModel::stillMissing,
                onAdd = viewModel::add,
                onEnd = viewModel::end,
                modifier = Modifier.padding(padding),
            )
        } else {
            Waiting(
                onSite = onSite,
                mayRun = mayRun,
                history = history,
                jobNames = jobNames,
                onStart = viewModel::start,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/* ------------------------------------------------------- nothing running */

@Composable
private fun Waiting(
    onSite: Int,
    mayRun: Boolean,
    history: List<MusterEntity>,
    jobNames: Map<String, String>,
    onStart: (Muster.Reason, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var note by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(Muster.Reason.EMERGENCY) }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingAll) {
        item {
            Text(
                stringResource(R.string.muster_blurb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (mayRun) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            // The one number somebody wants before starting:
                            // how many names the list is about to hold.
                            stringResource(R.string.muster_on_site, onSite),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (onSite == 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.muster_nobody_checked_in),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Muster.Reason.entries.forEach { option ->
                                FilterChip(
                                    selected = reason == option,
                                    onClick = { reason = option },
                                    label = { Text(stringResource(reasonLabel(option))) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(R.string.muster_note_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onStart(reason, note)
                                note = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null)
                            Text(
                                stringResource(R.string.muster_start),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        item {
            Text(
                stringResource(R.string.muster_history),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (history.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.muster_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(history, key = { it.id }) { past ->
                PastRollCall(past, past.projectId?.let(jobNames::get))
            }
        }
    }
}

@Composable
private fun PastRollCall(past: MusterEntity, jobName: String?) {
    val missing = past.unaccountedAtEnd ?: 0
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = if (missing > 0) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    // A roll call still running has no count yet, which is not
                    // the same as a count of nobody.
                    past.endedAt == null -> stringResource(R.string.muster_live)
                    missing > 0 -> stringResource(R.string.muster_record_missing, missing)
                    else -> stringResource(R.string.muster_record_clear)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    stringResource(reasonLabel(readReason(past.reason))),
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(past.startedAt)),
                    // Absent when the people on site did not agree on one job,
                    // which is a real answer rather than a missing one.
                    jobName,
                    past.note?.takeIf { it.isNotBlank() },
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/* ---------------------------------------------------------- one running */

@Composable
private fun LiveRollCall(
    roll: Muster.Roll,
    onPresent: (String) -> Unit,
    onElsewhere: (String, String) -> Unit,
    onStillMissing: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The clock the header counts on. A roll call is measured in minutes and
    // somebody is watching it, so it ticks rather than freezing at whatever
    // the screen was composed with.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    var accountingFor by remember { mutableStateOf<Muster.Person?>(null) }
    var adding by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingAll) {
        item {
            LiveHeader(roll, now)
            Spacer(Modifier.height(16.dp))
        }
        items(roll.ordered(), key = { it.id }) { person ->
            PersonRow(
                person = person,
                onPresent = { onPresent(person.id) },
                onElsewhere = { accountingFor = person },
                onStillMissing = { onStillMissing(person.id) },
            )
            HorizontalDivider()
        }
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.muster_add_person))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.muster_add_person_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { ending = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.muster_end))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    accountingFor?.let { person ->
        AccountDialog(
            person = person,
            onDismiss = { accountingFor = null },
            onConfirm = { account ->
                onElsewhere(person.id, account)
                accountingFor = null
            },
        )
    }

    if (adding) {
        NameDialog(
            onDismiss = { adding = false },
            onConfirm = { name ->
                onAdd(name)
                adding = false
            },
        )
    }

    if (ending) {
        EndDialog(
            missing = roll.unaccounted().map { it.name },
            onDismiss = { ending = false },
            onConfirm = {
                onEnd()
                ending = false
            },
        )
    }
}

@Composable
private fun LiveHeader(roll: Muster.Roll, now: Long) {
    val missing = roll.unaccountedCount
    Card(
        Modifier.fillMaxWidth(),
        colors = if (missing > 0) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (missing > 0) {
                    stringResource(R.string.muster_unaccounted, missing)
                } else {
                    stringResource(R.string.muster_all_accounted)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(
                    stringResource(R.string.muster_live),
                    stringResource(R.string.muster_minutes, roll.minutesRunning(now).toInt()),
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (roll.isLongRunning(now)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.muster_taking_long),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: Muster.Person,
    onPresent: () -> Unit,
    onElsewhere: () -> Unit,
    onStillMissing: () -> Unit,
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                person.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (person.state.isAccountedFor) FontWeight.Normal else FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        val badges = listOfNotNull(
            if (person.staleCheckIn) stringResource(R.string.muster_stale) else null,
            if (person.addedDuringRollCall) stringResource(R.string.muster_added_badge) else null,
            person.account?.takeIf { it.isNotBlank() },
        )
        if (badges.isNotEmpty()) {
            Text(
                badges.joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (person.state.isAccountedFor) {
                // The only way back. A row gets ticked by mistake, and the
                // alternative to an undo is starting the roll call again.
                AssistChip(
                    onClick = onStillMissing,
                    label = { Text(stringResource(R.string.muster_still_missing)) },
                )
                Text(
                    stringResource(stateLabel(person.state)),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                FilledTonalButton(onClick = onPresent) {
                    Text(stringResource(R.string.muster_present))
                }
                OutlinedButton(onClick = onElsewhere) {
                    Text(stringResource(R.string.muster_elsewhere))
                }
            }
        }
    }
}

/* ------------------------------------------------------------- dialogues */

@Composable
private fun AccountDialog(
    person: Muster.Person,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var account by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person.name) },
        text = {
            Column {
                Text(stringResource(R.string.muster_account_question))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    placeholder = { Text(stringResource(R.string.muster_account_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(account) },
                // Refused by the rules anyway; greyed out here so the refusal
                // is not the first the person hears of it.
                enabled = account.isNotBlank(),
            ) {
                Text(stringResource(R.string.muster_elsewhere))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.muster_add_person)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.muster_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Ending it.
 *
 * With everybody found this is a plain confirmation. With names still missing
 * it reads them back, because the record is about to say those people were not
 * accounted for and nobody should be able to say afterwards that they did not
 * see it.
 */
@Composable
private fun EndDialog(
    missing: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (missing.isEmpty()) {
                    stringResource(R.string.muster_end)
                } else {
                    stringResource(R.string.muster_end_with_missing_title)
                },
            )
        },
        text = {
            if (missing.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.muster_end_with_missing_body,
                        missing.joinToString(SEPARATOR),
                    ),
                )
            } else {
                Text(stringResource(R.string.muster_all_accounted))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.muster_end)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/* ------------------------------------------------------------- the words */

private fun reasonLabel(reason: Muster.Reason): Int = when (reason) {
    Muster.Reason.DRILL -> R.string.muster_reason_drill
    Muster.Reason.EMERGENCY -> R.string.muster_reason_emergency
}

private fun stateLabel(state: Muster.State): Int = when (state) {
    Muster.State.UNACCOUNTED -> R.string.muster_still_missing
    Muster.State.PRESENT -> R.string.muster_present
    Muster.State.ACCOUNTED_ELSEWHERE -> R.string.muster_elsewhere
}

/** A stored reason nobody recognises reads as the serious one, not the drill. */
private fun readReason(stored: String): Muster.Reason =
    runCatching { Muster.Reason.valueOf(stored) }.getOrDefault(Muster.Reason.EMERGENCY)

private fun sentence(refusal: MusterRepository.Refusal): Int = when (refusal) {
    MusterRepository.Refusal.NOT_ALLOWED -> R.string.muster_not_allowed
    MusterRepository.Refusal.ALREADY_RUNNING -> R.string.muster_already_running
    MusterRepository.Refusal.UNKNOWN -> R.string.muster_refused_generic
    MusterRepository.Refusal.ALREADY_ENDED -> R.string.muster_already_running
    MusterRepository.Refusal.NO_ACCOUNT_GIVEN -> R.string.muster_account_required
    MusterRepository.Refusal.BLANK_NAME -> R.string.muster_refused_generic
}

private val PaddingAll = PaddingValues(16.dp)

private const val SEPARATOR = " · "
