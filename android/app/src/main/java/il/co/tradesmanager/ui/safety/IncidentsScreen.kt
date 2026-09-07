package il.co.tradesmanager.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionPlaceholder
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
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    var reporting by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { reporting = true }) {
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
                        Text(
                            incident.reportedByName + " · " +
                                Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale),
                        )
                    },
                )
            }
        }
    }

    if (reporting) {
        ReportDialog(
            onDismiss = { reporting = false },
            onReport = { severity, description ->
                viewModel.report(severity, description)
                reporting = false
            },
        )
    }
}

@Composable
private fun ReportDialog(
    onDismiss: () -> Unit,
    onReport: (Incidents.Severity, String) -> Unit,
) {
    var severity by remember { mutableStateOf(Incidents.Severity.NEAR_MISS) }
    var description by remember { mutableStateOf("") }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReport(severity, description) },
                enabled = description.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun severityLabel(severity: Incidents.Severity): Int = when (severity) {
    Incidents.Severity.NEAR_MISS -> R.string.inc_near_miss
    Incidents.Severity.MINOR -> R.string.inc_minor
    Incidents.Severity.SERIOUS -> R.string.inc_serious
    Incidents.Severity.FATAL -> R.string.inc_fatal
}
