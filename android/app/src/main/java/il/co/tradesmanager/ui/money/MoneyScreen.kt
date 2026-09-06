package il.co.tradesmanager.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.data.local.entity.CostEntryEntity
import il.co.tradesmanager.data.local.entity.InvoiceEntity
import il.co.tradesmanager.data.local.entity.VariationEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SectionHeaderWithAdd
import il.co.tradesmanager.ui.components.SectionPlaceholder
import il.co.tradesmanager.ui.components.currentLocale
import java.util.Locale

/**
 * What a job is worth, what it has cost, and what is still owed.
 *
 * The summary at the top is the whole point: four numbers a person can act on
 * without reading a ledger — what the client owes, what has gone out, what is
 * left, and what is still unpaid. Everything below is the working behind them.
 *
 * Amounts are net of VAT throughout, said out loud on the screen rather than
 * assumed, because "is that with or without מע״מ" is the question that follows
 * every figure on an Israeli site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    container: AppContainer,
    projectId: String,
    onOpenPayments: () -> Unit,
    onOpenTimesheet: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: MoneyViewModel = viewModel(
        factory = ViewModelFactory(container) { MoneyViewModel(it, projectId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val locale = currentLocale()

    val signedIn = session as? SessionRepository.State.SignedIn
    val canEdit = signedIn?.canWrite(Lens.MONEY) != false

    var editingBudget by remember { mutableStateOf(false) }
    var addingCost by remember { mutableStateOf(false) }
    var addingVariation by remember { mutableStateOf(false) }
    var addingInvoice by remember { mutableStateOf(false) }

    val money = state.financials

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.project?.name ?: stringResource(R.string.money_title)) },
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
            item { Summary(money, locale, canEdit) { editingBudget = true } }

            // Applications sit under the summary rather than beside the costs:
            // what has been claimed and certified is a different question from
            // what the job has spent, and mixing them is how a surveyor ends up
            // reading a cost line as an application.
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pay_title)) },
                    supportingContent = { Text(stringResource(R.string.pay_row_hint)) },
                    modifier = Modifier.clickable(onClick = onOpenPayments),
                )
            }

            // The hours beside the applications, because both are money the
            // job has earned or owes that no cost line has been typed for.
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ts_title)) },
                    supportingContent = { Text(stringResource(R.string.ts_row_hint)) },
                    modifier = Modifier.clickable(onClick = onOpenTimesheet),
                )
            }

            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.money_costs),
                    contentDescription = stringResource(R.string.money_add_cost),
                    onAdd = if (canEdit) ({ addingCost = true }) else null,
                )
            }
            if (state.costs.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.money_costs_empty)) }
            }
            items(state.costs, key = { it.id }) { cost ->
                CostRow(cost, locale, canEdit) { viewModel.removeCost(cost) }
            }

            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.money_variations),
                    contentDescription = stringResource(R.string.money_add_variation),
                    onAdd = if (canEdit) ({ addingVariation = true }) else null,
                )
            }
            if (state.variations.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.money_variations_empty)) }
            }
            items(state.variations, key = { it.id }) { variation ->
                VariationRow(
                    variation = variation,
                    locale = locale,
                    canEdit = canEdit,
                    onDecide = { viewModel.decideVariation(variation, it) },
                    onRemove = { viewModel.removeVariation(variation) },
                )
            }

            item {
                SectionHeaderWithAdd(
                    title = stringResource(R.string.money_invoices),
                    contentDescription = stringResource(R.string.money_add_invoice),
                    onAdd = if (canEdit) ({ addingInvoice = true }) else null,
                )
            }
            if (state.invoices.isEmpty()) {
                item { SectionPlaceholder(stringResource(R.string.money_invoices_empty)) }
            }
            items(state.invoices, key = { it.id }) { invoice ->
                InvoiceRow(
                    invoice = invoice,
                    locale = locale,
                    canEdit = canEdit,
                    onMarkPaid = { viewModel.markPaid(invoice) },
                    onRemove = { viewModel.removeInvoice(invoice) },
                )
            }
        }
    }

    if (editingBudget) {
        BudgetDialog(
            current = money,
            locale = locale,
            onDismiss = { editingBudget = false },
            onSave = { value, rate ->
                viewModel.setBudget(value, rate)
                editingBudget = false
            },
        )
    }

    if (addingCost) {
        CostDialog(
            onDismiss = { addingCost = false },
            onAdd = { category, description, amount, ref ->
                viewModel.addCost(category, description, amount, ref)
                addingCost = false
            },
        )
    }

    if (addingVariation) {
        VariationDialog(
            onDismiss = { addingVariation = false },
            onAdd = { title, amount ->
                viewModel.raiseVariation(title, amount)
                addingVariation = false
            },
        )
    }

    if (addingInvoice) {
        InvoiceDialog(
            suggested = money.uninvoiced.coerceAtLeast(0.0),
            onDismiss = { addingInvoice = false },
            onAdd = { number, amount ->
                viewModel.addInvoice(number, amount)
                addingInvoice = false
            },
        )
    }
}

/* ------------------------------------------------------------------ summary */

