package il.co.tradesmanager.ui.evidence

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Snags
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.SnagEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.rememberNow
import java.time.Instant
import java.time.ZoneId

/**
 * The defects found on the jobs, and how far off finished they are.
 *
 * The screen opens on what is outstanding rather than on everything, because a
 * snag list is a list of work and the closed rows are a record you go looking
 * for. The header counts outstanding, not open — a snag somebody says they
 * fixed is still work until somebody else has been and looked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnagsScreen(
    container: AppContainer,
    onOpenSnag: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SnagsViewModel = viewModel(
        factory = ViewModelFactory(container) { SnagsViewModel(it) },
    )
    val snags by viewModel.snags.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val now = rememberNow(intervalMillis = 5 * 60_000L)

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var raising by remember { mutableStateOf(false) }
    var outstandingOnly by remember { mutableStateOf(true) }

    val progress = remember(snags, now) {
        Snags.handover(snags.map { Triple(it.status, it.dueOn, it.blocksHandover) }, now)
    }
    val shown = remember(snags, outstandingOnly) {
        if (outstandingOnly) snags.filter { Snags.state(it.status).isOutstanding } else snags
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.snag_title)) },
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
            if (canEdit && projects.isNotEmpty()) {
                FloatingActionButton(onClick = { raising = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.snag_new))
                }
            }
        },
    ) { padding ->
        if (snags.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.snag_empty),
                hint = stringResource(R.string.snag_new),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item { HandoverBanner(progress = progress) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = outstandingOnly,
                        onClick = { outstandingOnly = true },
                        label = { Text(stringResource(R.string.snag_filter_outstanding)) },
                    )
                    FilterChip(
                        selected = !outstandingOnly,
                        onClick = { outstandingOnly = false },
                        label = { Text(stringResource(R.string.snag_filter_all)) },
                    )
                }
            }
            items(shown, key = { it.id }) { snag ->
                SnagRow(
                    snag = snag,
                    jobName = projects.firstOrNull { it.id == snag.projectId }?.name,
                    thumbnail = thumbnails[snag.id],
                    now = now,
                    locale = locale,
                    onClick = { onOpenSnag(snag.id) },
                )
            }
        }
    }

    if (raising) {
        RaiseSnagDialog(
            projects = projects,
            onDismiss = { raising = false },
            onRaise = { projectId, title, location, assignedTo, dueOn, blocks ->
                raising = false
                viewModel.raise(projectId, title, location, assignedTo, dueOn, blocks, onOpenSnag)
            },
        )
    }
}

/**
 * Whether anything is still holding up handover.
 *
 * The one number a site manager wants before they walk into a meeting, and it
 * is deliberately not the same as "how many snags are open".
 */
@Composable
private fun HandoverBanner(progress: Snags.Handover) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (progress.isReady) {
        scheme.primaryContainer to scheme.onPrimaryContainer
    } else {
        scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    if (progress.isReady) R.string.snag_ready else R.string.snag_not_ready,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = pluralCount(R.plurals.snag_outstanding, progress.outstanding.toLong()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SnagRow(
    snag: SnagEntity,
    jobName: String?,
    thumbnail: String?,
    now: Long,
    locale: java.util.Locale,
    onClick: () -> Unit,
) {
    val state = Snags.state(snag.status)
    val overdue = Snags.isOverdue(snag.status, snag.dueOn, now)
    ListItem(
        overlineContent = { Text(listOfNotNull(snag.reference, jobName).joinToString(" · ")) },
        headlineContent = { Text(snag.title) },
        supportingContent = {
            Column {
                val where = listOfNotNull(snag.location, snag.assignedToName)
                if (where.isNotEmpty()) Text(where.joinToString(" · "))
                Text(
                    text = stringResource(snagStateLabel(state)),
                    color = if (state == Snags.State.REJECTED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                if (overdue) {
                    Text(
                        text = pluralCount(
                            R.plurals.snag_overdue_days,
                            Snags.daysOverdue(snag.dueOn, now),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    snag.dueOn?.let {
                        val due = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                        Text(
                            text = stringResource(R.string.snag_due) + " " +
                                Formats.date(due.toLocalDate(), locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                // A snag list without pictures is a list of sentences somebody
                // has to walk the building to understand.
                thumbnail?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = snag.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (snag.blocksHandover) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.error)
                            .size(10.dp),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

internal fun snagStateLabel(state: Snags.State): Int = when (state) {
    Snags.State.OPEN -> R.string.snag_state_open
    Snags.State.AWAITING_CHECK -> R.string.snag_state_awaiting
    Snags.State.CLOSED -> R.string.snag_state_closed
    Snags.State.REJECTED -> R.string.snag_state_rejected
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseSnagDialog(
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onRaise: (
        projectId: String,
        title: String,
        location: String?,
        assignedTo: String?,
        dueOn: Long?,
        blocksHandover: Boolean,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var assignedTo by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var blocks by remember { mutableStateOf(true) }
    var projectId by remember { mutableStateOf(projects.firstOrNull()?.id) }

    val parsedDue = if (due.isBlank()) null else Formats.parseDate(due)
    val dueIsBad = due.isNotBlank() && parsedDue == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snag_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.snag_what)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.snag_where)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = assignedTo,
                    onValueChange = { assignedTo = it },
                    label = { Text(stringResource(R.string.snag_who)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text(stringResource(R.string.snag_due)) },
                    singleLine = true,
                    isError = dueIsBad,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.po_for_job),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    projects.forEach { project ->
                        FilterChip(
                            selected = projectId == project.id,
                            onClick = { projectId = project.id },
                            label = { Text(project.name) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(checked = blocks, onCheckedChange = { blocks = it })
                    Text(
                        text = stringResource(R.string.snag_blocks),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.snag_blocks_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && projectId != null && !dueIsBad,
                onClick = {
                    onRaise(
                        projectId.orEmpty(),
                        title.trim(),
                        location.trim().takeIf { it.isNotEmpty() },
                        assignedTo.trim().takeIf { it.isNotEmpty() },
                        parsedDue?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                        blocks,
                    )
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
