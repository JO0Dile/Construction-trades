package il.co.tradesmanager.ui.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLanguageTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyScreen(
    container: AppContainer,
    onRunChecklist: (String) -> Unit,
    onOpenTalks: () -> Unit,
    onOpenPermits: () -> Unit,
    onOpenSnags: () -> Unit,
    onOpenIncidents: () -> Unit,
    onOpenViolations: () -> Unit,
) {
    val viewModel: SafetyViewModel = viewModel(factory = ViewModelFactory(container) { SafetyViewModel(it) })
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    // Writing Evidence is what a violation is. The role model already decides
    // who may, so this asks it rather than naming SAFETY_OFFICER here — an
    // owner walking their own site should be able to write one too.
    val canRecordViolations =
        (session as? SessionRepository.State.SignedIn)?.role?.canWrite(Lens.EVIDENCE) == true
    val languageTag = currentLanguageTag()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saf_title)) },
                actions = {
                    // Talks and permits sit beside the checklists rather than
                    // in tabs of their own: one lens, three questions about it.
                    IconButton(onClick = onOpenTalks) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = stringResource(R.string.tbt_title),
                        )
                    }
                    IconButton(onClick = onOpenPermits) {
                        Icon(
                            Icons.Filled.Assignment,
                            contentDescription = stringResource(R.string.ptw_title),
                        )
                    }
                    IconButton(onClick = onOpenSnags) {
                        Icon(
                            Icons.Filled.ChecklistRtl,
                            contentDescription = stringResource(R.string.snag_title),
                        )
                    }
                    IconButton(onClick = onOpenIncidents) {
                        Icon(
                            Icons.Filled.ReportProblem,
                            contentDescription = stringResource(R.string.inc_title),
                        )
                    }
                    // Only for the officer. Everybody else opening this would
                    // find a register they cannot write to, which reads as the
                    // app being broken rather than as a role they do not hold.
                    if (canRecordViolations) {
                        IconButton(onClick = onOpenViolations) {
                            Icon(
                                Icons.Filled.Gavel,
                                contentDescription = stringResource(R.string.vio_title),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (templates.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.saf_empty),
                hint = stringResource(R.string.set_trades),
                icon = Icons.Filled.HealthAndSafety,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(templates, key = { it.id }) { template ->
                    ListItem(
                        headlineContent = { Text(template.titles.resolve(languageTag)) },
                        supportingContent = {
                            Text(
                                template.references.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            if (template.mandatoryBeforeWork) {
                                AssistChip(
                                    onClick = { onRunChecklist(template.id) },
                                    label = { Text(stringResource(R.string.saf_mandatory)) },
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRunChecklist(template.id) },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.saf_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
