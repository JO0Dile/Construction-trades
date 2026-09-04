package il.co.tradesmanager.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.DetailRow
import il.co.tradesmanager.ui.components.ItemThumbnail
import il.co.tradesmanager.ui.components.SectionHeader
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import il.co.tradesmanager.ui.components.unitLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(container: AppContainer, projectId: String, onBack: () -> Unit) {
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { ProjectDetailViewModel(it, projectId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val project = state.project

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: stringResource(R.string.proj_title)) },
                actions = {
                    val project = state.project
                    IconButton(
                        enabled = project != null,
                        onClick = {
                            if (project == null) return@IconButton
                            val result = Exporter.write(
                                context = context,
                                document = ExportDocument.ProjectSheet(project, state.tasks, state.materials),
                                languageTag = languageTag,
                                locale = locale,
                                rightToLeft = layoutDirection == LayoutDirection.Rtl,
                            )
                            context.startActivity(Exporter.shareIntent(context, result))
                        },
                    ) {
                        Icon(
                            Icons.Filled.IosShare,
                            contentDescription = stringResource(R.string.set_export),
                        )
                    }
                },
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
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (project != null) {
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        DetailRow(stringResource(R.string.proj_status), stringResource(statusLabel(project.status)))
                        project.city?.let { DetailRow(stringResource(R.string.proj_address), it) }
                        project.clientName?.let { DetailRow(stringResource(R.string.proj_client), it) }
                    }
                }
            }

            if (state.tasks.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(
                            stringResource(R.string.proj_progress) + "  " +
                                Formats.percent(state.progress, locale),
                            modifier = Modifier.padding(horizontal = 0.dp),
                        )
                        LinearProgressIndicator(
                            progress = { state.progress.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item { SectionHeader(stringResource(R.string.proj_tasks)) }
                items(state.tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.title) },
                        leadingContent = {
                            Checkbox(
                                checked = task.isDone,
                                onCheckedChange = { viewModel.setTaskDone(task.id, it) },
                            )
                        },
                    )
                }
            }

            if (state.materials.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.proj_materials)) }
                items(state.materials, key = { it.id }) { material ->
                    ListItem(
                        leadingContent = {
                            ItemThumbnail(
                                category = state.categoryOf(material),
                                kind = "MATERIAL",
                                size = 36,
                            )
                        },
                        headlineContent = { Text(material.label) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.proj_required_qty) + ": " +
                                    Formats.quantity(material.requiredQuantity, locale) + " " +
                                    stringResource(unitLabel(material.unit)),
                            )
                        },
                        trailingContent = {
                            Text(
                                Formats.quantity(material.allocatedQuantity, locale),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
        }
    }
}
