package il.co.tradesmanager.ui.evidence

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Briefings
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import java.time.Instant
import java.time.ZoneId

/**
 * Toolbox talks: what was briefed, and who was standing there for it.
 *
 * The overdue list sits above the log on purpose. A register answers "did we
 * brief the crew?", which is the question somebody asks when they are already
 * comfortable. The question worth putting first is the other one — who has not
 * been briefed — and the answer that matters most in it is the person with no
 * record at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalksScreen(
    container: AppContainer,
    onOpenTalk: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TalksViewModel = viewModel(
        factory = ViewModelFactory(container) { TalksViewModel(it) },
    )
    val talks by viewModel.talks.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val needBriefing by viewModel.needBriefing.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val now = rememberNow(intervalMillis = 5 * 60_000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var recording by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tbt_title)) },
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
            if (canEdit) {
                FloatingActionButton(onClick = { recording = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tbt_new))
                }
            }
        },
    ) { padding ->
        if (talks.isEmpty() && needBriefing.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.tbt_empty),
                hint = stringResource(R.string.tbt_new),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.tbt_overdue_title)) }
            if (needBriefing.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.tbt_all_briefed)) }
            }
            items(needBriefing, key = { it.accountId }) { record ->
                val last = record.lastAttendedAt
                ListItem(
                    headlineContent = { Text(record.name) },
                    supportingContent = {
                        Text(
                            text = if (last == null) {
                                stringResource(R.string.tbt_never)
                            } else {
                                pluralCount(
                                    R.plurals.tbt_last_briefed,
                                    Briefings.daysSince(last, now),
                                )
                            },
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.tbt_title)) }
            if (talks.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.tbt_empty)) }
            }
            items(talks, key = { it.id }) { talk ->
                val held = Instant.ofEpochMilli(talk.heldAt).atZone(ZoneId.systemDefault())
                ListItem(
                    overlineContent = { Text(Formats.date(held.toLocalDate(), locale)) },
                    headlineContent = { Text(talk.topic) },
                    supportingContent = {
                        val job = projects.firstOrNull { it.id == talk.projectId }?.name
                        Text(listOfNotNull(job, talk.presenterName).joinToString(" · "))
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTalk(talk.id) },
                )
            }
        }
    }

    if (recording) {
        RecordTalkDialog(
            projects = projects,
            onDismiss = { recording = false },
            onRecord = { projectId, topic, notes ->
                recording = false
                viewModel.record(projectId, topic, notes, onOpenTalk)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordTalkDialog(
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onRecord: (projectId: String?, topic: String, notes: String?) -> Unit,
) {
    var topic by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbt_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.tbt_topic)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.tbt_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.po_for_job),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = projectId == null,
                        onClick = { projectId = null },
                        label = { Text(stringResource(R.string.po_yard)) },
                    )
                    projects.forEach { project ->
                        FilterChip(
                            selected = projectId == project.id,
                            onClick = { projectId = project.id },
                            label = { Text(project.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = topic.isNotBlank(),
                onClick = {
                    onRecord(projectId, topic.trim(), notes.trim().takeIf { it.isNotEmpty() })
                },
            ) {
                Text(stringResource(R.string.acc_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
