package il.co.tradesmanager.ui.safety

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Emergency
import il.co.tradesmanager.core.safety.Muster
import il.co.tradesmanager.data.local.entity.MusterEntity
import il.co.tradesmanager.data.repository.MusterRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val firstAiders by viewModel.firstAiders.collectAsStateWithLifecycle()

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
        val running = live
        if (running != null && current != null) {
            LiveRollCall(
                muster = running,
                roll = current,
                firstAiders = firstAiders,
                rollFor = viewModel::rollFor,
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
                rollFor = viewModel::rollFor,
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
    rollFor: suspend (MusterEntity) -> Muster.Roll,
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
            EmergencyNumbers()
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
                PastRollCall(past, past.projectId?.let(jobNames::get), rollFor)
            }
        }
    }
}

@Composable
private fun PastRollCall(
    past: MusterEntity,
    jobName: String?,
    rollFor: suspend (MusterEntity) -> Muster.Roll,
) {
    val locale = currentLocale()
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
                    Instant.ofEpochMilli(past.startedAt).atZone(ZoneId.systemDefault()).let {
                        Formats.dateTime(it.toLocalDate(), it.toLocalTime(), locale)
                    },
                    // Absent when the people on site did not agree on one job,
                    // which is a real answer rather than a missing one.
                    jobName,
                    past.note?.takeIf { it.isNotBlank() },
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
            )
            ShareListButton(muster = past, rollFor = rollFor)
        }
    }
}

/* ---------------------------------------------------------- one running */

@Composable
private fun LiveRollCall(
    muster: MusterEntity,
    roll: Muster.Roll,
    firstAiders: Set<String>,
    rollFor: suspend (MusterEntity) -> Muster.Roll,
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
            LiveHeader(roll, now, firstAiders)
            Spacer(Modifier.height(8.dp))
            ShareListButton(muster = muster, rollFor = rollFor)
            Spacer(Modifier.height(16.dp))
            EmergencyNumbers()
            Spacer(Modifier.height(16.dp))
        }
        items(roll.ordered(), key = { it.id }) { person ->
            PersonRow(
                person = person,
                firstAider = person.personId != null && person.personId in firstAiders,
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
private fun LiveHeader(roll: Muster.Roll, now: Long, firstAiders: Set<String>) {
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
            // Who at the muster point can help, by name. Only those counted
            // present: a first aider still missing is not one who can help.
            val helpers = roll.people
                .filter { it.state == Muster.State.PRESENT && it.personId in firstAiders }
                .map { it.name }
            if (helpers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.muster_first_aiders_here, helpers.joinToString(SEPARATOR)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
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
    firstAider: Boolean,
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
            if (firstAider) stringResource(R.string.muster_first_aider) else null,
            if (person.staleCheckIn) stringResource(R.string.muster_stale) else null,
            if (person.addedDuringRollCall) stringResource(R.string.muster_added_badge) else null,
            if (person.visitor) stringResource(R.string.muster_visitor_badge) else null,
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

/* ------------------------------------------------------- handing it over */

/**
 * Sends the list to whatever the person uses -- WhatsApp, a text, email.
 *
 * On a running roll call as well as an ended one, because the moment it is
 * needed is when the fire brigade arrives and asks who is still inside, and
 * that is before anybody has ended anything. Plain text, missing names first
 * and in capitals in English, because it will be read on a small screen by
 * somebody in a hurry. Shared through the system's own sheet, so nothing
 * leaves the phone unless the person picks where it goes.
 */
@Composable
private fun ShareListButton(
    muster: MusterEntity,
    rollFor: suspend (MusterEntity) -> Muster.Roll,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val heading = stringResource(
        R.string.muster_share_heading,
        stringResource(reasonLabel(readReason(muster.reason))),
        moment(muster.startedAt, locale),
    )
    val endedLine = muster.endedAt?.let {
        stringResource(R.string.muster_share_ended, moment(it, locale))
    } ?: stringResource(R.string.muster_share_running)

    TextButton(
        onClick = {
            scope.launch {
                val roll = rollFor(muster)
                val text = listText(context, heading, endedLine, roll, muster.note)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            }
        },
    ) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Text(stringResource(R.string.muster_share), modifier = Modifier.padding(start = 8.dp))
    }
}

/** The list as text: who is missing, who is here, who is safe elsewhere and how. */
private fun listText(
    context: Context,
    heading: String,
    endedLine: String,
    roll: Muster.Roll,
    note: String?,
): String {
    // A visitor is marked as one in the text as well as on the screen: the
    // person reading this in a group chat is the one who does not know that
    // "Avi Cohen" is the engineer from the council and not one of the crew.
    fun named(person: Muster.Person): String =
        if (person.visitor) context.getString(R.string.muster_share_visitor, person.name) else person.name
    val missing = roll.unaccounted().map(::named)
    val present = roll.people.filter { it.state == Muster.State.PRESENT }.sortedBy { it.name }.map(::named)
    val elsewhere = roll.people.filter { it.state == Muster.State.ACCOUNTED_ELSEWHERE }
        .sortedBy { it.name }
        .map { person -> person.account?.let { "${person.name} (${it})" } ?: person.name }
    return buildList {
        add(heading)
        add(endedLine)
        if (missing.isNotEmpty()) {
            add(context.getString(R.string.muster_share_unaccounted, missing.size, missing.joinToString(LIST)))
        } else {
            add(context.getString(R.string.muster_all_accounted))
        }
        if (present.isNotEmpty()) {
            add(context.getString(R.string.muster_share_present, present.size, present.joinToString(LIST)))
        }
        if (elsewhere.isNotEmpty()) {
            add(context.getString(R.string.muster_share_elsewhere, elsewhere.size, elsewhere.joinToString(LIST)))
        }
        note?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("\n")
}

private fun moment(epochMillis: Long, locale: Locale): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).let {
        Formats.dateTime(it.toLocalDate(), it.toLocalTime(), locale)
    }

private const val LIST = ", "

/* ---------------------------------------------------- the numbers to ring */

/**
 * Israel's three emergency numbers, as buttons that open the dialer.
 *
 * On both halves of the screen: before a roll call, because the first call is
 * often made before anybody counts anything, and during one, because a name
 * still missing after ten minutes is when somebody rings 102. The dialer opens
 * with the number in and the person presses call -- the app never places a
 * call itself.
 */
@Composable
private fun EmergencyNumbers() {
    val context = LocalContext.current
    var noDialer by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.muster_call_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Emergency.ORDER.forEach { number ->
            OutlinedButton(
                onClick = {
                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    // A tablet with no telephony has nothing to open. Saying
                    // so beats a button that does nothing in the one place
                    // that matters most.
                    runCatching { context.startActivity(dial) }
                        .onFailure { noDialer = true }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Text(stringResource(numberLabel(number)), modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            stringResource(if (noDialer) R.string.muster_call_no_dialer else R.string.muster_call_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (noDialer) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun numberLabel(number: String): Int = when (number) {
    Emergency.AMBULANCE -> R.string.muster_call_ambulance
    Emergency.FIRE_AND_RESCUE -> R.string.muster_call_fire
    else -> R.string.muster_call_police
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