@Composable
private fun Summary(
    money: JobFinancials,
    locale: Locale,
    canEdit: Boolean,
    onEditBudget: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (money.revisedContract == 0.0) {
                Text(
                    text = stringResource(R.string.money_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Figure(
                    label = stringResource(R.string.money_revised),
                    amount = money.revisedContract,
                    locale = locale,
                    emphasis = true,
                )
                Text(
                    text = stringResource(R.string.money_ex_vat) + " · " +
                        Formats.money(money.withVat(money.revisedContract), locale) + " " +
                        stringResource(R.string.money_inc_vat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer12()
                // Spend against what the client is paying, which is the one
                // comparison that decides whether to keep going.
                LinearProgressIndicator(
                    progress = {
                        (money.costToDate / money.revisedContract).toFloat().coerceIn(0f, 1f)
                    },
                    color = when {
                        money.isOverBudget -> Colours.over
                        money.isForecastOverBudget -> Colours.warning
                        else -> Colours.good
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (money.isOverBudget || money.isForecastOverBudget) {
                    Text(
                        text = stringResource(
                            if (money.isOverBudget) R.string.money_over else R.string.money_over_soon,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (money.isOverBudget) Colours.over else Colours.warning,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer12()
                Figure(stringResource(R.string.money_cost), money.costToDate, locale)
                if (money.committedCosts > 0.0) {
                    Figure(
                        stringResource(R.string.money_committed),
                        money.committedCosts,
                        locale,
                        muted = true,
                    )
                    Text(
                        text = stringResource(R.string.money_committed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Figure(
                    label = stringResource(R.string.money_margin),
                    amount = money.margin,
                    locale = locale,
                    trailing = Formats.percent(money.marginFraction.coerceAtLeast(0.0), locale),
                )
                if (money.committedCosts > 0.0) {
                    Figure(
                        stringResource(R.string.money_forecast),
                        money.forecastMargin,
                        locale,
                        muted = true,
                    )
                }

                Spacer12()
                Figure(stringResource(R.string.money_invoiced), money.invoiced, locale)
                Figure(stringResource(R.string.money_uninvoiced), money.uninvoiced, locale, muted = true)
                Figure(stringResource(R.string.money_outstanding), money.outstanding, locale)
            }

            if (canEdit) {
                TextButton(onClick = onEditBudget, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.money_set_budget))
                }
            }
        }
    }
}

@Composable
private fun Spacer12() = Spacer(Modifier.height(12.dp))

/** One label and one figure, aligned so a column of them reads down the page. */
@Composable
private fun Figure(
    label: String,
    amount: Double,
    locale: Locale,
    emphasis: Boolean = false,
    muted: Boolean = false,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = Formats.money(amount, locale),
            style = if (emphasis) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = when {
                muted -> MaterialTheme.colorScheme.onSurfaceVariant
                amount < 0.0 -> Colours.over
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/* --------------------------------------------------------------------- rows */

@Composable
private fun CostRow(
    cost: CostEntryEntity,
    locale: Locale,
    canEdit: Boolean,
    onRemove: () -> Unit,
) {
    val supporting: (@Composable () -> Unit)? = cost.supplierInvoiceRef?.let { ref ->
        { Text(ref) }
    }
    ListItem(
        overlineContent = { Text(stringResource(costCategoryLabel(cost.category))) },
        headlineContent = { Text(cost.description) },
        supportingContent = supporting,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Formats.money(cost.amount, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (canEdit) {
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
}

@Composable
private fun VariationRow(
    variation: VariationEntity,
    locale: Locale,
    canEdit: Boolean,
    onDecide: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val pending = variation.status == JobFinancials.VariationStatus.PROPOSED
    Column {
        ListItem(
            overlineContent = { Text(variation.reference) },
            headlineContent = { Text(variation.title) },
            supportingContent = { Text(stringResource(variationStatusLabel(variation.status))) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Formats.money(variation.amount, locale),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (variation.amount < 0.0) {
                            Colours.over
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (canEdit) {
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
        // Approve and reject only exist while it is still a question.
        if (pending && canEdit) {
            Row(
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onDecide(true) }) {
                    Text(stringResource(R.string.money_approve))
                }
                TextButton(onClick = { onDecide(false) }) {
                    Text(stringResource(R.string.money_reject))
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(
    invoice: InvoiceEntity,
    locale: Locale,
    canEdit: Boolean,
    onMarkPaid: () -> Unit,
    onRemove: () -> Unit,
) {
    val paid = invoice.status == JobFinancials.InvoiceStatus.PAID
    Column {
        ListItem(
            overlineContent = { Text(invoice.number) },
            headlineContent = { Text(Formats.money(invoice.amount, locale)) },
            supportingContent = {
                Text(
                    stringResource(if (paid) R.string.money_paid else R.string.money_sent) +
                        " · " + Formats.money(
                            invoice.amount * (1.0 + invoice.vatRate),
                            locale,
                        ) + " " + stringResource(R.string.money_inc_vat),
                )
            },
            trailingContent = {
                if (canEdit) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                }
            },
        )
        if (!paid && canEdit) {
            TextButton(
                onClick = onMarkPaid,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            ) {
                Text(stringResource(R.string.money_mark_paid))
            }
        }
    }
}

/* ------------------------------------------------------------------ dialogs */

@Composable
private fun BudgetDialog(
    current: JobFinancials,
    locale: Locale,
    onDismiss: () -> Unit,
    onSave: (contractValue: Double, vatRate: Double) -> Unit,
) {
    var value by remember {
        mutableStateOf(if (current.contractValue == 0.0) "" else current.contractValue.toString())
    }
    var rate by remember { mutableStateOf((current.vatRate * 100).toInt().toString()) }
    val amount = value.toAmount()
    val vat = rate.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_set_budget)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(value, { value = it }, stringResource(R.string.money_contract))
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.money_vat_rate) + " %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (amount != null && vat != null) {
                    Text(
                        text = Formats.money(amount * (1.0 + vat / 100.0), locale) + " " +
                            stringResource(R.string.money_inc_vat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && vat != null && vat >= 0.0,
                onClick = { onSave(amount ?: 0.0, (vat ?: 0.0) / 100.0) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostDialog(
    onDismiss: () -> Unit,
    onAdd: (category: String, description: String, amount: Double, ref: String?) -> Unit,
) {
    var category by remember { mutableStateOf(JobFinancials.CostCategory.MATERIAL) }
    var description by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    val amount = value.toAmount()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_add_cost)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JobFinancials.CostCategory.all.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(stringResource(costCategoryLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.money_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountField(value, { value = it }, stringResource(R.string.money_amount))
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text(stringResource(R.string.money_supplier_ref)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && amount != null,
                onClick = {
                    onAdd(category, description.trim(), amount ?: 0.0, ref.takeIf { it.isNotBlank() })
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
private fun VariationDialog(onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    var title by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val amount = value.toAmount(allowNegative = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_add_variation)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.money_variation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.money_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountField(value, { value = it }, stringResource(R.string.money_amount), signed = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && amount != null,
                onClick = { onAdd(title.trim(), amount ?: 0.0) },
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
private fun InvoiceDialog(
    suggested: Double,
    onDismiss: () -> Unit,
    onAdd: (String, Double) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    // Pre-filled with what is left to bill: that is nearly always the figure,
    // and retyping it off the screen above is how a digit gets dropped.
    var value by remember { mutableStateOf(if (suggested > 0.0) suggested.toString() else "") }
    val amount = value.toAmount()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_add_invoice)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.money_invoice_no)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountField(value, { value = it }, stringResource(R.string.money_amount))
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.isNotBlank() && amount != null,
                onClick = { onAdd(number.trim(), amount ?: 0.0) },
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
private fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    signed: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.money_ex_vat)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (signed) KeyboardType.Number else KeyboardType.Decimal,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/* ------------------------------------------------------------------ helpers */

/**
 * Reads a typed amount. A comma is a decimal separator on a Hebrew keyboard as
 * often as a full stop, and refusing "1,5" would be refusing what the user
 * typed rather than what they meant.
 */
private fun String.toAmount(allowNegative: Boolean = false): Double? {
    val parsed = trim().replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return null
    return if (!allowNegative && parsed < 0.0) null else parsed
}

internal fun costCategoryLabel(category: String): Int = when (category) {
    JobFinancials.CostCategory.LABOUR -> R.string.cost_labour
    JobFinancials.CostCategory.PLANT -> R.string.cost_plant
    JobFinancials.CostCategory.SUBCONTRACTOR -> R.string.cost_subcontractor
    JobFinancials.CostCategory.OTHER -> R.string.cost_other
    else -> R.string.cost_material
}

internal fun variationStatusLabel(status: String): Int = when (status) {
    JobFinancials.VariationStatus.APPROVED -> R.string.money_approved
    JobFinancials.VariationStatus.REJECTED -> R.string.money_rejected
    else -> R.string.money_proposed
}

private object Colours {
    val good = Color(0xFF117864)
    val warning = Color(0xFFB9770E)
    val over = Color(0xFFC0392B)
}
