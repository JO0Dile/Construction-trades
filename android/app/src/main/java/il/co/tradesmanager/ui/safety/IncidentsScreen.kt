package il.co.tradesmanager.ui.safety

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.rememberImageAdder
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId

/**
 * What went wrong, written down.
 *
 * The table and the repository for this existed and nothing could reach them:
 * no way to report an accident, no way to read one back. Near misses are on
 * the same list as injuries on purpose — a near miss is the same accident with
 * better luck, and a register that only records the ones that drew blood
 * throws away every warning that came first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentsScreen(
    container: AppContainer,
    projectId: String?,
    onBack: () -> Unit,
) {
    val viewModel: IncidentsViewModel = viewModel(
        factory = ViewModelFactory(container) { IncidentsViewModel(it, projectId) },
    )
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val evidence by viewModel.evidence.collectAsStateWithLifecycle()
    val draftId by viewModel.draftId.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startReport() }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.inc_add),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    text = stringResource(R.string.inc_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (incidents.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.inc_empty)) }
            }
            items(incidents, key = { it.id }) { incident ->
                val severity = Incidents.parse(incident.severity)
                val at = Instant.ofEpochMilli(incident.occurredAt).atZone(zone)
                ListItem(
                    overlineContent = {
                        Text(
                            text = stringResource(severityLabel(severity)),
                            color = if (Incidents.needsEscalating(severity)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    headlineContent = { Text(incident.description) },
                    supportingContent = {
                        Column {
                            Text(
                                incident.reportedByName + " · " +
                                    Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale),
                            )
                            incident.costAmount?.let { Text(Formats.money(it, locale)) }
                        }
                    },
                )
            }
        }
    }

    if (draftId != null) {
        ReportDialog(
            viewModel = viewModel,
            evidenceCount = evidence.size,
            onDismiss = { viewModel.discardReport() },
        )
    }
}

/**
 * One report: how bad, what happened, the proof, and what it cost.
 *
 * The proof is required and the button says so. An incident is argued about
 * weeks later by people who were not there — by then the ladder has been
 * moved and the spill mopped, and the only thing left is what somebody
 * photographed while standing in front of it. The cost is optional, because
 * most reports are filed by somebody who has no idea yet what it will come to,
 * and a required figure would be answered with zero.
 */
@Composable
private fun ReportDialog(
    viewModel: IncidentsViewModel,
    evidenceCount: Int,
    onDismiss: () -> Unit,
) {
    var severity by remember { mutableStateOf(Incidents.Severity.NEAR_MISS) }
    var description by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    // Locale-independent: what is typed is what is stored, and a decimal comma
    // must not silently become a different number.
    val cost = costText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val costTyped = costText.isNotBlank()

    val addEvidence = rememberImageAdder(
        newCameraTarget = { viewModel.newEvidenceTarget() },
        onCaptured = { viewModel.evidenceCaptured(it) },
        onPicked = { viewModel.addEvidence(it) },
        // Both, for the same reason violations take both: a still of a man on
        // a ladder does not always show what was wrong with how he stood on it.
        allowed = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
    )

    // Text that will not parse is the screen's problem, not the rule's: the
    // rule takes a Double? and there is no Double for "3o shekels". Treating
    // it as "no cost given" would quietly throw away what somebody typed.
    val blocker = if (costTyped && (cost == null || cost < 0.0)) {
        Incidents.Blocker.BAD_COST
    } else {
        Incidents.blocksReporting(
            Incidents.Report(
                description = description,
                evidenceCount = evidenceCount,
                cost = cost,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inc_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.inc_severity),
                    style = MaterialTheme.typography.labelLarge,
                )
                Incidents.Severity.entries.forEach { option ->
                    FilterChip(
                        selected = severity == option,
                        onClick = { severity = option },
                        label = { Text(stringResource(severityLabel(option))) },
                    )
                }
                // Shown as the severity is chosen rather than after saving, so
                // it reaches somebody while they are still deciding what to do
                // — and worded as a prompt, because what the law requires is
                // not something this app is in a position to assert.
                if (Incidents.needsEscalating(severity)) {
                    Text(
                        text = stringResource(R.string.inc_escalate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.inc_what)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = addEvidence, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inc_evidence, evidenceCount))
                }
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text(stringResource(R.string.inc_cost)) },
                    supportingText = { Text(stringResource(R.string.inc_cost_hint)) },
                    singleLine = true,
                    isError = costTyped && (cost == null || cost < 0.0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Says which thing is outstanding rather than leaving a dead
                // button with no explanation.
                blocker?.let {
                    Text(
                        text = stringResource(blockerText(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.report(severity, description, cost) },
                enabled = blocker == null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The sentence for each blocker. Exhaustive with no `else`, so the next one
 * somebody adds must not become a blank line under the button.
 */
private fun blockerText(blocker: Incidents.Blocker): Int = when (blocker) {
    Incidents.Blocker.NOT_DESCRIBED -> R.string.inc_need_description
    Incidents.Blocker.NO_EVIDENCE -> R.string.inc_need_evidence
    Incidents.Blocker.BAD_COST -> R.string.inc_bad_cost
}

private fun severityLabel(severity: Incidents.Severity): Int = when (severity) {
    Incidents.Severity.NEAR_MISS -> R.string.inc_near_miss
    Incidents.Severity.MINOR -> R.string.inc_minor
    Incidents.Severity.SERIOUS -> R.string.inc_serious
    Incidents.Severity.FATAL -> R.string.inc_fatal
}
