package il.co.tradesmanager.ui.safety

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import il.co.tradesmanager.R
import il.co.tradesmanager.core.find.Search
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.Numbers
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.i18n.searchable
import il.co.tradesmanager.core.safety.Ppe
import il.co.tradesmanager.core.security.Signature
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.PpeIssueEntity
import il.co.tradesmanager.data.repository.PpeRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.ViewModelFactory
import il.co.tradesmanager.ui.components.SignaturePad
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Protective equipment: who is holding what, and handing more out.
 *
 * The form is on the same screen as the register rather than behind a
 * button on a second one, because the person issuing is usually standing at
 * the container with the man in front of them and the phone in one hand.
 * The man signs on the same screen, which is the point of the whole thing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PpeScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: PpeViewModel =
        viewModel(factory = ViewModelFactory(container) { PpeViewModel(it) })
    val mayRead by viewModel.mayRead.collectAsStateWithLifecycle()
    val mayIssue by viewModel.mayIssue.collectAsStateWithLifecycle()
    val holders by viewModel.holders.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val handedBack by viewModel.handedBack.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val stock by viewModel.stock.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val languageTag = currentLanguageTag()

    var issuing by remember { mutableStateOf(false) }
    var findPerson by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var typedName by remember { mutableStateOf("") }
    var findItem by remember { mutableStateOf("") }
    var item by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var typedItem by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var size by remember { mutableStateOf("") }
    var replaceAfter by remember { mutableStateOf<Int?>(null) }
    var signature by remember { mutableStateOf("") }
    // Bumped to give the signature pad a fresh, empty canvas after an issue.
    var form by remember { mutableIntStateOf(0) }
    var confirmingHandBack by remember { mutableStateOf<PpeIssueEntity?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val issuedText = stringResource(R.string.ppe_issued)
    val shortTemplate = stringResource(R.string.ppe_issued_short)
    LaunchedEffect(Unit) {
        viewModel.issued.collect { shortBy ->
            account = null
            typedName = ""
            findPerson = ""
            item = null
            typedItem = ""
            findItem = ""
            quantity = "1"
            size = ""
            replaceAfter = null
            signature = ""
            form += 1
            issuing = false
            snackbar.showSnackbar(
                if (shortBy == null) issuedText
                else String.format(locale, shortTemplate, Formats.quantity(shortBy, locale)),
            )
        }
    }
    val refusalText = refusal?.let { stringResource(sentence(it)) }
    LaunchedEffect(refusalText) {
        if (refusalText != null) {
            snackbar.showSnackbar(refusalText)
            viewModel.clearRefusal()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ppe_title)) },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.ppe_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (!mayRead) {
                item {
                    Text(
                        stringResource(R.string.ppe_not_allowed_view),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@LazyColumn
            }

            if (mayIssue && !issuing) {
                item {
                    Button(onClick = { issuing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ppe_issue))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (mayIssue && issuing) {
                item {
                    Heading(R.string.ppe_to_whom)
                    if (people.isNotEmpty()) {
                        OutlinedTextField(
                            value = findPerson,
                            onValueChange = { findPerson = it },
                            label = { Text(stringResource(R.string.ppe_find_person)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val terms = Search.terms(findPerson, shortest = 1)
                        val shown = people
                            .filter { terms.isEmpty() || Search.score(terms, it.displayName, "") > 0 }
                            .take(CHIPS_SHOWN)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.forEach { person ->
                                FilterChip(
                                    selected = account?.id == person.id,
                                    onClick = {
                                        account = if (account?.id == person.id) null else person
                                        if (account != null) typedName = ""
                                    },
                                    label = { Text(person.displayName) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = {
                            typedName = it
                            if (it.isNotBlank()) account = null
                        },
                        label = { Text(stringResource(R.string.ppe_someone_else)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))

                    Heading(R.string.ppe_what)
                    if (stock.isNotEmpty()) {
                        OutlinedTextField(
                            value = findItem,
                            onValueChange = { findItem = it },
                            label = { Text(stringResource(R.string.ppe_find_item)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val terms = Search.terms(findItem, shortest = 1)
                        val shown = stock
                            .filter { terms.isEmpty() || Search.score(terms, it.names.searchable(), it.searchIndex) > 0 }
                            .take(CHIPS_SHOWN)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.forEach { option ->
                                FilterChip(
                                    selected = item?.id == option.id,
                                    onClick = {
                                        item = if (item?.id == option.id) null else option
                                        if (item != null) typedItem = ""
                                    },
                                    label = {
                                        Text(
                                            option.names.resolve(languageTag) + SEPARATOR +
                                                stringResource(
                                                    R.string.ppe_in_stock,
                                                    Formats.quantity(option.quantity, locale),
                                                ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = typedItem,
                        onValueChange = {
                            typedItem = it
                            if (it.isNotBlank()) item = null
                        },
                        label = { Text(stringResource(R.string.ppe_item_typed)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = Numbers.typingDecimal(it).filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.ppe_quantity)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = size,
                            onValueChange = { size = it },
                            label = { Text(stringResource(R.string.ppe_size)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    Heading(R.string.ppe_replace_by)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = replaceAfter == null,
                            onClick = { replaceAfter = null },
                            label = { Text(stringResource(R.string.ppe_replace_never)) },
                        )
                        Ppe.REPLACE_AFTER_MONTHS.forEach { months ->
                            FilterChip(
                                selected = replaceAfter == months,
                                onClick = { replaceAfter = months },
                                label = { Text(stringResource(afterLabel(months))) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.ppe_replace_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    Heading(R.string.ppe_sign)
                    key(form) {
                        SignaturePad(onSignatureChange = { signature = it })
                    }
                    Spacer(Modifier.height(12.dp))

                    val itemName = item?.names?.resolve(languageTag) ?: typedItem
                    // Clamped only so a pasted twelve-digit number cannot wrap
                    // round to a small one. Past fifty the repository refuses
                    // and the screen says why, rather than a dead button.
                    val whole = Numbers.parseWhole(quantity)?.coerceIn(0L, 1_000L)?.toInt() ?: 0
                    Button(
                        enabled = (account != null || typedName.isNotBlank()) &&
                            itemName.isNotBlank() && whole > 0 && Signature.isSigned(signature),
                        onClick = {
                            val now = System.currentTimeMillis()
                            viewModel.issue(
                                account = account,
                                typedName = typedName,
                                item = item,
                                itemName = itemName,
                                quantity = whole,
                                size = size,
                                replaceBy = replaceAfter?.let {
                                    Ppe.monthsAfter(now, it, ZoneId.systemDefault())
                                },
                                signature = signature,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ppe_record))
                    }
                    TextButton(onClick = { issuing = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                Text(stringResource(R.string.ppe_holders), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
            }
            if (holders.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.ppe_nobody),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(holders, key = { it.key }) { holder ->
                    HolderCard(
                        holder = holder,
                        rows = rows,
                        locale = locale,
                        mayHandBack = mayIssue,
                        onHandBack = { confirmingHandBack = it },
                    )
                }
            }

            if (handedBack.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.ppe_history), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                }
                items(handedBack, key = { "back-" + it.id }) { row ->
                    Text(
                        listOfNotNull(
                            row.holderName,
                            row.itemName,
                            row.handedBackAt?.let { dateOf(it, locale) },
                        ).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }

    confirmingHandBack?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmingHandBack = null },
            title = { Text(stringResource(R.string.ppe_hand_back_confirm)) },
            text = {
                Column {
                    Text(row.holderName + SEPARATOR + row.itemName, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ppe_hand_back_body))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.handBack(row.id)
                    confirmingHandBack = null
                }) { Text(stringResource(R.string.ppe_hand_back)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingHandBack = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Heading(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** One person and what they hold, with anything past its date said first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HolderCard(
    holder: Ppe.Holder,
    rows: Map<String, PpeIssueEntity>,
    locale: Locale,
    mayHandBack: Boolean,
    onHandBack: (PpeIssueEntity) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = if (holder.overdue > 0) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(holder.name, style = MaterialTheme.typography.titleMedium)
            holder.items.forEach { (issue, state) ->
                val row = rows[issue.id] ?: return@forEach
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(
                                row.itemName,
                                stringResource(R.string.ppe_quantity_badge, row.quantity.toString()),
                                row.size?.let { stringResource(R.string.ppe_size_badge, it) },
                            ).joinToString(SEPARATOR),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (state == Ppe.State.IN_USE) FontWeight.Normal else FontWeight.Bold,
                        )
                        Text(
                            listOfNotNull(
                                stringResource(stateLabel(state)),
                                row.replaceBy?.let { stringResource(R.string.ppe_replace_on, dateOf(it, locale)) },
                                stringResource(R.string.ppe_issued_on, dateOf(row.issuedAt, locale), row.issuedByName),
                            ).joinToString(SEPARATOR),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (mayHandBack) {
                        OutlinedButton(onClick = { onHandBack(row) }) {
                            Text(stringResource(R.string.ppe_hand_back))
                        }
                    }
                }
            }
        }
    }
}

private fun dateOf(at: Long, locale: Locale): String =
    Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

@StringRes
private fun stateLabel(state: Ppe.State): Int = when (state) {
    Ppe.State.OVERDUE -> R.string.ppe_state_overdue
    Ppe.State.DUE_SOON -> R.string.ppe_state_due_soon
    Ppe.State.IN_USE -> R.string.ppe_state_in_use
    Ppe.State.HANDED_BACK -> R.string.ppe_history
}

@StringRes
private fun afterLabel(months: Int): Int = when (months) {
    6 -> R.string.ppe_after_6_months
    12 -> R.string.ppe_after_1_year
    24 -> R.string.ppe_after_2_years
    36 -> R.string.ppe_after_3_years
    else -> R.string.ppe_after_5_years
}

@StringRes
private fun sentence(refusal: PpeRepository.Refusal): Int = when (refusal) {
    PpeRepository.Refusal.NOT_ALLOWED -> R.string.ppe_refused_not_allowed
    PpeRepository.Refusal.NO_WORKER -> R.string.ppe_refused_worker
    PpeRepository.Refusal.NO_ITEM -> R.string.ppe_refused_item
    PpeRepository.Refusal.BAD_QUANTITY -> R.string.ppe_refused_quantity
    PpeRepository.Refusal.NOT_SIGNED -> R.string.ppe_refused_signed
    PpeRepository.Refusal.REPLACE_BY_TOO_EARLY -> R.string.ppe_refused_too_early
    PpeRepository.Refusal.NOT_HELD -> R.string.ppe_refused_not_held
    PpeRepository.Refusal.UNKNOWN -> R.string.write_not_saved
}

/** How many people or items are offered as chips before the search box has to narrow them. */
private const val CHIPS_SHOWN = 30

private const val SEPARATOR = " · "
