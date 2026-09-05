package il.co.tradesmanager.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.core.security.Passcode
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.account.roleHint
import il.co.tradesmanager.ui.account.roleLabel
import il.co.tradesmanager.ui.components.EmptyState

/**
 * Who works here.
 *
 * The People lens. Adding someone is a name, what they do, and optionally a
 * passcode — deliberately not an email and a verification link, because the
 * foreman adding a new labourer is standing next to them on a site, not
 * sending them an invitation to read at home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(container: AppContainer) {
    val viewModel: PeopleViewModel = viewModel(
        factory = ViewModelFactory(container) { PeopleViewModel(it) },
    )
    val members by viewModel.members.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val certifications by viewModel.certifications.collectAsStateWithLifecycle()
    val kinds by viewModel.kinds.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PeopleViewModel.Member?>(null) }

    val signedIn = session as? SessionRepository.State.SignedIn
    val canManage = signedIn?.canManageMembers == true

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.people_title)) }) },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.people_add))
                }
            }
        },
    ) { padding ->
        if (members.size <= 1) {
            EmptyState(
                message = stringResource(R.string.people_empty),
                hint = stringResource(R.string.people_add),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(members, key = { it.membership.id }) { member ->
                    // The role held in *this* company. The same person can be
                    // an owner on another firm's list without it showing here.
                    val role = member.role
                    ListItem(
                        headlineContent = { Text(member.account.displayName) },
                        supportingContent = {
                            val tickets = certifications[member.account.id].orEmpty()
                            val worst = tickets.minByOrNull {
                                Expiry.urgency(it.expiresOn, System.currentTimeMillis())
                            }
                            val state = Expiry.state(worst?.expiresOn, System.currentTimeMillis())
                            Text(
                                text = when (state) {
                                    Expiry.State.EXPIRED ->
                                        stringResource(roleLabel(role)) + " · " +
                                            stringResource(R.string.cert_expired)
                                    Expiry.State.EXPIRING_SOON ->
                                        stringResource(roleLabel(role)) + " · " +
                                            stringResource(R.string.cert_attention)
                                    else -> stringResource(roleLabel(role))
                                },
                                color = when (state) {
                                    Expiry.State.EXPIRED -> MaterialTheme.colorScheme.error
                                    Expiry.State.EXPIRING_SOON -> AmberWarning
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        trailingContent = {
                            // You cannot remove yourself: signing out is the
                            // thing someone actually wants there, and it is in
                            // Settings where they expect it.
                            if (canManage && member.account.id != signedIn?.account?.id) {
                                IconButton(onClick = { viewModel.remove(member) }) {
                                    Icon(
                                        Icons.Filled.PersonRemove,
                                        contentDescription = stringResource(
                                            R.string.people_remove,
                                        ),
                                    )
                                }
                            }
                        },
                        // Every row opens, including your own: tickets are worth
                        // seeing whether or not you may change anybody's role.
                        modifier = Modifier.fillMaxWidth().clickable { editing = member },
                    )
                }
            }
        }
    }

    if (adding) {
        AddMemberDialog(
            onDismiss = { adding = false },
            onAdd = { name, username, idNumber, role, passcode ->
                viewModel.addMember(name, username, idNumber, role, passcode)
                adding = false
            },
        )
    }

    editing?.let { member ->
        PersonSheet(
            person = member.account,
            role = member.role,
            certifications = certifications[member.account.id].orEmpty(),
            suggestedKinds = kinds,
            // You may look at your own tickets; you may not re-role yourself.
            canManage = canManage && member.account.id != signedIn?.account?.id,
            onDismiss = { editing = null },
            onSetRole = { viewModel.setRole(member, it) },
            onAddCertification = { title, reference, expiresOn ->
                viewModel.addCertification(member.account.id, title, reference, expiresOn)
            },
            onRemoveCertification = viewModel::removeCertification,
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            title = { Text(stringResource(R.string.people_title)) },
            text = {
                Text(
                    stringResource(
                        when (reason) {
                            AccountRepository.Refusal.LastAdministrator ->
                                R.string.people_last_admin
                            AccountRepository.Refusal.NotPermitted -> R.string.lens_locked
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (
        name: String,
        username: String?,
        idNumber: String?,
        role: Role,
        passcode: String?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.WORKER) }
    var passcode by remember { mutableStateOf("") }
    val passcodeOk = passcode.isEmpty() || Passcode.isAcceptable(passcode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.acc_your_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // What this person will actually type at the door. Filling it
                // in here is the whole point of adding somebody: they are told
                // a username and a password, not shown a list.
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.acc_identifier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { idNumber = it },
                    label = { Text(stringResource(R.string.acc_id_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.people_role),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Role.assignable.forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { role = option },
                            label = { Text(stringResource(roleLabel(option))) },
                        )
                    }
                }
                Text(
                    text = stringResource(roleHint(role)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = { Text(stringResource(R.string.acc_passcode)) },
                    isError = !passcodeOk,
                    supportingText = {
                        Text(
                            stringResource(
                                if (passcodeOk) {
                                    R.string.acc_passcode_hint
                                } else {
                                    R.string.acc_passcode_short
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && passcodeOk,
                onClick = {
                    onAdd(
                        name.trim(),
                        username.trim().takeIf { it.isNotEmpty() },
                        idNumber.trim().takeIf { it.isNotEmpty() },
                        role,
                        passcode.takeIf { it.isNotEmpty() },
                    )
                },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Amber: still legal, but book the renewal course. Not an error yet. */
private val AmberWarning = androidx.compose.ui.graphics.Color(0xFFB9770E)
