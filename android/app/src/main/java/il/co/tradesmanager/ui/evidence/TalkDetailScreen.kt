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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.LoadingState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId

/**
 * One talk and its attendance register.
 *
 * The register is the evidence; the talk row on its own proves nothing, which
 * is why an empty register says so rather than looking like a finished record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkDetailScreen(
    container: AppContainer,
    talkId: String,
    onBack: () -> Unit,
) {
    val viewModel: TalkDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { TalkDetailViewModel(it, talkId) },
    )
    val talk by viewModel.talk.collectAsStateWithLifecycle()
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    val crew by viewModel.crew.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var signingIn by remember { mutableStateOf(false) }

    val current = talk

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.topic ?: stringResource(R.string.tbt_title)) },
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
        floatingActionButton = {
            if (canEdit && current != null) {
                FloatingActionButton(onClick = { signingIn = true }) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = stringResource(R.string.tbt_sign_in),
                    )
                }
            }
        },
    ) { padding ->
        if (current == null) {
            LoadingState(
                message = stringResource(R.string.tbt_title),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val held = Instant.ofEpochMilli(current.heldAt).atZone(ZoneId.systemDefault())

        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Formats.dateTime(held.toLocalDate(), held.toLocalTime(), locale),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.tbt_presenter) +
                            ": " + current.presenterName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    current.notes?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                SectionHeader(
                    stringResource(R.string.tbt_register) + " · " +
                        pluralCount(R.plurals.tbt_attendees, attendees.size.toLong()),
                )
            }
            if (attendees.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.tbt_register_empty)) }
            }
            items(attendees, key = { it.id }) { attendee ->
                ListItem(
                    headlineContent = { Text(attendee.name) },
                    trailingContent = {
                        if (canEdit) {
                            IconButton(onClick = { viewModel.remove(attendee) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (signingIn) {
        SignInDialog(
            crew = crew,
            alreadySigned = attendees.mapNotNull { it.accountId }.toSet(),
            onDismiss = { signingIn = false },
            onSignIn = { accountId, name ->
                signingIn = false
                viewModel.signIn(accountId, name)
            },
        )
    }
}

/**
 * Two ways in, because a register has two kinds of person on it: the crew,
 * who are two taps away, and the subcontractor's lad who turned up this
 * morning and is not on anybody's books.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignInDialog(
    crew: List<AccountEntity>,
    alreadySigned: Set<String>,
    onDismiss: () -> Unit,
    onSignIn: (accountId: String?, name: String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val remaining = crew.filter { it.id !in alreadySigned }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbt_sign_in)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (remaining.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tbt_crew),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        remaining.forEach { account ->
                            SuggestionChip(
                                onClick = { onSignIn(account.id, account.displayName) },
                                label = { Text(account.displayName) },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.tbt_visitor),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.tbt_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = typed.isNotBlank(),
                onClick = { onSignIn(null, typed.trim()) },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
