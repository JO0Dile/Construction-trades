package il.co.tradesmanager.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import il.co.tradesmanager.R
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
    val kinds by viewModel.kinds.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val asGrid by viewModel.asGrid.collectAsStateWithLifecycle()
    var showNewProject by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proj_title)) },
                actions = {
                    IconButton(onClick = { viewModel.setAsGrid(!asGrid) }) {
                        Icon(
                            imageVector = if (asGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = stringResource(
                                if (asGrid) R.string.proj_view_list else R.string.proj_view_grid,
                            ),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewProject = true }) {
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
        } else if (asGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        name = project.name,
                        subtitle = listOfNotNull(project.kindLabel, project.city).joinToString(" · "),
                        status = project.status,
                        progress = progress[project.id] ?: 0.0,
                        coverUri = covers[project.id],
                        onClick = { onOpenProject(project.id) },
                    )
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(projects, key = { it.id }) { project ->
                    ProjectRow(
                        name = project.name,
                        subtitle = listOfNotNull(project.kindLabel, project.city).joinToString(" · "),
                        status = project.status,
                        progress = progress[project.id] ?: 0.0,
                        coverUri = covers[project.id],
                        onClick = { onOpenProject(project.id) },
                    )
                }
            }
        }
    }

    if (showNewProject) {
        NewProjectSheet(
            kinds = kinds,
            templates = templates,
            languageTag = languageTag,
            onDismiss = { showNewProject = false },
            onCreate = { name, kindLabel, template ->
                showNewProject = false
                viewModel.create(name, kindLabel, template, onOpenProject)
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
 * A job as a picture card.
 *
 * On site a job is a place, and people recognise a place by looking at it —
 * the floor plan they photographed, or the room half-finished. Until there is
 * a photograph the card falls back to the same tinted placeholder everywhere
 * else uses, so the grid stays even rather than collapsing to text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(
    name: String,
    subtitle: String,
    status: String,
    progress: Double,
    coverUri: String?,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Work,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { StatusChip(status) }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress > 0.0) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
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
    coverUri: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A thumbnail in the row too, so switching to the list view loses the
        // density of the grid but not the recognition.
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Work,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Column(Modifier.weight(1f)) {
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
