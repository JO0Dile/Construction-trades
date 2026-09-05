package il.co.tradesmanager.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.catalog.ProjectTemplateDto

/**
 * Starting a job.
 *
 * The old version offered whichever templates the user's trades shipped with
 * and nothing else, so a lobby, a stairwell, or a shop fit-out simply could
 * not be entered. Here the templates are one option among others: name the
 * job, say what sort of place it is — picking from the catalogue's list or
 * typing your own — and start either from a template or from nothing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewProjectSheet(
    kinds: List<ProjectKind>,
    templates: List<Pair<String, ProjectTemplateDto>>,
    languageTag: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, kindLabel: String, template: Pair<String, ProjectTemplateDto>?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<ProjectKind?>(null) }
    // Null means no custom kind is in play; empty string means the user asked
    // for one and has not typed it yet, which is why this is not just isBlank().
    var customKind by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf<Pair<String, ProjectTemplateDto>?>(null) }

    val kindLabel = customKind?.trim() ?: selectedKind?.names?.resolve(languageTag).orEmpty()
    val canCreate = name.isNotBlank() && kindLabel.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.proj_new),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.proj_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeading(stringResource(R.string.proj_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { kind ->
                    FilterChip(
                        selected = customKind == null && selectedKind?.id == kind.id,
                        onClick = {
                            selectedKind = kind
                            customKind = null
                        },
                        label = { Text(kind.names.resolve(languageTag)) },
                    )
                }
                FilterChip(
                    selected = customKind != null,
                    onClick = {
                        customKind = ""
                        selectedKind = null
                    },
                    label = { Text(stringResource(R.string.proj_kind_other)) },
                )
            }
            customKind?.let { typed ->
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { customKind = it },
                    label = { Text(stringResource(R.string.proj_kind_other_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeading(stringResource(R.string.proj_start_from))
            StartingPoint(
                title = stringResource(R.string.proj_blank),
                subtitle = stringResource(R.string.proj_blank_hint),
                selected = selectedTemplate == null,
                onClick = { selectedTemplate = null },
            )
            templates.forEach { entry ->
                StartingPoint(
                    title = entry.second.names.resolve(languageTag),
                    subtitle = entry.second.descriptions.resolve(languageTag),
                    selected = selectedTemplate?.second?.id == entry.second.id,
                    onClick = { selectedTemplate = entry },
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onCreate(name.trim(), kindLabel, selectedTemplate) },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.proj_create))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartingPoint(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
