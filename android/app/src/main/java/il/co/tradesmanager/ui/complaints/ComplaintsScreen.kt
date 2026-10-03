package il.co.tradesmanager.ui.complaints

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import il.co.tradesmanager.data.repository.ComplaintRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One job's complaints register.
 *
 * The ones unanswered for a week first and in red, because a neighbour who
 * has heard nothing for a week is the one who phones the municipality next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintsScreen(
    container: AppContainer,
    projectId: String,
    onBack: () -> Unit,
) {
    val viewModel: ComplaintsViewModel =
        viewModel(factory = ViewModelFactory(container) { ComplaintsViewModel(it, projectId) })
    val mayWrite by viewModel.mayWrite.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val jobName by viewModel.jobName.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    var receiving by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<ComplaintEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cp_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // The register as a document; the complainants' contact details are not in it.
                    if (rows.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val result = Exporter.write(
                                    context = context,
                                    document = ExportDocument.ComplaintRegister(jobName = jobName, complaints = rows.map { it.complaint }),
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
                FloatingActionButton(onClick = { receiving = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cp_receive))
                }
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.cp_empty),
                hint = stringResource(R.string.cp_blurb),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rows, key = { it.complaint.id }) { row ->
                val complaint = row.complaint
                val waiting = row.state == Complaints.State.WAITING_LONG
                ListItem(
                    overlineContent = {
                        Text(
                            complaint.reference + SEPARATOR +
                                stringResource(complaintSubjectLabel(Complaints.subjectOf(complaint.subject))) + SEPARATOR +
                                complaint.fromWhom,
                        )
                    },
                    headlineContent = { Text(complaint.description, maxLines = 3) },
                    supportingContent = {
                        Text(
                            when (row.state) {
                                Complaints.State.ANSWERED -> stringResource(R.string.cp_answered_on, dateOf(complaint.answeredAt ?: complaint.receivedAt, locale))
                                Complaints.State.WAITING_LONG -> stringResource(R.string.cp_waiting_long, dateOf(complaint.receivedAt, locale))
                                Complaints.State.OPEN -> stringResource(R.string.cp_received_on, dateOf(complaint.receivedAt, locale))
                            },
                            color = if (waiting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (waiting) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { reading = complaint },
                )
            }
        }
    }

    if (receiving) {
        ReceiveDialog(
            onDismiss = { receiving = false },
            onReceive = { from, contact, channel, subject, description, hoursAgo ->
                receiving = false
                viewModel.receive(from, contact, channel, subject, description, hoursAgo)
            },
        )
    }

    reading?.let { complaint ->
        ReadDialog(
            complaint = complaint,
            locale = locale,
            mayAnswer = mayWrite && complaint.answeredAt == null,
            onDismiss = { reading = null },
            onAnswer = { response ->
                reading = null
                viewModel.answer(complaint.id, response)
            },
        )
    }

    refusal?.let { reason ->
        AlertDialog(
            onDismissRequest = viewModel::clearRefusal,
            text = { Text(stringResource(complaintRefusalText(reason))) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiveDialog(
    onDismiss: () -> Unit,
    onReceive: (
        from: String,
        contact: String,
        channel: Complaints.Channel,
        subject: Complaints.Subject,
        description: String,
        hoursAgo: Int,
    ) -> Unit,
) {
    var from by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(Complaints.Channel.IN_PERSON) }
    var subject by remember { mutableStateOf(Complaints.Subject.NOISE) }
    var description by remember { mutableStateOf("") }
    var hoursAgo by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cp_receive)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.cp_subject), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Complaints.Subject.entries.forEach { option ->
                        FilterChip(
                            selected = subject == option,
                            onClick = { subject = option },
                            label = { Text(stringResource(complaintSubjectLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.cp_description)) },
                    supportingText = { Text(stringResource(R.string.cp_description_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text(stringResource(R.string.cp_from)) },
                    supportingText = { Text(stringResource(R.string.cp_from_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(stringResource(R.string.cp_contact)) },
                    supportingText = { Text(stringResource(R.string.cp_contact_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.cp_channel), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Complaints.Channel.entries.forEach { option ->
                        FilterChip(
                            selected = channel == option,
                            onClick = { channel = option },
                            label = { Text(stringResource(channelLabel(option))) },
                        )
                    }
                }
                Text(stringResource(R.string.cp_when), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RECEIVED_OFFERS.forEach { (hours, label) ->
                        FilterChip(selected = hoursAgo == hours, onClick = { hoursAgo = hours }, label = { Text(stringResource(label)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = from.isNotBlank() && description.isNotBlank(),
                onClick = { onReceive(from, contact, channel, subject, description, hoursAgo) },
            ) { Text(stringResource(R.string.cp_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ReadDialog(
    complaint: ComplaintEntity,
    locale: Locale,
    mayAnswer: Boolean,
    onDismiss: () -> Unit,
    onAnswer: (String) -> Unit,
) {
    var response by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(complaint.reference + SEPARATOR + stringResource(complaintSubjectLabel(Complaints.subjectOf(complaint.subject)))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(complaint.description, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        complaint.fromWhom,
                        complaint.contact,
                        stringResource(channelLabel(Complaints.channelOf(complaint.channel))),
                        stringResource(R.string.cp_received_on, dateOf(complaint.receivedAt, locale)),
                        stringResource(R.string.cp_taken_by, complaint.receivedByName),
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (complaint.response != null) {
                    Text(stringResource(R.string.cp_response), style = MaterialTheme.typography.labelLarge)
                    Text(complaint.response, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            complaint.answeredAt?.let { stringResource(R.string.cp_answered_on, dateOf(it, locale)) },
                            complaint.answeredByName,
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (mayAnswer) {
                    OutlinedTextField(
                        value = response,
                        onValueChange = { response = it },
                        label = { Text(stringResource(R.string.cp_response_typed)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (mayAnswer && complaint.response == null) {
                TextButton(enabled = response.isNotBlank(), onClick = { onAnswer(response) }) {
                    Text(stringResource(R.string.cp_record_response))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (mayAnswer && complaint.response == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
internal fun complaintSubjectLabel(subject: Complaints.Subject): Int = when (subject) {
    Complaints.Subject.NOISE -> R.string.cp_subject_noise
    Complaints.Subject.DUST -> R.string.cp_subject_dust
    Complaints.Subject.HOURS -> R.string.cp_subject_hours
    Complaints.Subject.BLOCKING -> R.string.cp_subject_blocking
    Complaints.Subject.DAMAGE -> R.string.cp_subject_damage
    Complaints.Subject.DIRT -> R.string.cp_subject_dirt
    Complaints.Subject.SAFETY -> R.string.cp_subject_safety
    Complaints.Subject.OTHER -> R.string.cp_subject_other
}

@StringRes
private fun channelLabel(channel: Complaints.Channel): Int = when (channel) {
    Complaints.Channel.IN_PERSON -> R.string.cp_channel_in_person
    Complaints.Channel.PHONE -> R.string.cp_channel_phone
    Complaints.Channel.WRITING -> R.string.cp_channel_writing
    Complaints.Channel.MUNICIPALITY -> R.string.cp_channel_municipality
}

@StringRes
private fun complaintRefusalText(refusal: ComplaintRepository.Refusal): Int = when (refusal) {
    ComplaintRepository.Refusal.NOT_ALLOWED -> R.string.cp_refused_not_allowed
    ComplaintRepository.Refusal.BLANK_FROM -> R.string.cp_refused_from
    ComplaintRepository.Refusal.BLANK_DESCRIPTION -> R.string.cp_refused_description
    ComplaintRepository.Refusal.RECEIVED_IN_FUTURE -> R.string.cp_refused_future
    ComplaintRepository.Refusal.BLANK_RESPONSE -> R.string.cp_refused_response
    ComplaintRepository.Refusal.ALREADY_ANSWERED -> R.string.cp_refused_answered
    ComplaintRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** When it was made, in hours back: most are written down the same day. */
private val RECEIVED_OFFERS = listOf(
    0 to R.string.cp_just_now,
    3 to R.string.cp_3_hours_ago,
    24 to R.string.de_yesterday,
    48 to R.string.de_2_days_ago,
)

private const val SEPARATOR = " · "
