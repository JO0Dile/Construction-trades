package il.co.tradesmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLocale
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenInventory: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = ViewModelFactory(container) { HomeViewModel(it) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.set_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            item { SectionHeader(stringResource(R.string.home_today)) }
            if (state.today.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_no_tasks_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(state.today, key = { it.id }) { block ->
                    ListItem(
                        headlineContent = { Text(block.title) },
                        supportingContent = {
                            Text(
                                Formats.time(LocalTime.ofSecondOfDay(block.startMinute * 60L), locale) +
                                    " – " +
                                    Formats.time(LocalTime.ofSecondOfDay(block.endMinute * 60L), locale),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryCard(
                        title = pluralStringResource(
                            R.plurals.low_stock_count,
                            state.lowStock.size,
                            state.lowStock.size,
                        ),
                        subtitle = stringResource(R.string.home_low_stock),
                        onClick = onOpenInventory,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = state.activeProjects.size.toString(),
                        subtitle = stringResource(R.string.home_active_projects),
                        onClick = onOpenProjects,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.activeProjects.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_active_projects)) }
                items(state.activeProjects, key = { it.id }) { project ->
                    ListItem(
                        headlineContent = { Text(project.name) },
                        supportingContent = { Text(project.kindLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.openChecklists > 0) {
                item {
                    Card(
                        onClick = onOpenSchedule,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_safety_due),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
