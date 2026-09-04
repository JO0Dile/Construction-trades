package il.co.tradesmanager.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.repository.ProjectRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLanguageTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(container: AppContainer, onOpenProject: (String) -> Unit) {
    val languageTag = currentLanguageTag()
    val viewModel: ProjectsViewModel = viewModel(
        factory = ViewModelFactory(container) { ProjectsViewModel(it, languageTag) },
    )
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var showTemplatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.proj_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTemplatePicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.proj_new))
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.proj_empty),
                hint = stringResource(R.string.proj_from_template),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(projects, key = { it.id }) { project ->
                    ProjectRow(
                        name = project.name,
                        subtitle = listOfNotNull(project.kindLabel, project.city).joinToString(" · "),
                        status = project.status,
                        progress = progress[project.id] ?: 0.0,
                        onClick = { onOpenProject(project.id) },
                    )
                }
            }
        }
    }

    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text(stringResource(R.string.proj_from_template)) },
            text = {
                if (templates.isEmpty()) {
                    Text(stringResource(R.string.proj_empty))
                } else {
                    LazyColumn {
                        items(templates) { (tradeId, template) ->
                            Card(
                                onClick = {
                                    viewModel.createFromTemplate(
                                        tradeId = tradeId,
                                        template = template,
                                        name = template.names.resolve(languageTag),
                                        kindLabel = template.names.resolve(languageTag),
                                    )
                                    showTemplatePicker = false
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        template.names.resolve(languageTag),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        template.descriptions.resolve(languageTag),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal fun statusLabel(status: String): Int = when (status) {
    ProjectRepository.Status.ACTIVE -> R.string.proj_status_active
    ProjectRepository.Status.ON_HOLD -> R.string.proj_status_hold
    ProjectRepository.Status.DONE -> R.string.proj_status_done
    else -> R.string.proj_status_planned
}

/**
 * One job in the list.
 *
 * Status and progress are the two things worth knowing before opening a job,
 * and neither was shown before — the row was a name and a subtitle, which is
 * the same information as the project's own title bar.
 */
@Composable
private fun ProjectRow(
    name: String,
    subtitle: String,
    status: String,
    progress: Double,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            StatusChip(status)
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Only drawn once there is a task list to be a fraction of; a full-width
        // empty bar on every row reads as "nothing is happening anywhere".
        if (progress > 0.0) {
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val colour = when (status) {
        ProjectRepository.Status.ACTIVE -> Color(0xFF117864)
        ProjectRepository.Status.ON_HOLD -> Color(0xFFB9770E)
        ProjectRepository.Status.DONE -> Color(0xFF566573)
        else -> Color(0xFF2E86C1)
    }
    Text(
        text = stringResource(statusLabel(status)),
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        modifier = Modifier
            .background(colour.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
