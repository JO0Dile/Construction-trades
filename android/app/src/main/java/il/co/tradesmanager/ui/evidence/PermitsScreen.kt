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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Permits
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.rememberNow

/**
 * Permits to work.
 *
 * The list re-sorts itself against a moving clock rather than against the
 * status column, because a permit that ran out ten minutes ago is still stored
 * as ISSUED and has to stop looking live all the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermitsScreen(
    container: AppContainer,
    onOpenPermit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: PermitsViewModel = viewModel(
        factory = ViewModelFactory(container) { PermitsViewModel(it) },
    )
    val permits by viewModel.permits.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val now = rememberNow()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.EVIDENCE) != false
    var raising by remember { mutableStateOf(false) }

    // SQL put the issued permits first; only Kotlin knows which of those are
    // about to run out, so the final ordering happens here against one tested
    // definition of urgency.
    val ordered = remember(permits, now) {
        permits.sortedBy { Permits.urgency(it.status, it.validTo, now) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ptw_title)) },
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
                FloatingActionButton(onClick = { raising = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ptw_new))
                }
            }
        },
    ) { padding ->
        if (ordered.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ptw_empty),
                hint = stringResource(R.string.ptw_new),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(ordered, key = { it.id }) { permit ->
                    PermitRow(
                        permit = permit,
                        jobName = projects.firstOrNull { it.id == permit.projectId }?.name,
                        now = now,
                        onClick = { onOpenPermit(permit.id) },
                    )
                }
            }
        }
    }

    if (raising) {
        RaisePermitDialog(
            projects = projects,
            onDismiss = { raising = false },
            onRaise = { projectId, kind, description, location, issuedTo, precautions ->
                raising = false
                viewModel.raise(
                    projectId = projectId,
                    type = kind,
                    description = description,
                    location = location,
                    issuedToName = issuedTo,
                    precautions = precautions,
                    onRaised = onOpenPermit,
                )
            },
        )
    }
}

@Composable
private fun PermitRow(
    permit: PermitEntity,
    jobName: String?,
    now: Long,
    onClick: () -> Unit,
) {
    val state = Permits.state(permit.status, permit.validFrom, permit.validTo, now)
    ListItem(
        overlineContent = {
            Text(permit.reference + " · " + stringResource(permitTypeLabel(permit.type)))
        },
        headlineContent = { Text(permit.description) },
        supportingContent = {
            val where = permit.location ?: jobName
            Text(listOfNotNull(where, permit.issuedToName).joinToString(" · "))
        },
        trailingContent = { PermitStateChip(state = state, validTo = permit.validTo, now = now) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/**
 * The permit's state, coloured by how much it matters.
 *
 * Expired is the error colour and not a neutral grey: it is the one state that
 * means somebody has to stop what they are doing right now. An expiring permit
 * shows the minutes rather than the word, because "running out" is advice and
 * "9 minutes left" is an instruction.
 */
@Composable
internal fun PermitStateChip(state: Permits.State, validTo: Long?, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        Permits.State.EXPIRED -> scheme.errorContainer to scheme.onErrorContainer
        Permits.State.EXPIRING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Permits.State.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    val label = if (state == Permits.State.EXPIRING) {
        pluralCount(R.plurals.ptw_minutes_left, Permits.minutesLeft(validTo, now))
    } else {
        stringResource(permitStateLabel(state))
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * A count that reads correctly in Hebrew and Arabic, where "two" and "few" are
 * separate grammatical forms rather than decoration.
 */
@Composable
internal fun pluralCount(id: Int, count: Long): String {
    val bounded = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(id, bounded, bounded)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaisePermitDialog(
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onRaise: (
        projectId: String?,
        type: String,
        description: String,
        location: String?,
        issuedTo: String,
        precautions: List<String>,
    ) -> Unit,
) {
    var kind by remember { mutableStateOf(PERMIT_KINDS.first()) }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var issuedTo by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }

    // Resolved here, in the language on screen, and carried onto the permit.
    val precautions = kind.precautionRes.map { stringResource(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ptw_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.ptw_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PERMIT_KINDS.forEach { candidate ->
                        FilterChip(
                            selected = candidate.type == kind.type,
                            onClick = { kind = candidate },
                            label = { Text(stringResource(candidate.titleRes)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.ptw_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = issuedTo,
                    onValueChange = { issuedTo = it },
                    label = { Text(stringResource(R.string.ptw_issued_to)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.ptw_location)) },
                    singleLine = true,
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
                // Shown before the permit is raised, not after: somebody has to
                // know what they are taking on before they take it on.
                Text(
                    text = stringResource(R.string.ptw_precautions),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                precautions.forEach { line ->
                    Text(text = "• $line", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && issuedTo.isNotBlank(),
                onClick = {
                    onRaise(
                        projectId,
                        kind.type,
                        description.trim(),
                        location.trim().takeIf { it.isNotEmpty() },
                        issuedTo.trim(),
                        precautions,
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
