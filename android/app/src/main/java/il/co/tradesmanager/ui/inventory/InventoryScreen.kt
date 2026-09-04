package il.co.tradesmanager.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import il.co.tradesmanager.core.i18n.resolve
import androidx.lifecycle.SavedStateHandle
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.export.ExportDocument
import il.co.tradesmanager.ui.export.Exporter
import il.co.tradesmanager.ui.nav.Routes
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.EmptyState
import il.co.tradesmanager.ui.components.ItemThumbnail
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.unitLabel
import il.co.tradesmanager.ui.components.currentLocale

private val KIND_FILTERS = listOf(
    null to R.string.inv_filter_all,
    "TOOL" to R.string.kind_tool,
    "MATERIAL" to R.string.kind_material,
    "SAFETY" to R.string.kind_safety,
    "CONSUMABLE" to R.string.kind_consumable,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    container: AppContainer,
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onScan: () -> Unit,
    savedStateHandle: SavedStateHandle,
) {
    val viewModel: InventoryViewModel = viewModel(
        factory = ViewModelFactory(container) { InventoryViewModel(it) },
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val photoByItem by viewModel.photoByItem.collectAsStateWithLifecycle()
    val languageTag = currentLanguageTag()
    val locale = currentLocale()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scanned by savedStateHandle
        .getStateFlow<String?>(Routes.SCAN_RESULT, null)
        .collectAsStateWithLifecycle()

    // A scan that matches opens the item; one that does not starts a new item
    // with the code already filled in, which is the point of scanning a label
    // on a box you have never stocked before.
    LaunchedEffect(scanned) {
        val code = scanned ?: return@LaunchedEffect
        savedStateHandle[Routes.SCAN_RESULT] = null
        val match = viewModel.findByBarcode(code)
        if (match != null) onEditItem(match.id) else onAddItem()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inv_title)) },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(R.string.inv_scan),
                        )
                    }
                    IconButton(
                        onClick = {
                            val result = Exporter.write(
                                context = context,
                                document = ExportDocument.Inventory(items),
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
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.inv_add_item))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.inv_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KIND_FILTERS.forEach { (kind, labelRes) ->
                    FilterChip(
                        selected = filters.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
                FilterChip(
                    selected = filters.lowStockOnly,
                    onClick = { viewModel.toggleLowStockOnly() },
                    label = { Text(stringResource(R.string.inv_filter_low)) },
                    trailingIcon = {
                        if (lowStockCount > 0) Badge { Text(lowStockCount.toString()) }
                    },
                )
            }

            if (items.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.inv_empty),
                    hint = stringResource(R.string.inv_from_catalog),
                    icon = Icons.Filled.Inventory2,
                    actionLabel = stringResource(R.string.inv_add_item),
                    onAction = onAddItem,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(items, key = { it.id }) { item ->
                        ListItem(
                            leadingContent = {
                                ItemThumbnail(
                                    category = item.category,
                                    kind = item.kind,
                                    photoUri = photoByItem[item.id],
                                )
                            },
                            headlineContent = { Text(item.names.resolve(languageTag)) },
                            supportingContent = {
                                Text(item.spec.resolve(languageTag), maxLines = 2)
                            },
                            overlineContent = if (item.isLowStock) {
                                { Text(stringResource(R.string.inv_low_stock), color = MaterialTheme.colorScheme.error) }
                            } else {
                                null
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            viewModel.adjustStock(item.id, -1.0, USED_ON_SITE)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.Remove,
                                            contentDescription = stringResource(R.string.inv_stock_remove),
                                        )
                                    }
                                    AssistChip(
                                        onClick = { onEditItem(item.id) },
                                        label = {
                                            Text(
                                                Formats.quantity(item.quantity, locale) + " " +
                                                    stringResource(unitLabel(item.unit)),
                                            )
                                        },
                                    )
                                    IconButton(
                                        onClick = { viewModel.adjustStock(item.id, 1.0, RESTOCKED) },
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = stringResource(R.string.inv_stock_add),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}


private const val USED_ON_SITE = "used_on_site"
private const val RESTOCKED = "restocked"
