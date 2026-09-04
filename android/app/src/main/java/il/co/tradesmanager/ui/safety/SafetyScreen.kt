package il.co.tradesmanager.ui.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.currentLanguageTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyScreen(container: AppContainer, onRunChecklist: (String) -> Unit) {
    val viewModel: SafetyViewModel = viewModel(factory = ViewModelFactory(container) { SafetyViewModel(it) })
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.saf_title)) }) },
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
