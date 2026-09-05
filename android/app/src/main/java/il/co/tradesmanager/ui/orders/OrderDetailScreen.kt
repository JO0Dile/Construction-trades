package il.co.tradesmanager.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.data.local.entity.PurchaseOrderLineEntity
import il.co.tradesmanager.data.repository.PurchasingRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionHeaderWithAdd
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.unitLabel

/**
 * One order: what is on it, what has arrived, and what it commits.
 *
 * Booking a delivery in here is one action with three consequences, because on
 * a site it is one event: the line moves forward, the goods go into stock, and
 * the job's material line is marked allocated. Doing those three by hand in
 * three places is how the three of them end up disagreeing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    container: AppContainer,
    orderId: String,
    onBack: () -> Unit,
) {
    val viewModel: OrderDetailViewModel = viewModel(
        factory = ViewModelFactory(container) { OrderDetailViewModel(it, orderId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.STUFF) != false

    var addingLine by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf<PurchaseOrderLineEntity?>(null) }

    val order = state.order
    val isDraft = order?.status == PurchasingRepository.Status.DRAFT
    val isOpen = order?.status == PurchasingRepository.Status.ORDERED ||
        order?.status == PurchasingRepository.Status.PART_RECEIVED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(order?.reference ?: stringResource(R.string.po_title)) },
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
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            order?.supplierName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = (state.project?.name ?: stringResource(R.string.po_yard)) +
                                " · " + stringResource(orderStatusLabel(order?.status.orEmpty())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Figure(stringResource(R.string.po_total), state.total, locale)
                        if (state.outstanding > 0.0 && !isDraft) {
                            Figure(
                                stringResource(R.string.po_outstanding),
                                state.outstanding,
                                locale,
                            )
                        }

                        if (isDraft) {
                            Text(
                                text = stringResource(R.string.po_draft_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (canEdit) {
                                Button(
                                    onClick = viewModel::place,
                                    enabled = state.lines.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) {
                                    Text(stringResource(R.string.po_place))
                                }
                            }
                        } else if (isOpen && canEdit) {
                            TextButton(
                                onClick = viewModel::cancel,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(stringResource(R.string.po_cancel))
                            }
                        }
                    }
                }
            }

            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.po_lines),
                    contentDescription = stringResource(R.string.po_add_line),
                    // Lines are fixed once the order has gone to the supplier:
                    // changing what you ordered after ordering it is a new
                    // order, not an edit.
                    onAdd = if (canEdit && isDraft) ({ addingLine = true }) else null,
                )
            }
            if (state.lines.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.po_lines_empty)) }
            }
            items(state.lines, key = { it.id }) { line ->
                LineRow(
                    line = line,
                    locale = locale,
                    canRemove = canEdit && isDraft,
                    canReceive = canEdit && isOpen,
                    onRemove = { viewModel.removeLine(line) },
                    onReceive = { receiving = line },
                )
            }
        }
    }

    if (addingLine) {
        AddLineDialog(
            onDismiss = { addingLine = false },
            onAdd = { label, unit, quantity, price ->
                viewModel.addLine(label, unit, quantity, price)
                addingLine = false
            },
        )
    }

    receiving?.let { line ->
        ReceiveDialog(
            line = line,
            onDismiss = { receiving = null },
            onReceive = {
                viewModel.receive(line, it)
                receiving = null
            },
        )
    }
}

@Composable
private fun Figure(label: String, amount: Double, locale: java.util.Locale) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(Formats.money(amount, locale), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun LineRow(
    line: PurchaseOrderLineEntity,
    locale: java.util.Locale,
    canRemove: Boolean,
    canReceive: Boolean,
    onRemove: () -> Unit,
    onReceive: () -> Unit,
) {
    val outstanding = line.quantityOrdered - line.quantityReceived
    Column {
        ListItem(
            headlineContent = { Text(line.label) },
            supportingContent = {
                Text(
                    Formats.quantity(line.quantityReceived, locale) + " " +
                        stringResource(R.string.po_received_of) + " " +
                        Formats.quantity(line.quantityOrdered, locale) + " " +
                        stringResource(unitLabel(line.unit)) + " · " +
                        Formats.money(line.unitPrice, locale),
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Formats.money(line.quantityOrdered * line.unitPrice, locale),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (canRemove) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                }
            },
        )
        // Only while something is still to come. A fully delivered line with a
        // live "book in" button invites someone to book it in twice.
        if (canReceive && outstanding > 0.0) {
            TextButton(
                onClick = onReceive,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.po_receive))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddLineDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, unit: String, quantity: Double, price: Double) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("PCS") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }

    val parsedQuantity = quantity.trim().replace(',', '.').toDoubleOrNull()
    val parsedPrice = price.trim().replace(',', '.').toDoubleOrNull()
    val ok = label.isNotBlank() &&
        parsedQuantity != null && parsedQuantity > 0.0 &&
        parsedPrice != null && parsedPrice >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.po_add_line)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.proj_material_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.proj_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.po_unit_price)) },
                    supportingText = { Text(stringResource(R.string.money_ex_vat)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.proj_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                enabled = ok,
                onClick = {
                    onAdd(label.trim(), unit, parsedQuantity ?: 0.0, parsedPrice ?: 0.0)
                },
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
private fun ReceiveDialog(
    line: PurchaseOrderLineEntity,
    onDismiss: () -> Unit,
    onReceive: (Double) -> Unit,
) {
    val outstanding = (line.quantityOrdered - line.quantityReceived).coerceAtLeast(0.0)
    // Pre-filled with what is still owed, because that is what usually turns
    // up, and retyping it off the screen above is how a digit gets dropped.
    var quantity by remember { mutableStateOf(outstanding.toString()) }
    val parsed = quantity.trim().replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.po_receive)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(line.label, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.proj_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0.0,
                onClick = { onReceive(parsed ?: 0.0) },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val UNITS = listOf("PCS", "M", "M2", "M3", "KG", "L", "ROLL", "BAG", "PAIR", "BOX")
