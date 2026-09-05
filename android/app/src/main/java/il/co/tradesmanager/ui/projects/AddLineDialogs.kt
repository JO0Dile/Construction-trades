package il.co.tradesmanager.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.ui.components.ItemThumbnail
import il.co.tradesmanager.ui.components.unitLabel
import kotlinx.coroutines.delay

/** Units offered when the user is typing a line the catalogue does not know. */
private val UNITS = listOf("PCS", "M", "M2", "M3", "KG", "L", "ROLL", "BAG", "PAIR", "BOX")

/**
 * Adding something the job needs.
 *
 * Typing wins over picking: on a site people know what they want and the
 * catalogue is there to save them spelling it. So the name field is the first
 * thing, catalogue matches appear underneath as they type, and choosing one
 * only fills the name and unit in — it never blocks entering something the
 * app has never heard of.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMaterialDialog(
    languageTag: String,
    search: suspend (String) -> List<CatalogItemEntity>,
    onDismiss: () -> Unit,
    onAdd: (label: String, unit: String, quantity: Double, catalogItemId: String?) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("PCS") }
    var catalogItemId by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<CatalogItemEntity>>(emptyList()) }

    // Debounced: a LIKE over the whole catalogue on every keystroke is wasted
    // work while someone is still typing a word.
    LaunchedEffect(label) {
        if (catalogItemId != null || label.length < 2) {
            matches = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        matches = search(label).take(8)
    }

    val amount = quantity.replace(',', '.').toDoubleOrNull()
    val canAdd = label.isNotBlank() && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proj_add_material)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        catalogItemId = null
                    },
                    label = { Text(stringResource(R.string.proj_material_name)) },
                    supportingText = { Text(stringResource(R.string.proj_material_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (matches.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(matches, key = { it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.names.resolve(languageTag)) },
                                supportingContent = {
                                    val spec = item.spec.resolve(languageTag)
                                    if (spec.isNotBlank()) Text(spec)
                                },
                                leadingContent = {
                                    ItemThumbnail(
                                        category = item.category,
                                        kind = item.kind,
                                        catalogItemId = item.id,
                                        size = 32,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    label = item.names.resolve(languageTag)
                                    unit = item.unit
                                    catalogItemId = item.id
                                    matches = emptyList()
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.proj_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.proj_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UNITS.forEach { code ->
                        FilterChip(
                            selected = unit == code,
                            onClick = { unit = code },
                            label = { Text(stringResource(unitLabel(code))) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = { onAdd(label.trim(), unit, amount ?: 1.0, catalogItemId) },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proj_add_task)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.proj_task_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title.trim()) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
