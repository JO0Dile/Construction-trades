package il.co.tradesmanager.ui.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.core.work.Contacts
import il.co.tradesmanager.data.local.entity.JobContactEntity
import il.co.tradesmanager.data.repository.JobContactRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter

/**
 * Who is who on one job from outside the firm, with a tap to ring or write
 * to each. The page pinned to the site office wall, kept on the phone where
 * it can be kept up to date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobContactsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: JobContactsViewModel =
        viewModel(factory = ViewModelFactory(container) { JobContactsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JobContactEntity?>(null) }
    var opened by remember { mutableStateOf<JobContactEntity?>(null) }
    var removing by remember { mutableStateOf<JobContactEntity?>(null) }
    var nothingToOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The directory for the site office wall: those still on the job.
                    if (contacts.any { it.removedAt == null }) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.ContactDirectory(jobName = jobName, contacts = contacts),
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.jc_add))
                }
            }
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.jc_empty),
                hint = stringResource(R.string.jc_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(contacts, key = { it.id }) { contact ->
                val gone = contact.removedAt != null
                ListItem(
                    overlineContent = {
                        Text(
                            listOfNotNull(stringResource(contactKindLabel(Contacts.kindOf(contact.kind))), contact.organisation)
                                .joinToString(SEPARATOR),
                        )
                    },
                    headlineContent = { Text(contact.name) },
                    supportingContent = {
                        Text(
                            if (gone) {
                                stringResource(R.string.jc_left_the_job)
                            } else {
                                listOfNotNull(contact.phone, contact.email).joinToString(SEPARATOR)
                            },
                            color = if (gone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingContent = {
                        if (!gone && contact.phone != null) {
                            IconButton(onClick = { if (!dial(context, contact.phone)) nothingToOpen = true }) {
                                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.jc_call, contact.name))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { opened = contact },
                )
            }
        }
    }

    opened?.let { contact ->
        val gone = contact.removedAt != null
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(contact.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listOfNotNull(stringResource(contactKindLabel(Contacts.kindOf(contact.kind))), contact.organisation)
                            .joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    contact.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (gone) {
                        Text(stringResource(R.string.jc_left_the_job), style = MaterialTheme.typography.bodySmall)
                    } else {
                        contact.phone?.let { phone ->
                            OutlinedButton(onClick = { if (!dial(context, phone)) nothingToOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Call, contentDescription = null)
                                Text(phone, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        contact.email?.let { email ->
                            OutlinedButton(onClick = { if (!write(context, email)) nothingToOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Email, contentDescription = null)
                                Text(email, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (mayWrite) {
                            OutlinedButton(onClick = { editing = contact; opened = null }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.jc_correct))
                            }
                            OutlinedButton(onClick = { removing = contact; opened = null }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.jc_remove))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { opened = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (adding) {
        EntryDialog(
            title = stringResource(R.string.jc_add),
            initial = null,
            onDismiss = { adding = false },
            onSave = { entry ->
                adding = false
                viewModel.add(entry)
            },
        )
    }

    editing?.let { contact ->
        EntryDialog(
            title = stringResource(R.string.jc_correct),
            initial = contact,
            onDismiss = { editing = null },
            onSave = { entry ->
                editing = null
                viewModel.correct(contact.id, entry)
            },
        )
    }

    removing?.let { contact ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.jc_remove_question, contact.name)) },
            text = { Text(stringResource(R.string.jc_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        viewModel.remove(contact.id)
                    },
                ) { Text(stringResource(R.string.jc_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (nothingToOpen) {
        AlertDialog(
            onDismissRequest = { nothingToOpen = false },
            text = { Text(stringResource(R.string.jc_nothing_to_open)) },
            confirmButton = {
                TextButton(onClick = { nothingToOpen = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(contactRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDialog(
    title: String,
    initial: JobContactEntity?,
    onDismiss: () -> Unit,
    onSave: (JobContactRepository.Entry) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var organisation by remember { mutableStateOf(initial?.organisation.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.let { Contacts.kindOf(it.kind) } ?: Contacts.Kind.SUBCONTRACTOR) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.jc_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Contacts.Kind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(stringResource(contactKindLabel(option))) })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.jc_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = organisation,
                    onValueChange = { organisation = it },
                    label = { Text(stringResource(R.string.jc_organisation)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.jc_phone)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.jc_email)) },
                    supportingText = { Text(stringResource(R.string.jc_reach_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.jc_notes)) },
                    supportingText = { Text(stringResource(R.string.jc_notes_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()),
                onClick = { onSave(JobContactRepository.Entry(name, organisation, kind, phone, email, notes)) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** ACTION_DIAL: the dialler opens with the number in, and the person presses call. */
private fun dial(context: Context, phone: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + EmergencySheet.dialString(phone)))) }.isSuccess

/** A new message to [email] in whatever mail app the phone has. */
private fun write(context: Context, email: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email.trim()))) }.isSuccess

@StringRes
internal fun contactKindLabel(kind: Contacts.Kind): Int = when (kind) {
    Contacts.Kind.CLIENT -> R.string.jc_kind_client
    Contacts.Kind.SUPERVISOR -> R.string.jc_kind_supervisor
    Contacts.Kind.ARCHITECT -> R.string.jc_kind_architect
    Contacts.Kind.STRUCTURAL_ENGINEER -> R.string.jc_kind_structural
    Contacts.Kind.OTHER_ENGINEER -> R.string.jc_kind_engineer
    Contacts.Kind.SAFETY_CONSULTANT -> R.string.jc_kind_safety
    Contacts.Kind.MUNICIPALITY -> R.string.jc_kind_municipality
    Contacts.Kind.SUBCONTRACTOR -> R.string.jc_kind_subcontractor
    Contacts.Kind.SUPPLIER -> R.string.jc_kind_supplier
    Contacts.Kind.OTHER -> R.string.jc_kind_other
}

@StringRes
private fun contactRefusalText(refusal: JobContactRepository.Refusal): Int = when (refusal) {
    JobContactRepository.Refusal.NOT_ALLOWED -> R.string.jc_refused_not_allowed
    JobContactRepository.Refusal.BLANK_NAME -> R.string.jc_refused_name
    JobContactRepository.Refusal.NO_WAY_TO_REACH -> R.string.jc_refused_reach
    JobContactRepository.Refusal.NOT_A_PHONE_NUMBER -> R.string.es_refused_phone
    JobContactRepository.Refusal.NOT_AN_EMAIL -> R.string.jc_refused_email
    JobContactRepository.Refusal.ALREADY_REMOVED -> R.string.jc_refused_removed
    JobContactRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

private const val SEPARATOR = " · "
